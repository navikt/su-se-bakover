package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import satser.domain.SatsFactory
import satser.domain.Satskategori
import økonomi.domain.utbetaling.UtbetalingRepo
import økonomi.domain.utbetaling.UtbetalingslinjePåTidslinje
import økonomi.domain.utbetaling.hentGjeldendeUtbetaling
import java.time.Clock
import java.time.YearMonth
import kotlin.collections.filter
import kotlin.collections.ifEmpty
import kotlin.collections.map

class ReguleringStatusUteståendeService(
    private val sakService: SakService,
    private val utbetalingRepo: UtbetalingRepo,
    private val vedtakRepo: VedtakRepo,
    private val satsFactory: SatsFactory,
    private val clock: Clock,
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

        val sakerMedGammeltGrunnbeløp = sakerMedUtbetalingOgStansMai.mapNotNull { sakInfo ->
            val (sakId, saksnummer, _, type) = sakInfo
            val vedtakSomKanRevurderes = vedtakRepo.hentVedtakSomKanRevurderesForSak(sakId)
            /*
            val vedtaksdata =
                hentGjeldendeVedtaksdataForRegulering(
                    etterspurtMai,
                    sakInfo,
                    vedtakSomKanRevurderes,
                    clock,
                ).getOrElse {
                    throw IllegalStateException("Klarte ikke å hente vedtaksdata for løpende sak saksnummer=$saksnummer")
                }

            // TODO single gir flere elementer exception, first gir no elemnts exception
            // Førstenevnte er rikig at kan oppstå? Sistnevnte bør jo ikke det?
            val beregning = vedtaksdata.hentMånedsberegning(etterspurtMai).first()
             */

            val beregning = GjeldendeVedtaksdata(
                periode = etterspurtMai,
                vedtakListe = vedtakSomKanRevurderes
                    .filter { it.beregning != null }.ifEmpty { emptyList() }.toNonEmptyList(),
                clock = clock,
            ).hentMånedsberegning(etterspurtMai)
                .first() // TODO ??

            // TODO Hva med invilget fra og med 1 juni, men behandlet i mai før ny G?
            // Bør istedenfor first, loope gjennom alle???

            val benyttetG = beregning.getBenyttetGrunnbeløp()
            val kategori = beregning.getSats()
            val benyttetSats = beregning.fullSupplerendeStønadForMåned.sats.sats.toDouble()

            if (beregning.erRegulertMedNyttGrunnbeløp(type, sisteBeløper)) {
                null
            } else {
                SakMedGammeltGrunnbeløp(
                    saksnummer = saksnummer,
                    type = type,
                    benyttetGrunnbeløp = benyttetG,
                    benyttetSatskategori = kategori,
                    benyttetSats = benyttetSats,
                )
            }
        }

        return ReguleringStatus(
            aar = etterspurtMai.fraOgMed.year,
            sisteGrunnbeløpOgSatser = sisteBeløper,
            sakerMedUtebetalingIMai = sakerMedUtbetalingOgStansMai.size,
            sakerMedGammelG = sakerMedGammeltGrunnbeløp,
        )
    }

    private fun hentSakerMedLøpendeUtbetalingEllerStansForMåned(
        saker: List<SakInfo>,
        måned: Måned,
    ): List<SakInfo> {
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
        }
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
    val garantipensjonOrdinær: Int,
    val garantipensjonHøy: Int,
)
