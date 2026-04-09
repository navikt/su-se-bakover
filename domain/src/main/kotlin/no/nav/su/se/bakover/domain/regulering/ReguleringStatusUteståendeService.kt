package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
import io.ktor.util.date.Month
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.extensions.split
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.sak.SakService
import satser.domain.SatsFactory
import satser.domain.Satskategori
import økonomi.domain.utbetaling.UtbetalingRepo
import økonomi.domain.utbetaling.hentGjeldendeUtbetaling
import java.time.Clock
import java.time.Year
import java.time.YearMonth
import kotlin.collections.filter
import kotlin.collections.map

class ReguleringStatusUteståendeService(
    val sakService: SakService,
    val utbetalingRepo: UtbetalingRepo,
    val satsFactory: SatsFactory,
    val clock: Clock,
) {

    fun hentStatusSisteGrunnbeløp(): ReguleringStatus {
        val sisteMai = hentSisteMai(clock)
        val sisteBeløper = SisteGrunnbeløpOgSatser(
            grunnbeløp = satsFactory.grunnbeløp(sisteMai).grunnbeløpPerÅr,
            garantipensjonOrdinær = satsFactory.ordinærAlder(sisteMai).garantipensjonForMåned.garantipensjonPerÅr,
            garantipensjonHøy = satsFactory.høyAlder(sisteMai).garantipensjonForMåned.garantipensjonPerÅr,
        )

        val alleSaker = sakService.hentSakIdSaksnummerOgFnrForAlleSaker()
        val sakerMedUtbetalingMai = hentSakerMedLøpendeUtbetalingForMåned(alleSaker, sisteMai)
        val (løpendeSakerIkkefunnet, løpendeSaker) = sakerMedUtbetalingMai.split()

        val sakerMedGammeltGrunnbeløp = løpendeSaker.mapNotNull {
            val beregning = it.hentGjeldendeMånedsberegninger(sisteMai, clock).singleOrNull()
                ?: throw (IllegalStateException("Forventer kun én månedsberegning per måned"))

            val benyttetG = beregning.getBenyttetGrunnbeløp()
            val kategori = beregning.getSats()
            val benyttetSats = beregning.fullSupplerendeStønadForMåned.sats.sats.toDouble()

            val gammeltBeløp = when (it.type) {
                Sakstype.UFØRE -> benyttetG != sisteBeløper.grunnbeløp
                Sakstype.ALDER -> when (kategori) {
                    Satskategori.ORDINÆR -> benyttetSats != sisteBeløper.garantipensjonOrdinær.toDouble()
                    Satskategori.HØY -> benyttetSats != sisteBeløper.garantipensjonHøy.toDouble()
                }
            }
            if (gammeltBeløp) {
                SakMedGammeltGrunnbeløp(
                    saksnummer = it.saksnummer,
                    type = it.type,
                    benyttetGrunnbeløp = benyttetG,
                    benyttetSatskategori = kategori,
                    benyttetSats = benyttetSats,
                )
            } else {
                null
            }
        }

        return ReguleringStatus(
            aar = sisteMai.fraOgMed.year,
            sisteGrunnbeløpOgSatser = sisteBeløper,
            sakerMedUtebetalingIMai = sakerMedUtbetalingMai.size,
            sakerMedGammelG = sakerMedGammeltGrunnbeløp,
            løpendeSakerIkkeFunner = løpendeSakerIkkefunnet,
        )
    }

    private fun hentSisteMai(clock: Clock): Måned {
        val åretsMai = YearMonth.of(Year.now(clock).value, Month.MAY.ordinal + 1)
        return if (YearMonth.now(clock).isBefore(åretsMai)) {
            åretsMai.minusYears(1)
        } else {
            åretsMai
        }.let { Måned.fra(it) }
    }

    /**
     * @return A list where each entry is either the `Saksnummer` (in case of a fetch failure)
     *         or the resolved `Sak` for cases with ongoing payments during the specified month.
     */
    private fun hentSakerMedLøpendeUtbetalingForMåned(
        saker: List<SakInfo>,
        måned: Måned,
    ): List<Either<Saksnummer, Sak>> {
        if (saker.isEmpty()) return emptyList()

        val utbetalingerPerSak = utbetalingRepo.hentOversendteUtbetalingerForSakIder(
            saker.map { it.sakId },
        )

        return saker.filter {
            utbetalingerPerSak[it.sakId]?.hentGjeldendeUtbetaling(måned.fraOgMed)?.fold(
                { false },
                { true },
            ) == true
        }.map { sakInfo ->
            sakService.hentSak(sakInfo.sakId).mapLeft { sakInfo.saksnummer }
        }
    }
}

data class ReguleringStatus(
    val aar: Int,
    val sisteGrunnbeløpOgSatser: SisteGrunnbeløpOgSatser,
    val sakerMedUtebetalingIMai: Int,
    val sakerMedGammelG: List<SakMedGammeltGrunnbeløp>,

    // For å debuge hvis hentSakerMedLøpendeUtbetalingForMåned ikke fungerer som forventet
    val løpendeSakerIkkeFunner: List<Saksnummer>,
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
    val garantipensjonOrdinær: Int,
    val garantipensjonHøy: Int,
)
