package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import org.slf4j.LoggerFactory
import satser.domain.SatsFactory
import satser.domain.Satskategori
import økonomi.domain.utbetaling.UtbetalingRepo
import økonomi.domain.utbetaling.UtbetalingslinjePåTidslinje
import økonomi.domain.utbetaling.hentGjeldendeUtbetaling
import java.time.Clock
import java.time.YearMonth
import java.util.UUID
import kotlin.collections.isNotEmpty
import kotlin.collections.map

class ReguleringStatusUteståendeService(
    private val sakService: SakService,
    private val utbetalingRepo: UtbetalingRepo,
    private val vedtakRepo: VedtakRepo,
    private val satsFactory: SatsFactory,
    private val reguleringStatusRepo: ReguleringStatusUteståendeRepo,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
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
     * Produserer en [ReguleringStatus] som gir oversikt over hvilke saker som ikke er regulert med siste grunnbeløp for mai i angitt år.
     * Henter alle saker med løpende utbetaling eller stans i mai, og sjekker om vedtakets beregning benytter gjeldende grunnbeløp og satser.
     * Saker som benytter gammelt grunnbeløp returneres som [SakMedGammeltGrunnbeløp].
     *
     * [VedtakRepo.hentBeregninginfoTilVedtakPåDato] benyttes til å hente informasjom om brukt grunnbeløp.
     * hentBeregninginfoTilVedtakPåDato henter det nyligste vedtaket som har en beregning, ikke vedtak for stans/gjenopptak.
     * Det returneres kun et vedtak som vil si at at det ikke nødvendigvis vil dekke alle perioder fra og med mai.
     * Dette løses på følgende måte:
     * 1. henter nyligste vedtak med parameter ogFremtidige=true, som gjør at det hentes det nyligste fra og med mai.
     * 2. hvis vedtaket starter frem i tid for mai så hentes alle vedtaksperioder fra og med mai ved å bruke [VedtakRepo.hentVedtakSomKanRevurderesForSak]
     * 3. Info om grunnbeløp per periode hentes med [VedtakRepo.hentBeregninginfoTilVedtakPåDato] med ogFremtidige=false.
     *   hentBeregninginfoTilVedtakPåDato må brukes per peridoe fordi vedtaksperoder fra hentVedtakSomKanRevurderesForSak
     *   kan inneholde perioder som mangler beregningsinformasjon fordi det er stans/gjenopptak.
     */
    fun produserStatusSisteGrunnbeløp(
        aar: Int,
        idPågående: UUID = UUID.randomUUID(),
    ): ReguleringStatus {
        val etterspurtMai = Måned.fra(YearMonth.of(aar, 5))
        log.info("hentStatusSisteGrunnbeløp for måned $etterspurtMai")

        val alleSaker = sakService.hentSakIdSaksnummerOgFnrForAlleSakerNyesteFørst()
        val sakerMedUtbetalingOgStansMai = hentSakerMedLøpendeUtbetalingEllerStansForMåned(alleSaker, etterspurtMai)
        val sisteBeløper = satsFactory.grunnbeløpOgGarantipensjon(etterspurtMai)

        val sakerMedGammeltGrunnbeløp = sessionFactory.withTransactionContext { tx ->
            sakerMedUtbetalingOgStansMai.mapNotNull { sakInfo ->
                vedtakRepo.hentBeregninginfoTilVedtakPåDato(
                    sakInfo = sakInfo,
                    dato = etterspurtMai.fraOgMed,
                    ogFremtidige = true,
                    tx = tx,
                )
                    .let { beregningsinfoFraVedtak ->
                        val (_, saksnummer, _, saktype) = sakInfo
                        if (beregningsinfoFraVedtak.fraOgMed <= etterspurtMai.fraOgMed) {
                            if (sisteBeløper.erRegulertMedNyttGrunnbeløp(saktype, beregningsinfoFraVedtak)) {
                                null
                            } else {
                                SakMedGammeltGrunnbeløp(
                                    saksnummer = saksnummer,
                                    type = saktype,
                                    benyttetGrunnbeløp = beregningsinfoFraVedtak.benyttetGrunnbeløp,
                                    benyttetSatskategori = Satskategori.valueOf(beregningsinfoFraVedtak.satskategori),
                                    benyttetSats = beregningsinfoFraVedtak.benyttetSatsbeløp,
                                )
                            }
                        } else {
                            val vedtaksdataFraMai =
                                vedtakRepo.hentVedtakSomKanRevurderesForSakFraOgMed(sakInfo.sakId, etterspurtMai, tx).toNonEmptyList().let {
                                    val tilOgMed = it.last().periode.tilOgMed
                                    GjeldendeVedtaksdata(
                                        Periode.create(etterspurtMai.fraOgMed, tilOgMed),
                                        it,
                                        clock,
                                    )
                                }

                            var sakMedGammelt: SakMedGammeltGrunnbeløp? = null
                            vedtaksdataFraMai.vedtaksperioder.firstOrNull {
                                val vedtakinfo =
                                    vedtakRepo.hentBeregninginfoTilVedtakPåDato(sakInfo, it.fraOgMed, tx = tx)
                                if (sisteBeløper.erRegulertMedNyttGrunnbeløp(saktype, vedtakinfo)) {
                                    false
                                } else {
                                    sakMedGammelt = SakMedGammeltGrunnbeløp(
                                        saksnummer = saksnummer,
                                        type = saktype,
                                        benyttetGrunnbeløp = vedtakinfo.benyttetGrunnbeløp,
                                        benyttetSatskategori = Satskategori.valueOf(vedtakinfo.satskategori),
                                        benyttetSats = vedtakinfo.benyttetSatsbeløp,
                                    )
                                    true
                                }
                            }
                            sakMedGammelt
                        }
                    }
            }
        }

        log.info("hentStatusSisteGrunnbeløp - utleding av saker som har gammelt grunnbeløp fullført, antall=${sakerMedGammeltGrunnbeløp.size}")
        val produsertStatusoversikt = ReguleringStatus(
            aar = etterspurtMai.fraOgMed.year,
            sisteGrunnbeløpOgSatser = sisteBeløper,
            sakerMedUtebetalingIMai = sakerMedUtbetalingOgStansMai.size,
            sakerMedGammelG = sakerMedGammeltGrunnbeløp,
        )
        reguleringStatusRepo.lagreProdusert(idPågående, produsertStatusoversikt)
        return produsertStatusoversikt
    }

    private fun hentSakerMedLøpendeUtbetalingEllerStansForMåned(
        saker: List<SakInfo>,
        måned: Måned,
    ): List<SakInfo> {
        if (saker.isEmpty()) return emptyList()

        val utbetalingerPerSak = utbetalingRepo.hentOversendteUtbetalingerForSakIder(
            saker.map { it.sakId },
        )

        return saker.mapNotNull { sak ->
            utbetalingerPerSak[sak.sakId]?.hentGjeldendeUtbetaling(måned.fraOgMed)?.fold(
                { null },
                {
                    when (it) {
                        is UtbetalingslinjePåTidslinje.Reaktivering,
                        is UtbetalingslinjePåTidslinje.Ny,
                        is UtbetalingslinjePåTidslinje.Stans,
                        -> sak

                        is UtbetalingslinjePåTidslinje.Opphør -> null
                    }
                },
            )
        }
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
    val sakerMedGammelG: List<SakMedGammeltGrunnbeløp>,
)

data class SakMedGammeltGrunnbeløp(
    val saksnummer: Saksnummer,
    val type: Sakstype,
    val benyttetGrunnbeløp: Int?, // Kun uføre
    val benyttetSatskategori: Satskategori,
    val benyttetSats: Double,
)
