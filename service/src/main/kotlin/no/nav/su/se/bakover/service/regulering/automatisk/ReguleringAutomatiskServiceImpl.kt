package no.nav.su.se.bakover.service.regulering.automatisk

import arrow.core.Either
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.job.AktiveLangvarigeJobber
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.regulering.BleIkkeRegulert
import no.nav.su.se.bakover.domain.regulering.EksternReguleringPerioderRepo
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.ReguleringAutomatiskService
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøring
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøringFremgang
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøringFremgangRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøringRepo
import no.nav.su.se.bakover.domain.regulering.ReguleringOppsummering
import no.nav.su.se.bakover.domain.regulering.ReguleringRepo
import no.nav.su.se.bakover.domain.regulering.Reguleringsresultat
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.StartAutomatiskReguleringForInnsynCommand
import no.nav.su.se.bakover.domain.regulering.logg
import no.nav.su.se.bakover.domain.regulering.toResultat
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.service.regulering.AapReguleringerService
import no.nav.su.se.bakover.service.regulering.ReguleringServiceImpl
import no.nav.su.se.bakover.service.regulering.ReguleringerFraPesysService
import no.nav.su.se.bakover.service.statistikk.SakStatistikkService
import org.slf4j.LoggerFactory
import satser.domain.SatsFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import kotlin.collections.joinToString

