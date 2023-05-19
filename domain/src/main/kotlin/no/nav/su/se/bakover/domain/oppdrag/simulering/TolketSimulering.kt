package no.nav.su.se.bakover.domain.oppdrag.simulering

import no.nav.su.se.bakover.common.Beløp
import no.nav.su.se.bakover.common.MånedBeløp
import no.nav.su.se.bakover.common.Månedsbeløp
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.minAndMaxOf
import no.nav.su.se.bakover.common.tid.periode.tilMåned
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.Integer.max
import java.time.LocalDate

internal data class TolketSimulering(
    private val simulering: Simulering,
) {
    private val tolketPerioder = simulering.periodeList.map { it.tolk() }.also {
        require(it.isNotEmpty()) { "Skal alltid være minst 1 periode" }
    }

    private fun hentPerioderMedUtbetaling(): List<TolketPeriodeMedUtbetalinger> {
        return tolketPerioder.mapNotNull {
            when (it) {
                is TolketPeriodeMedUtbetalinger -> it
                is TolketPeriodeUtenUtbetalinger -> null
            }
        }
    }

    val periode = tolketPerioder.map {
        when (it) {
            is TolketPeriodeMedUtbetalinger -> it.måned
            is TolketPeriodeUtenUtbetalinger -> it.periode
        }
    }.minAndMaxOf()

    val månederMedSimuleringsresultat = hentPerioderMedUtbetaling().map { it.måned }

    fun harFeilutbetalinger() = hentFeilutbetalteBeløp().sum() > 0

    fun harFeilutbetalinger(periode: Periode): Boolean {
        return hentFeilutbetalteBeløp().filter { periode.overlapper(it.periode) }.sumOf { it.beløp.sum() } > 0
    }

    /**
     * Sjekk for spesialtilfelle hvor vi har mottatt tom respons (indikerer ingen posteringer/utbetalinger).
     * Her vil [no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringResponseMapper] lage en "fiktiv" periode
     * satt til simuleringsperioden some er brukt, men denne vil ikke inneholde noen utbetalinger.
     * OBS - i motsetning til perioder med utbetalinger vil denne perioden kunne være lenger enn 1 mnd.
     */
    fun erAlleMånederUtenUtbetaling(): Boolean {
        return tolketPerioder.all { it is TolketPeriodeUtenUtbetalinger }
    }
    fun kontooppstilling(): Map<Periode, Kontooppstilling> {
        return if (erAlleMånederUtenUtbetaling()) {
            mapOf(
                periode to Kontooppstilling(
                    debetYtelse = Kontobeløp.Debet(0),
                    kreditYtelse = Kontobeløp.Kredit(0),
                    debetFeilkonto = Kontobeløp.Debet(0),
                    kreditFeilkonto = Kontobeløp.Kredit(0),
                    debetMotpostFeilkonto = Kontobeløp.Debet(0),
                    kreditMotpostFeilkonto = Kontobeløp.Kredit(0),
                ),
            )
        } else {
            hentPerioderMedUtbetaling().associate { it.kontooppstilling() }
        }
    }

    fun hentUtbetalteBeløp(): Månedsbeløp {
        return if (erAlleMånederUtenUtbetaling()) {
            Månedsbeløp(emptyList())
        } else {
            return Månedsbeløp(
                hentPerioderMedUtbetaling()
                    .map { it.hentUtbetaltBeløp() }
                    .filter { it.sum() > 0 },
            )
        }
    }

    fun hentFeilutbetalteBeløp(): Månedsbeløp {
        return if (erAlleMånederUtenUtbetaling()) {
            Månedsbeløp(emptyList())
        } else {
            Månedsbeløp(
                hentPerioderMedUtbetaling()
                    .map { it.hentFeilutbetalteBeløp() }
                    .filter { it.sum() > 0 },
            )
        }
    }

    fun hentTilUtbetaling(): Månedsbeløp {
        return if (erAlleMånederUtenUtbetaling()) {
            Månedsbeløp(emptyList())
        } else {
            Månedsbeløp(
                hentPerioderMedUtbetaling()
                    .map { it.hentTilUtbetaling() }
                    .filter { it.sum() > 0 },
            )
        }
    }

    fun hentTotalUtbetaling(): Månedsbeløp {
        return if (erAlleMånederUtenUtbetaling()) {
            Månedsbeløp(emptyList())
        } else {
            Månedsbeløp(
                hentPerioderMedUtbetaling()
                    .map { it.hentTotalUtbetaling() }
                    .filter { it.sum() > 0 },
            )
        }
    }

    fun totalOppsummering(): PeriodeOppsummering {
        return periodeOppsummering().reduce { acc, periodeOppsummering ->
            acc.copy(
                periode = periode,
                sumTilUtbetaling = acc.sumTilUtbetaling + periodeOppsummering.sumTilUtbetaling,
                sumEtterbetaling = acc.sumEtterbetaling + periodeOppsummering.sumEtterbetaling,
                sumFramtidigUtbetaling = acc.sumFramtidigUtbetaling + periodeOppsummering.sumFramtidigUtbetaling,
                sumTotalUtbetaling = acc.sumTotalUtbetaling + periodeOppsummering.sumTotalUtbetaling,
                sumTidligereUtbetalt = acc.sumTidligereUtbetalt + periodeOppsummering.sumTidligereUtbetalt,
                sumFeilutbetaling = acc.sumFeilutbetaling + periodeOppsummering.sumFeilutbetaling,
                sumReduksjonFeilkonto = acc.sumReduksjonFeilkonto + periodeOppsummering.sumReduksjonFeilkonto,
            )
        }
    }

    fun periodeOppsummering(): List<PeriodeOppsummering> {
        return if (erAlleMånederUtenUtbetaling()) {
            listOf(
                PeriodeOppsummering(
                    periode = periode,
                    sumTilUtbetaling = 0,
                    sumEtterbetaling = 0,
                    sumFramtidigUtbetaling = 0,
                    sumTotalUtbetaling = 0,
                    sumTidligereUtbetalt = 0,
                    sumFeilutbetaling = 0,
                    sumReduksjonFeilkonto = 0,
                ),
            )
        } else {
            hentPerioderMedUtbetaling().map { it.oppsummering() }
        }
    }
}
internal sealed interface TolketPeriode

