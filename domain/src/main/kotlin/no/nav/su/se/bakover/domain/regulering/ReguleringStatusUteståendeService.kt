package no.nav.su.se.bakover.domain.regulering

import arrow.core.Either
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
import økonomi.domain.utbetaling.UtbetalingslinjePåTidslinje
import økonomi.domain.utbetaling.hentGjeldendeUtbetaling
import java.time.Clock
import java.time.YearMonth
import kotlin.collections.filter
import kotlin.collections.map

class ReguleringStatusUteståendeService(
    val sakService: SakService,
    val utbetalingRepo: UtbetalingRepo,
    val satsFactory: SatsFactory,
    val clock: Clock,
) {

    fun hentStatusSisteGrunnbeløp(aar: Int): ReguleringStatus {
        val etterspurtMai = Måned.fra(YearMonth.of(aar, 5))
        val sisteBeløper = SisteGrunnbeløpOgSatser(
            grunnbeløp = satsFactory.grunnbeløp(etterspurtMai).grunnbeløpPerÅr,
            garantipensjonOrdinær = satsFactory.ordinærAlder(etterspurtMai).garantipensjonForMåned.garantipensjonPerÅr,
            garantipensjonHøy = satsFactory.høyAlder(etterspurtMai).garantipensjonForMåned.garantipensjonPerÅr,
        )

        val alleSaker = sakService.hentSakIdSaksnummerOgFnrForAlleSakerNyesteFørst()
        val sakerMedUtbetalingOgStansMai = hentSakerMedLøpendeUtbetalingEllerStansForMåned(alleSaker, etterspurtMai)
        val (løpendeSakerIkkefunnet, løpendeOgMidlertidigStansSaker) = sakerMedUtbetalingOgStansMai.split()

        val sakerMedGammeltGrunnbeløp = løpendeOgMidlertidigStansSaker.mapNotNull {
            val beregning = it.hentGjeldendeMånedsberegninger(etterspurtMai, clock).singleOrNull()
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
            aar = etterspurtMai.fraOgMed.year,
            sisteGrunnbeløpOgSatser = sisteBeløper,
            sakerMedUtebetalingIMai = sakerMedUtbetalingOgStansMai.size,
            sakerMedGammelG = sakerMedGammeltGrunnbeløp,
            løpendeSakerIkkeFunner = løpendeSakerIkkefunnet,
        )
    }

    private fun hentSakerMedLøpendeUtbetalingEllerStansForMåned(
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
                {
                    when (it) {
                        is UtbetalingslinjePåTidslinje.Reaktivering,
                        is UtbetalingslinjePåTidslinje.Ny,
                        is UtbetalingslinjePåTidslinje.Stans,
                        -> true

                        is UtbetalingslinjePåTidslinje.Opphør -> false
                    }
                },
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
