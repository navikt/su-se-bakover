package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import beregning.domain.Månedsberegning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.lagTidslinje
import org.slf4j.LoggerFactory
import satser.domain.SatsFactory
import satser.domain.Satskategori
import java.time.YearMonth
import java.util.UUID
import kotlin.collections.isNotEmpty

class ReguleringStatusUteståendeService(
    private val sakService: SakService,
    private val vedtakRepo: VedtakRepo,
    private val satsFactory: SatsFactory,
    private val reguleringStatusRepo: ReguleringStatusUteståendeRepo,
    private val reguleringRepo: ReguleringRepo,
    private val sessionFactory: SessionFactory,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private companion object {
        const val BATCH_STØRRELSE = 50

        // Gjelder kun per pod. Samtidighet mellom poder må håndteres av leader-/trigger-laget og er utenfor denne endringen.
        val BATCH_SEMAPHORE = Semaphore(4)
    }

    private data class BatchResultat(
        val antallLøpende: Int,
        val antallSakerMedGammeltGrunnbeløp: Int,
        val sakerUtenÅpenRegulering: List<SakMedGammeltGrunnbeløp>,
    )

    fun hentSisteStatusoversikter() = reguleringStatusRepo.hent()

    fun produserStatusSisteGrunnbeløpAsync(aar: Int): Either<StatusPågående, StatusFullført> {
        if (reguleringStatusRepo.hentPågående().isNotEmpty()) {
            return StatusPågående.left()
        }
        CoroutineScope(Dispatchers.IO).launch {
            val idPågående = reguleringStatusRepo.lagreOppstartet()
            Either.catch {
                produserStatusSisteGrunnbeløp(aar, idPågående)
            }.mapLeft {
                log.error(
                    "produserStatusSisteGrunnbeløp - Feil ved produksjon av status for siste grunnbeløp for år $aar",
                    it,
                )
                reguleringStatusRepo.lagreFeilet(idPågående)
            }
        }
        return StatusFullført.right()
    }

    /**
     * Produserer en [ReguleringStatus] som gir oversikt over hvilke saker som ikke er regulert med siste grunnbeløp
     * for alle sine perioder etter mai for angitt år.
     * Saker som benytter gammelt grunnbeløp returneres som [SakMedGammeltGrunnbeløp].
     */
    fun produserStatusSisteGrunnbeløp(
        aar: Int,
        idPågående: UUID = UUID.randomUUID(),
    ): ReguleringStatus {
        val etterspurtMai = Måned.fra(YearMonth.of(aar, 5))
        log.info("hentStatusSisteGrunnbeløp for måned $etterspurtMai")

        val alleSaker = sakService.hentSakIdSaksnummerOgFnrForAlleSakerNyesteFørst()
        val sisteBeløp = satsFactory.grunnbeløpOgGarantipensjon(etterspurtMai)
        val åpneReguleringer = reguleringRepo.hentStatusForÅpneManuelleReguleringerEnkel()
            .mapTo(mutableSetOf()) { it.saksnummer }
        val totalBatcher = (alleSaker.size + BATCH_STØRRELSE - 1) / BATCH_STØRRELSE
        val batchResultater = runBlocking {
            alleSaker
                .chunked(BATCH_STØRRELSE)
                .mapIndexed { batchIndex, sakerPerBatch ->
                    async(Dispatchers.IO) {
                        BATCH_SEMAPHORE.withPermit {
                            log.info(
                                "hentStatusSisteGrunnbeløp starter batch ${batchIndex + 1}/$totalBatcher, antall saker=${sakerPerBatch.size}",
                            )
                            sessionFactory.withTransactionContext { tx ->
                                val vedtakPerSak = vedtakRepo.hentVedtakSomKanRevurderesForSakerFraOgMed(
                                    sakIder = sakerPerBatch.map { it.sakId },
                                    fraOgMed = etterspurtMai,
                                    tx = tx,
                                )
                                val sakInfoMedVedtakTidslinje = sakerPerBatch.mapNotNull { sak ->
                                    val vedtakstidslinje =
                                        vedtakPerSak[sak.sakId].orEmpty().lagTidslinje()?.fjernMånederFør(etterspurtMai).let { tidslinje ->
                                            (tidslinje ?: emptyList()).filterNot { it.erOpphør() }
                                        }
                                    if (vedtakstidslinje.isNotEmpty()) {
                                        sak to vedtakstidslinje
                                    } else {
                                        null
                                    }
                                }

                                val sakerMedGammeltGrunnbeløp = sakInfoMedVedtakTidslinje.mapNotNull { (sakInfo, vedtaksdata) ->
                                    vedtaksdata.firstNotNullOfOrNull {
                                        val beregning = it.originaltVedtak.beregning
                                        if (beregning != null) {
                                            val månedsbesberegning: Månedsberegning = beregning.getMånedsberegninger().first {
                                                // Selv om tidslinje er satt fom mai så har orginalt vedtak fortsatt tidligere perioder
                                                it.periode.fraOgMed >= etterspurtMai.fraOgMed
                                            }
                                            if (sisteBeløp.erRegulertMedNyttGrunnbeløp(sakInfo.type, månedsbesberegning)) {
                                                null
                                            } else {
                                                SakMedGammeltGrunnbeløp(
                                                    saksnummer = sakInfo.saksnummer,
                                                    type = sakInfo.type,
                                                    benyttetGrunnbeløp = månedsbesberegning.getBenyttetGrunnbeløp(),
                                                    benyttetSatskategori = månedsbesberegning.getSats(),
                                                    benyttetSats = månedsbesberegning.getSatsbeløp(),
                                                )
                                            }
                                        } else {
                                            // Hvis beregning mangler skyldes det stans/gjenopptak og info må hente det som var gjeldende vedtak før stans
                                            val beregningInfoVedtak =
                                                vedtakRepo.hentBeregninginfoTilVedtakPåDato(sakInfo, it.periode.fraOgMed, tx = tx)
                                            if (sisteBeløp.erRegulertMedNyttGrunnbeløp(sakInfo.type, beregningInfoVedtak)) {
                                                log.info("hentStatusSisteGrunnbeløp for sak ${sakInfo.saksnummer} - er regulert (beregningInfoVedtak )")
                                                null
                                            } else {
                                                SakMedGammeltGrunnbeløp(
                                                    saksnummer = sakInfo.saksnummer,
                                                    type = sakInfo.type,
                                                    benyttetGrunnbeløp = beregningInfoVedtak.benyttetGrunnbeløp,
                                                    benyttetSatskategori = Satskategori.valueOf(beregningInfoVedtak.satskategori),
                                                    benyttetSats = beregningInfoVedtak.benyttetSatsbeløp,
                                                )
                                            }
                                        }
                                    }
                                }
                                BatchResultat(
                                    antallLøpende = sakInfoMedVedtakTidslinje.size,
                                    antallSakerMedGammeltGrunnbeløp = sakerMedGammeltGrunnbeløp.size,
                                    sakerUtenÅpenRegulering = sakerMedGammeltGrunnbeløp.filterNot {
                                        it.saksnummer in åpneReguleringer
                                    },
                                )
                            }.also {
                                log.info("hentStatusSisteGrunnbeløp fullførte batch ${batchIndex + 1}/$totalBatcher")
                            }
                        }
                    }
                }
                .awaitAll()
        }

        val antallLøpende = batchResultater.sumOf { it.antallLøpende }
        val antallSakerMedGammeltGrunnbeløp = batchResultater.sumOf { it.antallSakerMedGammeltGrunnbeløp }
        val sakerUtenÅpenRegulering = batchResultater.flatMap { it.sakerUtenÅpenRegulering }

        log.info("hentStatusSisteGrunnbeløp - utleding av saker som har gammelt grunnbeløp fullført, antall=$antallSakerMedGammeltGrunnbeløp")
        val produsertStatusoversikt = ReguleringStatus(
            aar = etterspurtMai.fraOgMed.year,
            sisteGrunnbeløpOgSatser = sisteBeløp,
            sakerMedUtebetalingIMai = antallLøpende,
            sakerMedGammelG = antallSakerMedGammeltGrunnbeløp,
            utenÅpenRegulering = sakerUtenÅpenRegulering,
        )
        reguleringStatusRepo.lagreProdusert(idPågående, produsertStatusoversikt)
        return produsertStatusoversikt
    }

    fun slettPågåendeStatus(): Either<FantIkkePågåendeStatus, Unit> {
        val pågående = reguleringStatusRepo.hentPågående()
        if (pågående.isEmpty()) {
            return FantIkkePågåendeStatus.left()
        }
        val id = pågående.single().id
        reguleringStatusRepo.slettPågående(id)
        return Unit.right()
    }
}

object StatusPågående
object StatusFullført

object FantIkkePågåendeStatus

/**
 * Representerer en produksjon av [ReguleringStatus], som er selve oversikten over om SU saker er regulert.
 * [ProduserStatus] er statusen på produseringen av [ReguleringStatus].
 */
data class ProdusertReguleringStatus(
    val id: UUID,
    val produserStatus: ProduserStatus,
    val reguleringStatus: ReguleringStatus?,
) {
    enum class ProduserStatus {
        Pågående,
        Fullført,
        Feilet,
    }
}

data class ReguleringStatus(
    val aar: Int,
    val sisteGrunnbeløpOgSatser: SatsFactory.SisteGrunnbeløpOgSatser,
    val sakerMedUtebetalingIMai: Int,
    val sakerMedGammelG: Int,
    val utenÅpenRegulering: List<SakMedGammeltGrunnbeløp>,
)

data class SakMedGammeltGrunnbeløp(
    val saksnummer: Saksnummer,
    val type: Sakstype,
    val benyttetGrunnbeløp: Int?, // Kun uføre
    val benyttetSatskategori: Satskategori,
    val benyttetSats: Double,
)