internal data class TolketPeriodeUtenUtbetalinger(
    val periode: Periode,
) : TolketPeriode
internal data class TolketPeriodeMedUtbetalinger(
    val måned: Måned,
    val utbetaling: TolketUtbetaling,
) : TolketPeriode {
    fun kontooppstilling(): Pair<Måned, Kontooppstilling> {
        return måned.tilMåned() to utbetaling.kontoppstilling
    }
    fun harFeilutbetalinger() = hentFeilutbetalteBeløp().sum() > 0
    fun hentUtbetaltBeløp(): MånedBeløp {
        return MånedBeløp(måned.tilMåned(), Beløp(utbetaling.kontoppstilling.kreditYtelse.sum()))
    }

    fun hentFeilutbetalteBeløp(): MånedBeløp {
        return MånedBeløp(måned.tilMåned(), Beløp(utbetaling.kontoppstilling.debetFeilkonto.sum()))
    }

    fun hentReduksjonFeilkonto(): MånedBeløp {
        return MånedBeløp(måned.tilMåned(), Beløp(utbetaling.kontoppstilling.kreditFeilkonto.sum()))
    }

    fun hentTilUtbetaling(): MånedBeløp {
        return MånedBeløp(måned.tilMåned(), Beløp(max(utbetaling.kontoppstilling.sumUtbetaling.sum(), 0)))
    }

    fun hentTotalUtbetaling(): MånedBeløp {
        return MånedBeløp(måned.tilMåned(), Beløp(utbetaling.kontoppstilling.debetYtelse.sum() - utbetaling.kontoppstilling.debetFeilkonto.sum()))
    }

    fun hentEtterbetaling(): MånedBeløp {
        return if (måned.erForfalt()) {
            hentTilUtbetaling()
        } else {
            MånedBeløp(måned.tilMåned(), Beløp.zero())
        }
    }

    fun hentFramtidigUtbetaling(): MånedBeløp {
        return if (!måned.erForfalt()) {
            hentTilUtbetaling()
        } else {
            MånedBeløp(måned.tilMåned(), Beløp.zero())
        }
    }

    fun oppsummering(): PeriodeOppsummering {
        return PeriodeOppsummering(
            periode = måned,
            sumTilUtbetaling = Månedsbeløp(månedbeløp = listOf(hentTilUtbetaling())).sum(),
            sumEtterbetaling = Månedsbeløp(månedbeløp = listOf(hentEtterbetaling())).sum(),
            sumFramtidigUtbetaling = Månedsbeløp(månedbeløp = listOf(hentFramtidigUtbetaling())).sum(),
            sumTotalUtbetaling = Månedsbeløp(månedbeløp = listOf(hentTotalUtbetaling())).sum(),
            sumTidligereUtbetalt = Månedsbeløp(månedbeløp = listOf(hentUtbetaltBeløp())).sum(),
            sumFeilutbetaling = Månedsbeløp(månedbeløp = listOf(hentFeilutbetalteBeløp())).sum(),
            sumReduksjonFeilkonto = Månedsbeløp(månedbeløp = listOf(hentReduksjonFeilkonto())).sum(),
        )
    }

    private fun Måned.erForfalt(): Boolean {
        return tilOgMed < utbetaling.forfall
    }
}