class ReguleringAutomatiskServiceImpl(
    private val reguleringRepo: ReguleringRepo,
    private val reguleringKjøringRepo: ReguleringKjøringRepo,
    private val reguleringKjøringFremgangRepo: ReguleringKjøringFremgangRepo,
    private val sakService: SakService,
    private val vedtakRepo: VedtakRepo,
    private val clock: Clock,
    private val satsFactory: SatsFactory,
    private val reguleringService: ReguleringServiceImpl,
    private val statistikkService: SakStatistikkService,
    private val sessionFactory: SessionFactory,
    private val reguleringerFraPesysService: ReguleringerFraPesysService,
    private val aapReguleringerService: AapReguleringerService,
    private val eksternReguleringPerioderRepo: EksternReguleringPerioderRepo,
) : ReguleringAutomatiskService {
    private val log = LoggerFactory.getLogger(this::class.java)

    private companion object {
        const val EKSTERN_OPPSLAG_BATCH_STORRELSE = 50
        private val BATCH_SEMAPHORE = Semaphore(4)
    }

    /**
     * Starter automatisk regulering av alle saker for en gitt måned.
     *
     * Henter alle saker, og gir hver sak til [automatiskReguleringBatchvis]. Returnerer
     * ett resultat per sak: enten [BleIkkeRegulert] (saken ble ikke regulert) eller
     * [ReguleringOppsummering] (saken ble regulert, automatisk eller manuelt).
     *
     * Ukjente feil logges i sikkerlogg og kastes videre.
     *
     * @param fraOgMedMåned måneden reguleringen gjelder fra og med
     * @param grunnbeløpRegulering om det er en grunnbeløpsregulering
     */
    override fun startAutomatiskRegulering(
        fraOgMedMåned: Måned,
        grunnbeløpRegulering: Boolean,
    ): List<Either<BleIkkeRegulert, ReguleringOppsummering>> {
        return Either.catch { automatiskReguleringBatchvis(fraOgMedMåned, satsFactory, grunnbeløpRegulering) }
            .mapLeft {
                log.error(
                    "Ukjent feil skjedde ved automatisk regulering for fraOgMedMåned: $fraOgMedMåned. Se sikkerlogg for feilmelding.",
                    RuntimeException("Inkluderer stacktrace"),
                )
                sikkerLogg.error("Ukjent feil skjedde ved automatisk regulering for fraOgMedMåned: $fraOgMedMåned", it)

                throw it
            }
            .fold(
                ifLeft = { it },
                ifRight = { it },
            )
    }

    /**
     * Starter automatisk regulering for innsyn, typisk en testkjøring.
     *
     * Kjører [automatiskReguleringBatchvis] med [ReguleringTestRun] fra [command] som
     * begrenser omfanget (kun sakstype, maks antall saker, om manuelle reguleringer
     * skal lagres) og med satsfabrikk fra commandens angitte satsdato.
     *
     * Ukjente feil logges i sikkerlogg, men kastes ikke videre (tjenesten er for
     * innsyn/testing).
     */
    override fun startAutomatiskReguleringForInnsyn(
        command: StartAutomatiskReguleringForInnsynCommand,
    ) {
        val factory = command.satsFactory.gjeldende(command.gjeldendeSatsFra)

        Either.catch {
            automatiskReguleringBatchvis(
                fraOgMedMåned = command.startDatoRegulering,
                satsFactory = factory,
                testRun = ReguleringTestRun(
                    lagreManuelle = command.lagreManuelle,
                    maksAntallSaker = command.maksAntallSaker,
                    kunSakstype = command.kunSakstype,
                ),
                grunnbeløpRegulering = command.grunnbeløpRegulering,
            )
        }.onLeft {
            log.error(
                "Ukjent feil skjedde ved automatisk regulering for innsyn for kommando: $command. Se sikkerlogg for flere detaljer.",
                RuntimeException("Inkluderer stacktrace"),
            )
            sikkerLogg.error("Ukjent feil skjedde ved automatisk regulering for innsyn for kommando: $command", it)
        }
    }

    /**
     * Henter saksinformasjon for alle saker og løper igjennom alle sakene ett etter ett.
     * Dette kan ta lang tid, så denne bør ikke kjøres synkront.
     *
     * Kjøringen registreres som en aktiv langvarig jobb (se [AktiveLangvarigeJobber]), mens
     * saker prosesseres batchvis ([EKSTERN_OPPSLAG_BATCH_STORRELSE] per batch) med begrenset
     * parallellitet. Når alle batcher er ferdige, lagres et samlet resultat med
     * [lagreResultat].
     *
     * // TODO dokumenter og marker ulike steg i metoden som reflekterer feiltyper
     *
     * @param fraOgMedMåned måneden reguleringen gjelder fra og med
     * @param satsFactory fabrikk for gjeldende satser
     * @param grunnbeløpRegulering om det er en grunnbeløpsregulering
     * @param testRun begrensninger for test-/innsynskjøringer, eller null for ordinær kjøring
     * @return ett resultat per sak (regulert eller ikke regulert)
     */
    private fun automatiskReguleringBatchvis(
        fraOgMedMåned: Måned,
        satsFactory: SatsFactory,
        grunnbeløpRegulering: Boolean,
        testRun: ReguleringTestRun? = null,
    ): List<Either<BleIkkeRegulert, ReguleringOppsummering>> = AktiveLangvarigeJobber.kjør(
        navn = "automatisk-regulering",
        metadata = mapOf(
            "fraOgMedMåned" to fraOgMedMåned.toString(),
            "dryrun" to (testRun != null).toString(),
            "grunnbeløpRegulering" to grunnbeløpRegulering.toString(),
        ),
    ) { kjøringId ->
        val startTid = LocalDateTime.now()
        log.info("Automatisk regulering: Starter for måned=$fraOgMedMåned, dryrun=${testRun != null} ${testRun?.let { ", maksAntall=${it.maksAntallSaker}, kunSakstype=${it.kunSakstype}" }}")
        val alleSaker = sakService.hentSakIdSaksnummerOgFnrForAlleSakerNyesteFørst()
            .let { saker -> testRun?.kunSakstype?.let { saker.filter { it.type == testRun.kunSakstype } } ?: saker }
            .let { saker -> testRun?.maksAntallSaker?.let { saker.take(it) } ?: saker }

        val totalBatcher = (alleSaker.size + EKSTERN_OPPSLAG_BATCH_STORRELSE - 1) / EKSTERN_OPPSLAG_BATCH_STORRELSE
        val resultater = runBlocking {
            alleSaker
                .chunked(EKSTERN_OPPSLAG_BATCH_STORRELSE)
                .mapIndexed { batchIndex, sakerPerBatch ->
                    async(Dispatchers.IO) {
                        BATCH_SEMAPHORE.withPermit {
                            log.info(
                                "Automatisk regulering: Starter batch ${batchIndex + 1} av $totalBatcher. Antall saker i batch: ${sakerPerBatch.size}",
                            )
                            sakerPerBatch.automatiskReguleringEnkeltBatch(
                                fraOgMedMåned,
                                grunnbeløpRegulering,
                                satsFactory,
                                testRun,
                                kjøringId,
                                batchIndex,
                            )
                        }
                    }
                }
                .awaitAll()
                .flatten()
        }
        resultater.also {
            lagreResultat(fraOgMedMåned, startTid, testRun, alleSaker, it, kjøringId)
        }
    }

    /**
     * Prosesserer én batch med saker gjennom de tre stegene i automatisk regulering:
     *
     * 1. Klargjøring: henter vedtaksdata og vurderer om hver sak skal reguleres
     *    ([HentVedtaksdataOgVurderOmReguleres]).
     * 2. Klargjøring: henter eksterne regulerte beløp (Pesys og AAP)
     *    ([HentEksterneBeløper]).
     * 3. Utførelse: kjører selve reguleringsbehandlingen per sak
     *    ([UtførAutomatiskBehandlingRegulering]).
     *    Resulterer i en [ReguleringOppsummering] enten med type [Reguleringstype.MANUELL] eller [Reguleringstype.AUTOMATISK]r
     *
     * Fremgangen for batchen lagres med [lagreBatchFremgang], slik at kjøringer kan
     * følges underveis og gjenopptas etter avbrudd.
     *
     * @param fraOgMedMåned måneden reguleringen gjelder fra og med
     * @param grunnbeløpRegulering om det er en grunnbeløpsregulering
     * @param satsFactory fabrikk for gjeldende satser
     * @param testRun begrensninger for test-/innsynskjøringer, eller null for ordinær kjøring
     * @param kjøringId identifikator for den overordnede kjøringen
     * @param batchIndex indeks for denne batchen (0-basert)
     * @return ett resultat per sak i batchen
     */
    private fun List<SakInfo>.automatiskReguleringEnkeltBatch(
        fraOgMedMåned: Måned,
        grunnbeløpRegulering: Boolean,
        satsFactory: SatsFactory,
        testRun: ReguleringTestRun?,
        kjøringId: UUID,
        batchIndex: Int,
    ): List<Either<BleIkkeRegulert, ReguleringOppsummering>> {
        val sakerPerBatch = this

        // STEG 1 - KLARGJØRING: Vedtaksdata og vurder om skal regulere
        val tidSakVedtaksdata = LocalDateTime.now()
        log.info("Automatisk regulering: Henter sak og vedtaksinfo for batch.")
        val sakerEtterSteg1 =
            HentVedtaksdataOgVurderOmReguleres(
                satsFactory,
                clock,
                reguleringRepo,
                vedtakRepo,
            ).hent(sakerPerBatch, fraOgMedMåned, grunnbeløpRegulering)
        log.info(
            loggMedTidsbruk(
                "Automatisk regulering: Henter sak og vedtaksinfo fullført for batch",
                tidSakVedtaksdata,
            ),
        )

        // STEG 2 - KLARGJØRING: Hent eksterne beløper
        val tidEksterneBeløp = LocalDateTime.now()
        log.info("Automatisk regulering: Henter eksterne beløp for batch.")
        val (sakerEtterSteg2, eksterntRegulerteBeløp) = HentEksterneBeløper(
            reguleringerFraPesysService,
            aapReguleringerService,
            eksternReguleringPerioderRepo,
            satsFactory,
        ).hent(sakerEtterSteg1, fraOgMedMåned, kjøringId)
        log.info(
            loggMedTidsbruk(
                "Automatisk regulering: Henter eksterne beløp for batch",
                tidEksterneBeløp,
            ),
        )

        // STEG 3 - UTFØRELSE AV REGULERING
        val tidKjørReguleringForSaker = LocalDateTime.now()
        log.info("Automatisk regulering: kjører regulering for saker fra batch.")
        val sakerEtterSteg3 = UtførAutomatiskBehandlingRegulering(
            reguleringService,
            reguleringRepo,
            satsFactory,
            sessionFactory,
            statistikkService,
            clock,
        ).utfør(sakerEtterSteg2, eksterntRegulerteBeløp, testRun)
        log.info(
            loggMedTidsbruk(
                "Automatisk regulering: kjører regulering for saker fra batch",
                tidKjørReguleringForSaker,
            ),
        )
        lagreBatchFremgang(kjøringId, batchIndex, sakerPerBatch.size, sakerEtterSteg3)
        return sakerEtterSteg3
    }

    /**
     * Aggregerer resultatene fra en komplett kjøring til en [ReguleringKjøring] og lagrer den.
     *
     * Resultatene grupperes etter [Reguleringsresultat.Utfall] og telles opp per utfall
     * (f.eks. IKKE_LOEPENDE, ALLEREDE_REGULERT, AUTOMATISK, MANUELL, FEILET). Det samlede
     * resultatet lagres og logges, slik at utfallet av kjøringen kan følges i drift.
     *
     * @param fraOgMedMåned måneden reguleringen gjelder fra og med
     * @param startTid tidspunktet kjøringen startet
     * @param testRun begrensninger for test-/innsynskjøringer, eller null for ordinær kjøring
     * @param alleSaker alle saker som ble vurdert
     * @param resultater ett resultat per sak
     * @param kjøringId identifikator for kjøringen
     */
    private fun lagreResultat(
        fraOgMedMåned: Måned,
        startTid: LocalDateTime,
        testRun: ReguleringTestRun? = null,
        alleSaker: List<SakInfo>,
        resultater: List<Either<BleIkkeRegulert, ReguleringOppsummering>>,
        kjøringId: UUID,
    ) {
        val resultaterPerUtfall = resultater.map { it.tilReguleringsresultat() }.groupBy { it.utfall }

        val reguleringKjøring = ReguleringKjøring(
            id = kjøringId,
            aar = fraOgMedMåned.årOgMåned.year,
            type = ReguleringKjøring.REGULERINGSTYPE_GRUNNBELØP,
            dryrun = testRun != null,
            startTid = startTid,
            sakerAntall = alleSaker.size,
            sakerIkkeLøpende = resultaterPerUtfall[Reguleringsresultat.Utfall.IKKE_LOEPENDE].orEmpty(),
            sakerAlleredeRegulert = resultaterPerUtfall[Reguleringsresultat.Utfall.ALLEREDE_REGULERT].orEmpty(),
            sakerMåRevurderes = resultaterPerUtfall[Reguleringsresultat.Utfall.MÅ_REVURDERE].orEmpty(),
            reguleringerSomFeilet = resultaterPerUtfall[Reguleringsresultat.Utfall.FEILET].orEmpty(),
            reguleringerAlleredeÅpen = resultaterPerUtfall[Reguleringsresultat.Utfall.AAPEN_REGULERING].orEmpty(),
            reguleringerManuell = resultaterPerUtfall[Reguleringsresultat.Utfall.MANUELL].orEmpty(),
            reguleringerAutomatisk = resultaterPerUtfall[Reguleringsresultat.Utfall.AUTOMATISK].orEmpty(),
        )
        reguleringKjøringRepo.lagre(reguleringKjøring)
        log.info(reguleringKjøring.logg())
    }

    /**
     * Lagrer en snapshot av batchen i [regulering_kjoring_fremgang]. Idempotent på
     * (kjøringId, batchNummer) — flere kall med samme nøkkel blir no-op i DB.
     *
     * Pakkes i [Either.catch] slik at en lagrings-feil (f.eks. midlertidig DB-glipp)
     * ikke avbryter selve reguleringsjobben — vi mister da fremgang for den batchen,
     * men fortsetter videre.
     */
    private fun lagreBatchFremgang(
        kjøringId: UUID,
        batchIndex: Int,
        sakerIBatch: Int,
        batchResultater: List<Either<BleIkkeRegulert, ReguleringOppsummering>>,
    ) {
        Either.catch {
            reguleringKjøringFremgangRepo.lagre(
                ReguleringKjøringFremgang(
                    kjøringId = kjøringId,
                    batchNummer = batchIndex,
                    tidspunkt = Instant.now(clock),
                    sakerIBatch = sakerIBatch,
                    resultater = batchResultater.map { it.tilReguleringsresultat() },
                ),
            )
        }.onLeft {
            log.warn(
                "Automatisk regulering: Klarte ikke lagre fremgang for batch=$batchIndex, kjøringId=$kjøringId. Jobben fortsetter.",
                it,
            )
        }
    }
}

