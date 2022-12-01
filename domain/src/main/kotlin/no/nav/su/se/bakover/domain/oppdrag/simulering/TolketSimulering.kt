package no.nav.su.se.bakover.domain.oppdrag.simulering

import no.nav.su.se.bakover.common.application.Beløp
import no.nav.su.se.bakover.common.application.MånedBeløp
import no.nav.su.se.bakover.common.application.Månedsbeløp
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.periode.Måned
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.minAndMaxOf
import no.nav.su.se.bakover.common.periode.tilMåned
import java.lang.Integer.max
import java.time.LocalDate

internal data class TolketSimulering(
    private val simulering: Simulering,
) {
    private val tolketPerioder = simulering.periodeList.map { it.tolk() }.also {
        require(it.isNotEmpty()) { "Skal alltid være minst 1 periode" }
    }

    private fun hentPerioderMedUtbetaling(): List<TolketPeriodeMedUtbetalinger> {
        require(!erSimuleringUtenUtbetalinger()) { "Skal bare kalles hvis simulering inneholder utbetalinger" }
        return tolketPerioder.map {
            when (it) {
                is TolketPeriodeMedUtbetalinger -> it
                is TolketPeriodeUtenUtbetalinger -> throw IllegalStateException("Skal bare kalles hvis simulering inneholder utbetalinger")
            }
        }
    }

    val periode = tolketPerioder.map {
        when (it) {
            is TolketPeriodeMedUtbetalinger -> it.måned
            is TolketPeriodeUtenUtbetalinger -> it.periode
        }
    }.minAndMaxOf()

    fun harFeilutbetalinger() = hentFeilutbetalteBeløp().sum() > 0

    /**
     * Sjekk for spesialtilfelle hvor vi har mottatt tom respons (indikerer ingen posteringer/utbetalinger).
     * Her vil [no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringResponseMapper] lage en "fiktiv" periode
     * satt til simuleringsperioden some er brukt, men denne vil ikke inneholde noen utbetalinger.
     * OBS - i motsetning til perioder med utbetalinger vil denne perioden kunne være lenger enn 1 mnd.
     */
    fun erSimuleringUtenUtbetalinger(): Boolean {
        return tolketPerioder.count() == 1 && tolketPerioder.all { it is TolketPeriodeUtenUtbetalinger }
    }
    fun kontooppstilling(): Map<Periode, Kontooppstilling> {
        return if (erSimuleringUtenUtbetalinger()) {
            mapOf(
                periode to Kontooppstilling(
                    simulertUtbetaling = Kontobeløp.Debet(0),
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
        return if (erSimuleringUtenUtbetalinger()) {
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
        return if (erSimuleringUtenUtbetalinger()) {
            Månedsbeløp(emptyList())
        } else {
            Månedsbeløp(
                hentPerioderMedUtbetaling()
                    .map { it.hentFeilutbetalteBeløp() }
                    .filter { it.sum() > 0 },
            )
        }
    }

    fun hentUtbetalingSomSimuleres(): Månedsbeløp {
        return if (erSimuleringUtenUtbetalinger()) {
            Månedsbeløp(emptyList())
        } else {
            Månedsbeløp(
                hentPerioderMedUtbetaling()
                    .map { it.hentUtbetalingSomSimuleres() }
                    .filter { it.sum() > 0 },
            )
        }
    }

    fun hentTilUtbetaling(): Månedsbeløp {
        return if (erSimuleringUtenUtbetalinger()) {
            Månedsbeløp(emptyList())
        } else {
            Månedsbeløp(
                hentPerioderMedUtbetaling()
                    .map { it.hentTilUtbetaling() }
                    .filter { it.sum() > 0 },
            )
        }
    }

    fun hentDebetYtelse(): Månedsbeløp {
        return if (erSimuleringUtenUtbetalinger()) {
            Månedsbeløp(emptyList())
        } else {
            Månedsbeløp(
                hentPerioderMedUtbetaling()
                    .map { it.hentDebetYtelse() }
                    .filter { it.sum() > 0 },
            )
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

    fun hentUtbetalingSomSimuleres(): MånedBeløp {
        return MånedBeløp(måned.tilMåned(), Beløp(utbetaling.kontoppstilling.simulertUtbetaling.sum()))
    }

    fun hentTilUtbetaling(): MånedBeløp {
        return MånedBeløp(måned.tilMåned(), Beløp(max(utbetaling.kontoppstilling.sumUtbetaling.sum(), 0)))
    }

    fun hentDebetYtelse(): MånedBeløp {
        return MånedBeløp(måned.tilMåned(), Beløp(utbetaling.kontoppstilling.debetYtelse.sum()))
    }
}

internal data class TolketUtbetaling(
    private val detaljer: List<TolketDetalj>,
) {
    private val feilkonto: List<TolketDetalj.Feilkonto> = detaljer.filterIsInstance<TolketDetalj.Feilkonto>()
    private val motpostFeilkonto: List<TolketDetalj.MotpostFeilkonto> = detaljer.filterIsInstance<TolketDetalj.MotpostFeilkonto>()
    private val ytelse: List<TolketDetalj.Ytelse> = detaljer.filterIsInstance<TolketDetalj.Ytelse>()

    val kontoppstilling = Kontooppstilling(
        debetYtelse = hentDebetYtelse(),
        simulertUtbetaling = hentUtbetalingSomSimuleres(),
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

    private fun hentTilUtbetaling(): Kontobeløp.Summert {
        return Kontobeløp.Summert(max(Kontobeløp.Summert(hentUtbetalingSomSimuleres(), hentKreditYtelse()).sum(), 0))
    }

    fun erTomSimulering(): Boolean {
        return detaljer.isEmpty()
    }

    private fun forfallsDato(): LocalDate {
        return ytelse.first().forfall
    }
}

sealed class TolketDetalj {
    abstract val beløp: Kontobeløp
    abstract val forfall: LocalDate
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
        fun from(simulertDetaljer: SimulertDetaljer, forfall: LocalDate) = when {
            simulertDetaljer.erFeilkonto() -> {
                Feilkonto(beløp = Kontobeløp(simulertDetaljer.belop), forfall = forfall)
            }
            simulertDetaljer.erYtelse() -> {
                Ytelse(beløp = Kontobeløp(simulertDetaljer.belop), forfall = forfall, erUtbetalingSomSimuleres = simulertDetaljer.erUtbetalingSomSimuleres())
            }
            simulertDetaljer.erMotpostFeilkonto() -> {
                MotpostFeilkonto(beløp = Kontobeløp(simulertDetaljer.belop), forfall = forfall)
            }
            simulertDetaljer.erSkatt() -> {
                Skatt(beløp = Kontobeløp(simulertDetaljer.belop), forfall = forfall)
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
        override val forfall: LocalDate,
    ) : TolketDetalj()

    data class Ytelse(
        override val beløp: Kontobeløp,
        override val forfall: LocalDate,
        val erUtbetalingSomSimuleres: Boolean,
    ) : TolketDetalj()

    data class MotpostFeilkonto(
        override val beløp: Kontobeløp,
        override val forfall: LocalDate,
    ) : TolketDetalj()

    data class Skatt(
        override val beløp: Kontobeløp,
        override val forfall: LocalDate,
    ) : TolketDetalj()
}
