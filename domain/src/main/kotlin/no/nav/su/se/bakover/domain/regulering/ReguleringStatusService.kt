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
import satser.domain.Satskategori
import økonomi.domain.utbetaling.UtbetalingRepo
import økonomi.domain.utbetaling.hentGjeldendeUtbetaling
import java.time.Clock
import java.time.Year
import java.time.YearMonth
import kotlin.collections.filter
import kotlin.collections.map

class ReguleringStatusService(
    val sakService: SakService,
    val utbetalingRepo: UtbetalingRepo,
    val clock: Clock,
) {

    fun hentStatusSisteGrunnbeløp(): ReguleringStatus {
        val sisteMai = hentSisteMai()
        val sisteGrunnbeløp = 0
        val sisteSatsOrd = 0.0
        val sisteSatsHoy = 0.0

        val alleSaker = sakService.hentSakIdSaksnummerOgFnrForAlleSaker()
        val sakerMedUtbetalingMai = hentSakerMedLøpendeUtbetalingForMåned(alleSaker, sisteMai)
        val (løpendeSakerIkkefunnet, løpendeSaker) = sakerMedUtbetalingMai.split()

        val sakerMedGammeltGrunnbeløp = løpendeSaker.mapNotNull {
            val beregning = it.hentGjeldendeMånedsberegninger(Måned.fra(sisteMai), clock).singleOrNull()
                ?: throw (IllegalStateException("Forventer kun én månedsberegning per måned"))
            val benyttetG = beregning.getBenyttetGrunnbeløp()
                ?: throw (IllegalStateException("Beregning mangler grunnbeløp"))
            val kategori = beregning.getSats()
            val benyttetSats = beregning.getSatsbeløp()

            val sisteGrunnbeløpErBenyttet = benyttetG == sisteGrunnbeløp
            val sisteSatsErBenyttet = when (kategori) {
                Satskategori.ORDINÆR -> benyttetSats == sisteSatsOrd
                Satskategori.HØY -> benyttetSats == sisteSatsHoy
            }
            if (sisteGrunnbeløpErBenyttet && sisteSatsErBenyttet) {
                null
            } else {
                SakMedGammeltGrunnbeløp(
                    saksnummer = it.saksnummer,
                    type = it.type,
                    benyttetGrunnbeløp = benyttetG,
                    benyttetSatskategori = kategori,
                    benyttetSats = benyttetSats,
                )
            }
        }

        return ReguleringStatus(
            sisteGrunnbeløp = sisteGrunnbeløp,
            sisteSatsOrd = sisteSatsOrd,
            sisteSatsHoy = sisteSatsHoy,
            sakerMedUtebetalingIMai = sakerMedUtbetalingMai.size,
            sakerMedGammelG = sakerMedGammeltGrunnbeløp,
            løpendeSakerIkkeFunner = løpendeSakerIkkefunnet,
        )
    }

    private fun hentSisteMai(): YearMonth {
        val åretsMai = YearMonth.of(Year.now().value, Month.MAY.ordinal)
        return if (YearMonth.now().isBefore(åretsMai)) {
            åretsMai.minusYears(1)
        } else {
            åretsMai
        }
    }

    /**
     * @return A list where each entry is either the `Saksnummer` (in case of a fetch failure)
     *         or the resolved `Sak` for cases with ongoing payments during the specified month.
     */
    private fun hentSakerMedLøpendeUtbetalingForMåned(
        saker: List<SakInfo>,
        måned: YearMonth,
    ): List<Either<Saksnummer, Sak>> {
        if (saker.isEmpty()) return emptyList()

        val utbetalingerPerSak = utbetalingRepo.hentOversendteUtbetalingerForSakIder(
            saker.map { it.sakId },
        )

        return saker.filter {
            utbetalingerPerSak[it.sakId]
                ?.hentGjeldendeUtbetaling(måned.atDay(1))
                ?.fold(
                    { false },
                    { true },
                ) == true
        }.map { sakInfo ->
            sakService.hentSak(sakInfo.sakId).mapLeft { sakInfo.saksnummer }
        }
    }
}

data class ReguleringStatus(
    val sisteGrunnbeløp: Int,
    val sisteSatsOrd: Double,
    val sisteSatsHoy: Double,
    val sakerMedUtebetalingIMai: Int,
    val sakerMedGammelG: List<SakMedGammeltGrunnbeløp>,

    // For å debuge hvis hentSakerMedLøpendeUtbetalingForMåned ikke fungerer som forventet
    val løpendeSakerIkkeFunner: List<Saksnummer>,
)

data class SakMedGammeltGrunnbeløp(
    val saksnummer: Saksnummer,
    val type: Sakstype,
    val benyttetGrunnbeløp: Int,
    val benyttetSatskategori: Satskategori,
    val benyttetSats: Double,
)
