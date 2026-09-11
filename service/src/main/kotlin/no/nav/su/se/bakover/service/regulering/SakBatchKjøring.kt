package no.nav.su.se.bakover.service.regulering

import arrow.core.Either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.infrastructure.job.AktiveLangvarigeJobber
import no.nav.su.se.bakover.common.sikkerLogg
import org.slf4j.Logger
import java.util.UUID

/**
 * Delt batch- og parallellitetsmotor for automatiske kjøringer over alle saker
 * (regulering, omregning, o.l.).
 *
 * Ansvar: kun batching + parallell kjøring av saker med begrenset samtidighet,
 * pluss det ytre try/catch+logging-laget som var identisk i
 * [ReguleringAutomatiskServiceImpl.startAutomatiskRegulering] og
 * `OmregningAldersFradragServiceImpl.startAutomatiskOmregning`. Ingen
 * forretningslogikk om hva som faktisk skal gjøres per sak — selve
 * arbeidet gis inn som funksjonen [prosesserBatch].
 *
 * DISKUSJON: Foreløpig kun brukt av [OmregningAldersFradragServiceImpl] (draft).
 * [ReguleringAutomatiskServiceImpl] er IKKE endret ennå — planen er å bevise
 * mønsteret her først, og heller erstatte tilsvarende kode i
 * [ReguleringAutomatiskServiceImpl] i en egen, senere omgang.
 */
internal object SakBatchKjøring {
    // DISKUSJON: navnet EKSTERN_OPPSLAG_BATCH_STORRELSE er egentlig grunnbeløp-spesifikt
    // (arvet fra ReguleringGrunnbeløpServiceImpl), men beholdt her for å matche
    // eksisterende kode ordrett. Bør trolig revurderes/omdøpes til noe mer generisk
    // (f.eks. BATCH_STORRELSE) når/hvis dette faktisk erstatter koden der.
    private const val EKSTERN_OPPSLAG_BATCH_STORRELSE = 50
    private val BATCH_SEMAPHORE = Semaphore(4)

    /**
     * Ytre lag: fanger ukjente feil, logger dem (i sikkerlogg + vanlig logg) og kaster
     * dem videre. Parallell til try/catch-blokken i
     * [ReguleringAutomatiskServiceImpl.startAutomatiskRegulering].
     *
     * @param operasjonNavn brukt kun i loggtekst, f.eks. "regulering" eller "omregning"
     * @param log loggeren til den kallende tjenesten (slik at loggene fortsatt viser riktig klasse)
     * @param block selve kjøringen, typisk et kall til [kjør]
     */
    fun <T> startAutomatisk(
        operasjonNavn: String,
        log: Logger,
        block: () -> T,
    ): T {
        return Either.catch { block() }
            .mapLeft {
                log.error(
                    "Ukjent feil skjedde ved automatisk $operasjonNavn. Se sikkerlogg for feilmelding.",
                    RuntimeException("Inkluderer stacktrace"),
                )
                sikkerLogg.error("Ukjent feil skjedde ved automatisk $operasjonNavn", it)
                throw it
            }
            .fold(
                ifLeft = { it },
                ifRight = { it },
            )
    }

    /**
     * Kjører [prosesserBatch] for alle [alleSaker], delt opp i batcher på
     * [BATCH_STORRELSE] saker, med maks 4 batcher kjørende samtidig.
     *
     * Hele kjøringen registreres som en aktiv langvarig jobb (se
     * [AktiveLangvarigeJobber]) med den gitte [navn] og [metadata].
     *
     * @param navn navnet på jobben, brukt i driftslogging (f.eks. "automatisk-regulering")
     * @param operasjonNavn brukt kun i loggtekst for hver batch, f.eks. "regulering" eller "omregning"
     * @param log loggeren til den kallende tjenesten (slik at loggene fortsatt viser riktig klasse)
     * @param alleSaker sakene som skal prosesseres
     * @param metadata ekstra informasjon om kjøringen, brukt i driftslogging
     * @param prosesserBatch gjør det faktiske arbeidet for én batch; får batchen,
     *  dens indeks (0-basert) og id-en til hele kjøringen
     * @param lagreFremgang kalles etter hver ferdig batch, for å lagre fremgang
     *  (f.eks. til [no.nav.su.se.bakover.domain.regulering.ReguleringKjøringFremgangRepo])
     * @return alle resultater fra alle batcher, flatt sammenslått
     */
    fun <Resultat> kjør(
        navn: String,
        operasjonNavn: String,
        log: Logger,
        alleSaker: List<SakInfo>,
        metadata: Map<String, String>,
        prosesserBatch: suspend (batch: List<SakInfo>, batchIndex: Int, kjøringId: UUID) -> List<Resultat>,
        lagreFremgang: (kjøringId: UUID, batchIndex: Int, antallSakerIBatch: Int, resultater: List<Resultat>) -> Unit,
    ): List<Resultat> = AktiveLangvarigeJobber.kjør(navn = navn, metadata = metadata) { kjøringId ->
        val batcher = alleSaker.chunked(EKSTERN_OPPSLAG_BATCH_STORRELSE)
        val totalBatcher = batcher.size

        runBlocking {
            batcher
                .mapIndexed { batchIndex, sakerPerBatch ->
                    async(Dispatchers.IO) {
                        BATCH_SEMAPHORE.withPermit {
                            log.info(
                                "Automatisk $operasjonNavn: Starter batch ${batchIndex + 1} av $totalBatcher. Antall saker i batch: ${sakerPerBatch.size}",
                            )
                            prosesserBatch(sakerPerBatch, batchIndex, kjøringId)
                                .also { lagreFremgang(kjøringId, batchIndex, sakerPerBatch.size, it) }
                        }
                    }
                }
                .awaitAll()
                .flatten()
        }
    }
}