internal data class TolketUtbetaling(
    private val detaljer: List<TolketDetalj>,
    val forfall: LocalDate,
) {
    private val feilkonto: List<TolketDetalj.Feilkonto> = detaljer.filterIsInstance<TolketDetalj.Feilkonto>()
    private val motpostFeilkonto: List<TolketDetalj.MotpostFeilkonto> = detaljer.filterIsInstance<TolketDetalj.MotpostFeilkonto>()
    private val ytelse: List<TolketDetalj.Ytelse> = detaljer.filterIsInstance<TolketDetalj.Ytelse>()

    val kontoppstilling = Kontooppstilling(
        debetYtelse = hentDebetYtelse(),
        kreditYtelse = hentKreditYtelse(),
        debetFeilkonto = hentDebetFeilkonto(),
        kreditFeilkonto = hentKreditFeilkonto(),
        debetMotpostFeilkonto = hentDebetMotpostFeilkonto(),
        kreditMotpostFeilkonto = hentKreditMotpostFeilkonto(),
    )

    private fun hentDebetYtelse(): Kontobeløp.Debet {
        return Kontobeløp.Debet(ytelse.filter { it.erDebet() }.sumOf { it.sum() })
    }

    private fun hentKreditYtelse(): Kontobeløp.Kredit {
        return Kontobeløp.Kredit(ytelse.filter { it.erKredit() }.sumOf { it.sum() })
    }

    private fun hentUtbetalingSomSimuleres(): Kontobeløp.Debet {
        return Kontobeløp.Debet(ytelse.filter { it.erUtbetalingSomSimuleres && it.erDebet() }.sumOf { it.sum() })
    }

    private fun hentDebetFeilkonto(): Kontobeløp.Debet {
        return Kontobeløp.Debet(feilkonto.filter { it.erDebet() }.sumOf { it.sum() })
    }

    private fun hentKreditFeilkonto(): Kontobeløp.Kredit {
        return Kontobeløp.Kredit(feilkonto.filter { it.erKredit() }.sumOf { it.sum() })
    }

    private fun hentDebetMotpostFeilkonto(): Kontobeløp.Debet {
        return Kontobeløp.Debet(motpostFeilkonto.filter { it.erDebet() }.sumOf { it.sum() })
    }

    private fun hentKreditMotpostFeilkonto(): Kontobeløp.Kredit {
        return Kontobeløp.Kredit(motpostFeilkonto.filter { it.erKredit() }.sumOf { it.sum() })
    }
}

sealed class TolketDetalj {
    abstract val beløp: Kontobeløp
    fun erDebet() = beløp is Kontobeløp.Debet
    fun erKredit() = beløp is Kontobeløp.Kredit
    fun sum() = beløp.sum()

    companion object {
        private fun SimulertDetaljer.erUtbetalingSomSimuleres(): Boolean {
            return erYtelse() &&
                sats >= 0 &&
                belop == sats &&
                typeSats == "MND" &&
                antallSats == 1 &&
                !tilbakeforing
        }
        fun from(
            simulertDetaljer: SimulertDetaljer,
            log: Logger = LoggerFactory.getLogger(this::class.java),
        ) = when {
            simulertDetaljer.erFeilkonto() -> {
                Feilkonto(beløp = Kontobeløp(simulertDetaljer.belop))
            }
            simulertDetaljer.erYtelse() -> {
                Ytelse(beløp = Kontobeløp(simulertDetaljer.belop), erUtbetalingSomSimuleres = simulertDetaljer.erUtbetalingSomSimuleres())
            }
            simulertDetaljer.erMotpostFeilkonto() -> {
                MotpostFeilkonto(beløp = Kontobeløp(simulertDetaljer.belop))
            }
            simulertDetaljer.erSkatt() -> {
                Skatt(beløp = Kontobeløp(simulertDetaljer.belop))
            }
            else -> {
                log.error("Ukjent detalj: $simulertDetaljer")
                null
            }
        }

        private fun SimulertDetaljer.erFeilkonto() = klasseType == KlasseType.FEIL

        private fun SimulertDetaljer.erYtelse() = klasseType == KlasseType.YTEL
        private fun SimulertDetaljer.erMotpostFeilkonto() = klasseType == KlasseType.MOTP
        private fun SimulertDetaljer.erSkatt() = klasseType == KlasseType.SKAT
    }

    data class Feilkonto(
        override val beløp: Kontobeløp,
    ) : TolketDetalj()

    data class Ytelse(
        override val beløp: Kontobeløp,
        val erUtbetalingSomSimuleres: Boolean,
    ) : TolketDetalj()

    data class MotpostFeilkonto(
        override val beløp: Kontobeløp,
    ) : TolketDetalj()

    data class Skatt(
        override val beløp: Kontobeløp,
    ) : TolketDetalj()
}
