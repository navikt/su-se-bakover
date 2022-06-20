package no.nav.su.se.bakover.domain.oppdrag.simulering

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Beløp
import no.nav.su.se.bakover.domain.MånedBeløp
import no.nav.su.se.bakover.domain.Månedsbeløp
import java.time.LocalDate

data class TolketSimulering(
    private val simulering: Simulering,
) {
    val simulertePerioder = simulering.periodeList.map {
        val utbetalinger = when (it.utbetaling.isNotEmpty()) {
            true -> {
                it.utbetaling.map { simulertUtbetaling ->
                    val detaljer = simulertUtbetaling.detaljer.mapNotNull { simulertDetalj ->
                        TolketDetalj.from(simulertDetalj, simulertUtbetaling.forfall)
                    }
                    TolketUtbetaling.from(detaljer)
                }
            }
            false -> {
                listOf(TolketUtbetaling.IngenUtbetaling())
            }
        }

        TolketPeriode(
            periode = Periode.create(it.fraOgMed, it.tilOgMed),
            utbetalinger = utbetalinger,
        )
    }

    fun harFeilutbetalinger() = simulertePerioder.any { it.harFeilutbetalinger() }

    fun periode(): Periode {
        return simulertePerioder.let {
            Periode.create(
                it.minOf { it.periode.fraOgMed },
                it.maxOf { it.periode.tilOgMed },
            )
        }
    }

    /**
     * Sjekk for spesialtilfellet hvor vi har mottatt en tom respons.
     * @see no.nav.su.se.bakover.client.oppdrag.simulering.SimuleringResponseMapper
     */
    private fun erTomSimulering(): Boolean {
        return simulertePerioder.all { it.utbetalinger.all { it is TolketUtbetaling.IngenUtbetaling } }
    }

    /**
     * Identifiser eventuelle utbetalte beløp per periode.
     * Inkluderer kun beløp som er større enn 0.
     */
    fun hentUtbetalteBeløp(periode: Periode = periode()): Månedsbeløp {
        return if (erTomSimulering()) {
            Månedsbeløp(emptyList())
        } else {
            return Månedsbeløp(
                simulertePerioder
                    .filter { periode inneholder it.periode }
                    .map { it.hentUtbetaltBeløp() }
                    .filter { it.sum() > 0 },
            )
        }
    }

    fun hentFeilutbetalteBeløp(periode: Periode = periode()): Månedsbeløp {
        return if (erTomSimulering()) {
            Månedsbeløp(emptyList())
        } else {
            Månedsbeløp(
                simulertePerioder
                    .filter { periode inneholder it.periode }
                    .map { it.hentFeilutbetalteBeløp() }
                    .filter { it.sum() > 0 },
            )
        }
    }

    fun hentØnsketUtbetaling(periode: Periode = periode()): Månedsbeløp {
        return if (erTomSimulering()) {
            Månedsbeløp(emptyList())
        } else {
            Månedsbeløp(
                simulertePerioder
                    .filter { periode inneholder it.periode }
                    .map { it.hentØnsketUtbetaling() }
                    .filter { it.sum() > 0 },
            )
        }
    }
}

data class TolketPeriode(
    val periode: Periode,
    val utbetalinger: List<TolketUtbetaling>,
) {
    fun harFeilutbetalinger() = utbetalinger.any { it is TolketUtbetaling.Feilutbetaling }
    fun hentUtbetaltBeløp(): MånedBeløp {
        return MånedBeløp(periode, Beløp(utbetalinger.sumOf { it.hentUtbetaltBeløp().sum() }))
    }

    fun hentFeilutbetalteBeløp(): MånedBeløp {
        return MånedBeløp(periode, Beløp(utbetalinger.sumOf { it.hentFeilutbetaltBeløp().sum() }))
    }

    fun hentØnsketUtbetaling(): MånedBeløp {
        return MånedBeløp(periode, Beløp(utbetalinger.sumOf { it.hentØnsketUtbetaling().sum() }))
    }
}

sealed class TolketUtbetaling {
    abstract val tolketDetalj: List<TolketDetalj>

    abstract fun bruttobeløp(): Int

    fun hentUtbetaltBeløp(): Beløp {
        return Beløp(tolketDetalj.filterIsInstance<TolketDetalj.TidligereUtbetalt>().sumOf { it.beløp })
    }

    fun hentFeilutbetaltBeløp(): Beløp {
        return Beløp(tolketDetalj.filterIsInstance<TolketDetalj.Feilutbetaling>().sumOf { it.beløp })
    }

    fun hentØnsketUtbetaling(): Beløp {
        return Beløp(tolketDetalj.filterIsInstance<TolketDetalj.Ordinær>().sumOf { it.beløp })
    }