/**
 * Bygger en loggmelding med tidsbruk i sekunder siden [initiellTid].
 */
private fun loggMedTidsbruk(melding: String, initiellTid: LocalDateTime) =
    "$melding, tidsbrukSekunder=${Duration.between(initiellTid, LocalDateTime.now()).seconds}"

/**
 * Oversetter et reguleringsresultat per sak (f.eks. et [BleIkkeRegulert]-utfall eller en
 * [ReguleringOppsummering]) til en felles [Reguleringsresultat] med utfall og beskrivelse.
 *
 * Resultatet brukes til å gruppere og telle utfallet av en kjøring, og til fremgangssnapshots
 * per batch.
 */
private fun Either<BleIkkeRegulert, ReguleringOppsummering>.tilReguleringsresultat(): Reguleringsresultat = fold(
    ifLeft = { bleIkkeRegulert ->
        when (bleIkkeRegulert) {
            is BleIkkeRegulert.TrengerIkkeRegulere.IkkeLøpendeSak -> bleIkkeRegulert.toResultat(Reguleringsresultat.Utfall.IKKE_LOEPENDE)
            is BleIkkeRegulert.TrengerIkkeRegulere.AlleredeRegulert -> bleIkkeRegulert.toResultat(Reguleringsresultat.Utfall.ALLEREDE_REGULERT)
            is BleIkkeRegulert.TrengerIkkeRegulere.FinnesÅpenRegulering -> bleIkkeRegulert.toResultat(
                Reguleringsresultat.Utfall.AAPEN_REGULERING,
                bleIkkeRegulert.toString(),
            )

            is BleIkkeRegulert.MåRegulereMedRevurdering -> bleIkkeRegulert.toResultat(
                Reguleringsresultat.Utfall.MÅ_REVURDERE,
                bleIkkeRegulert.årsak.toString(),
            )

            is BleIkkeRegulert.FantIkkeSak,
            is BleIkkeRegulert.KunneIkkeBehandleAutomatisk,
            is BleIkkeRegulert.ReguleringFeiletVedKlargjøring.UthentingAvVedtakFeilet,
            is BleIkkeRegulert.ReguleringFeiletVedKlargjøring.UthentingFradragEksterntFeilet,
            -> bleIkkeRegulert.toResultat(Reguleringsresultat.Utfall.FEILET, bleIkkeRegulert.toString())
        }
    },
    ifRight = { oppsummering ->
        when (val type = oppsummering.reguleringstype) {
            is Reguleringstype.MANUELL -> oppsummering.toResultat(
                utfall = Reguleringsresultat.Utfall.MANUELL,
                beskrivelse = type.problemer.joinToString(", ") { it.kategori.name },
            )

            Reguleringstype.AUTOMATISK -> oppsummering.toResultat(
                utfall = Reguleringsresultat.Utfall.AUTOMATISK,
                beskrivelse = oppsummering.toString(),
            )
        }
    },
)

/*
* Konfigurasjon av automatisk regulering for å kunne teste på ulike måter.
*/
internal data class ReguleringTestRun(
    val lagreManuelle: Boolean = false,
    val maksAntallSaker: Int? = null,
    val kunSakstype: Sakstype? = null,
) {
    /*
     * Det kan være ønskelig å få opprettet manuelle reguleringer uten å faktisk innføre et nytt grunnbeløp i systemet.
     * Da kan dry run med kunstig grunnbeløp benyttes med valget om å lagre manuelle reguleringer.
     * Selve reguleringen vil benytte eksisterende beløp etter den er opprettet men behovet er først og fremst å få
     * den manuelle reguleringen opprettet for å teste flyt ikke beregning.
     */
    /**
     * Avgjør om en manuell regulering skal lagres under en dry run (test/innsynskjøring).
     *
     * @return true bare når vi ikke kjører i produksjon, manuelle reguleringer skal lagres,
     *         og den gitte reguleringen er manuell
     */
    fun lagreManuelleUnderDryRun(regulering: Regulering) =
        ApplicationConfig.isNotProd() && lagreManuelle && regulering.reguleringstype is Reguleringstype.MANUELL
}
