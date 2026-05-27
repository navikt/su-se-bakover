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
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import org.slf4j.LoggerFactory
import satser.domain.SatsFactory
import satser.domain.Satskategori
import vedtak.domain.GrunnbeløpOgSatsbeløpPåVedtak
import økonomi.domain.utbetaling.UtbetalingRepo
import økonomi.domain.utbetaling.UtbetalingslinjePåTidslinje
import økonomi.domain.utbetaling.hentGjeldendeUtbetaling
import java.time.Clock
import java.time.YearMonth
import java.util.UUID
import javax.jms.IllegalStateException
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

    fun produserStatusSisteGrunnbeløp(
        aar: Int,
        idPågående: UUID = UUID.randomUUID(),
    ): ReguleringStatus {
        val etterspurtMai = Måned.fra(YearMonth.of(aar, 5))
        log.info("hentStatusSisteGrunnbeløp for måned $etterspurtMai")

        val sisteBeløper = SisteGrunnbeløpOgSatser(
            grunnbeløp = satsFactory.grunnbeløp(etterspurtMai).grunnbeløpPerÅr,
            garantipensjonOrdinærMåned = satsFactory.forSatskategoriAlder(
                etterspurtMai,
                Satskategori.ORDINÆR,
            ).satsForMånedAsDouble,
            garantipensjonHøyMåned = satsFactory.forSatskategoriAlder(
                etterspurtMai,
                Satskategori.HØY,
            ).satsForMånedAsDouble,
        )

        log.info("hentStatusSisteGrunnbeløp - henter alle saker")
        val alleSaker = sakService.hentSakIdSaksnummerOgFnrForAlleSakerNyesteFørst()

        log.info("hentStatusSisteGrunnbeløp - henter saker med løpende utbetaling eller stans")
        val sakerMedUtbetalingOgStansMai = hentSakerMedLøpendeUtbetalingEllerStansForMåned(alleSaker, etterspurtMai)

        log.info("hentStatusSisteGrunnbeløp - utleder saker som har gammelt grunnbeløp")
        val sakerMedGammeltGrunnbeløp = sessionFactory.withTransactionContext { tx ->
            sakerMedUtbetalingOgStansMai.mapNotNull { sakInfo ->
                vedtakRepo.hentBruktGrunnbeløpOgSatsbeløpTilVedtakMedBeregning(sakInfo, etterspurtMai.fraOgMed, tx)
                    .let { enkelVedtakInfo ->
                        val (_, saksnummer, _, saktype) = sakInfo
                        if (enkelVedtakInfo.fraOgMed <= etterspurtMai.fraOgMed) {
                            if (erRegulertMedNyttGrunnbeløp(enkelVedtakInfo, saktype, sisteBeløper)) {
                                null
                            } else {
                                SakMedGammeltGrunnbeløp(
                                    saksnummer = saksnummer,
                                    type = saktype,
                                    benyttetGrunnbeløp = enkelVedtakInfo.benyttetGrunnbeløp,
                                    benyttetSatskategori = Satskategori.valueOf(enkelVedtakInfo.satskategori),
                                    benyttetSats = enkelVedtakInfo.benyttetSatsbeløp,
                                )
                            }
                        } else {
                            // hentBruktGrunnbeløpOgSatsbeløpTilVedtak henter bare nyligste vedtak,
                            // så om vedtak starter senere enn mai så må det hentes ytterligere info for å sjekke fom mai
                            val vedtakInfo =
                                vedtakRepo.hentVedtakSomKanRevurderesForSak(sakInfo.sakId, tx).toNonEmptyList().let {
                                    GjeldendeVedtaksdata(etterspurtMai, it, clock)
                                }
                            val beregning = vedtakInfo.hentMånedsberegning(etterspurtMai).singleOrNull()
                                ?: throw (IllegalStateException("Forventer kun én månedsberegning per måned"))
                            if (vedtakInfo.erRegulertMedNyttGrunnbeløp(
                                    etterspurtMai,
                                    sakInfo.type,
                                    satsFactory,
                                    sisteBeløper,
                                    beregning,
                                )
                            ) {
                                null
                            } else {
                                SakMedGammeltGrunnbeløp(
                                    saksnummer = saksnummer,
                                    type = saktype,
                                    benyttetGrunnbeløp = beregning.getBenyttetGrunnbeløp(),
                                    benyttetSatskategori = beregning.getSats(),
                                    benyttetSats = beregning.fullSupplerendeStønadForMåned.satsForMånedAsDouble,
                                )
                            }
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

    private fun erRegulertMedNyttGrunnbeløp(
        grunnbeløpOgSatsbeløpPåVedtak: GrunnbeløpOgSatsbeløpPåVedtak,
        sakstype: Sakstype,
        sisteBeløper: SisteGrunnbeløpOgSatser,
    ) = with(grunnbeløpOgSatsbeløpPåVedtak) {
        when (sakstype) {
            Sakstype.UFØRE -> benyttetGrunnbeløp == sisteBeløper.grunnbeløp
            Sakstype.ALDER -> when (Satskategori.valueOf(satskategori)) {
                Satskategori.ORDINÆR -> benyttetSatsbeløp == sisteBeløper.garantipensjonOrdinærMåned
                Satskategori.HØY -> benyttetSatsbeløp == sisteBeløper.garantipensjonHøyMåned
            }
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
    val sisteGrunnbeløpOgSatser: SisteGrunnbeløpOgSatser,
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

data class SisteGrunnbeløpOgSatser(
    val grunnbeløp: Int,
    val garantipensjonOrdinærMåned: Double,
    val garantipensjonHøyMåned: Double,
)