    companion object {
        fun from(tolketDetaljer: List<TolketDetalj>) = when {
            tolketDetaljer.erFeilutbetaling() -> {
                Feilutbetaling(tolketDetaljer)
            }
            tolketDetaljer.erTidligereUtbetalt() -> {
                val sum = tolketDetaljer.sumOf { it.beløp }
                when {
                    sum > 0 -> Etterbetaling(tolketDetaljer + TolketDetalj.Etterbetaling(sum))
                    sum == 0 -> UendretUtbetaling(
                        tolketDetaljer + TolketDetalj.UendretUtbetaling(
                            tolketDetaljer.filterIsInstance<TolketDetalj.Ordinær>().sumOf { it.beløp },
                        ),
                    )
                    else -> throw IndikererFeilutbetaling
                }
            }
            tolketDetaljer.erOrdinær() -> {
                val tolketDetalj = tolketDetaljer.first() as TolketDetalj.Ordinær
                if (tolketDetalj.fraOgMed < tolketDetalj.forfall.withDayOfMonth(1)) {
                    Etterbetaling(tolketDetaljer + TolketDetalj.Etterbetaling(tolketDetalj.beløp))
                } else {
                    Ordinær(tolketDetaljer)
                }
            }
            else -> throw IngenEntydigTolkning
        }

        private fun List<TolketDetalj>.erFeilutbetaling() =
            any { it is TolketDetalj.Feilutbetaling } &&
                any { it is TolketDetalj.TidligereUtbetalt }

        private fun List<TolketDetalj>.erTidligereUtbetalt() =
            count() == 2 &&
                any { it is TolketDetalj.Ordinær } &&
                any { it is TolketDetalj.TidligereUtbetalt }

        private fun List<TolketDetalj>.erOrdinær() =
            count() == 1 &&
                all { it is TolketDetalj.Ordinær }
    }

    data class Ordinær(
        override val tolketDetalj: List<TolketDetalj>,
    ) : TolketUtbetaling() {
        override fun bruttobeløp(): Int =
            tolketDetalj.filterIsInstance<TolketDetalj.Ordinær>()
                .sumOf { it.beløp }
    }

    data class Feilutbetaling(
        override val tolketDetalj: List<TolketDetalj>,
    ) : TolketUtbetaling() {
        override fun bruttobeløp(): Int =
            tolketDetalj.filterIsInstance<TolketDetalj.Feilutbetaling>()
                .sumOf { it.beløp }
                .times(-1)
    }

    data class Etterbetaling(
        override val tolketDetalj: List<TolketDetalj>,
    ) : TolketUtbetaling() {
        override fun bruttobeløp(): Int =
            tolketDetalj.filterIsInstance<TolketDetalj.Etterbetaling>()
                .sumOf { it.beløp }
    }

    data class UendretUtbetaling(
        override val tolketDetalj: List<TolketDetalj>,
    ) : TolketUtbetaling() {
        override fun bruttobeløp(): Int =
            tolketDetalj.filterIsInstance<TolketDetalj.UendretUtbetaling>()
                .sumOf { it.beløp }
    }

    data class IngenUtbetaling(
        override val tolketDetalj: List<TolketDetalj> = listOf(TolketDetalj.IngenUtbetaling()),
    ) : TolketUtbetaling() {
        override fun bruttobeløp(): Int =
            tolketDetalj.filterIsInstance<TolketDetalj.IngenUtbetaling>()
                .sumOf { it.beløp }
    }

    object IngenEntydigTolkning : IllegalStateException("Simulert utbetaling kunne ikke tolkes entydig.")
    object IndikererFeilutbetaling :
        IllegalStateException("Indikasjon på feilutbetaling, men detaljer for feilutbetaling mangler.")
}

sealed class TolketDetalj {
    abstract val beløp: Int

    companion object {
        fun from(simulertDetaljer: SimulertDetaljer, forfall: LocalDate) = when {
            simulertDetaljer.erFeilutbetaling() -> {
                Feilutbetaling(beløp = simulertDetaljer.belop)
            }
            simulertDetaljer.erTidligereUtbetalt() -> {
                TidligereUtbetalt(beløp = simulertDetaljer.belop)
            }
            simulertDetaljer.erØnsketUtbetalt() -> {
                Ordinær(beløp = simulertDetaljer.belop, forfall, simulertDetaljer.faktiskFraOgMed)
            }
            else -> null
        }

        private fun SimulertDetaljer.erFeilutbetaling() = klasseType == KlasseType.FEIL

        private fun SimulertDetaljer.erTidligereUtbetalt() = klasseType == KlasseType.YTEL &&
            (klassekode == KlasseKode.SUUFORE || klassekode == KlasseKode.SUALDER) &&
            typeSats == "" &&
            antallSats == 0 &&
            tilbakeforing &&
            sats == 0 &&
            belop < 0

        private fun SimulertDetaljer.erØnsketUtbetalt() = klasseType == KlasseType.YTEL &&
            (klassekode == KlasseKode.SUUFORE || klassekode == KlasseKode.SUALDER) &&
            typeSats == "MND" &&
            antallSats == 1 &&
            sats >= 0 &&
            belop >= 0
    }

    data class Etterbetaling(
        override val beløp: Int,
    ) : TolketDetalj()

    data class Feilutbetaling(
        override val beløp: Int,
    ) : TolketDetalj()

    data class Ordinær(
        override val beløp: Int,
        val forfall: LocalDate,
        val fraOgMed: LocalDate,
    ) : TolketDetalj()

    data class TidligereUtbetalt(
        override val beløp: Int,
    ) : TolketDetalj()

    data class UendretUtbetaling(
        override val beløp: Int,
    ) : TolketDetalj()

    data class IngenUtbetaling(
        override val beløp: Int = 0,
    ) : TolketDetalj()
}
