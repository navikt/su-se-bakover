package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import beregning.domain.Månedsberegning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
import kotlin.collections.map

class ReguleringStatusUteståendeService(
    private val sakService: SakService,
    private val vedtakRepo: VedtakRepo,
    private val satsFactory: SatsFactory,
    private val reguleringStatusRepo: ReguleringStatusUteståendeRepo,
    private val reguleringRepo: ReguleringRepo,
    private val sessionFactory: SessionFactory,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

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
        val sisteBeløper = satsFactory.grunnbeløpOgGarantipensjon(etterspurtMai)

        val (løpende, sakerMedGammeltGrunnbeløp) = sessionFactory.withTransactionContext { tx ->
            val sakInfoMedVedtakTidslinje = alleSaker.mapNotNull { sak ->
                val vedtakSomKanRevurderes =
                    vedtakRepo.hentVedtakSomKanRevurderesForSakFraOgMed(sak.sakId, etterspurtMai, tx)
                val vedtakstidslinje =
                    vedtakSomKanRevurderes.lagTidslinje()?.fjernMånederFør(etterspurtMai).let { tidslinje ->
                        (tidslinje ?: emptyList()).filterNot { it.erOpphør() }
                    }
                if (vedtakstidslinje.isNotEmpty()) {
                    sak to vedtakstidslinje
                } else {
                    null
                }
            }

            // sender med sakInfoMedVedtakTidslinje for å ha med antall senere
            sakInfoMedVedtakTidslinje to sakInfoMedVedtakTidslinje.mapNotNull { (sakInfo, vedtaksdata) ->
                vedtaksdata.firstNotNullOfOrNull {
                    val beregning = it.originaltVedtak.beregning
                    if (beregning != null) {
                        val månedsbesberegning: Månedsberegning = beregning.getMånedsberegninger().first {
                            // Selv om tidslinje er satt fom mai så har orginalt vedtak fortsatt tidligere perioder
                            it.periode.fraOgMed >= etterspurtMai.fraOgMed
                        }
                        if (sisteBeløper.erRegulertMedNyttGrunnbeløp(sakInfo.type, månedsbesberegning)) {
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
                        if (sisteBeløper.erRegulertMedNyttGrunnbeløp(sakInfo.type, beregningInfoVedtak)) {
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
        }

        val åpneReguleringer = reguleringRepo.hentStatusForÅpneManuelleReguleringerEnkel().map { it.saksnummer }
        val sakerUtenÅpenRegulering = sakerMedGammeltGrunnbeløp.filter {
            åpneReguleringer.contains(it.saksnummer).not()
        }

        log.info("hentStatusSisteGrunnbeløp - utleding av saker som har gammelt grunnbeløp fullført, antall=${sakerMedGammeltGrunnbeløp.size}")
        val produsertStatusoversikt = ReguleringStatus(
            aar = etterspurtMai.fraOgMed.year,
            sisteGrunnbeløpOgSatser = sisteBeløper,
            sakerMedUtebetalingIMai = løpende.size,
            sakerMedGammelG = sakerMedGammeltGrunnbeløp.size,
            utenÅpenRegulering = sakerUtenÅpenRegulering,
        )
        reguleringStatusRepo.lagreProdusert(idPågående, produsertStatusoversikt)
        return produsertStatusoversikt
    }
}

object StatusPågående
object StatusFullført

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
