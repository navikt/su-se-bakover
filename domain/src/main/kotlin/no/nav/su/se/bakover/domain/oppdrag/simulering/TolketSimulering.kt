package no.nav.su.se.bakover.domain.oppdrag.simulering

import arrow.core.extensions.list.foldable.exists
import java.time.LocalDate

data class TolketSimulering(
    private val simulering: Simulering,
) {
    val simulertePerioder = simulering.periodeList.map {
        val utbetalinger = when (it.utbetaling.isNotEmpty()) {
            true -> {
                it.utbetaling.map { simulertUtbetaling ->
                    val detaljer = simulertUtbetaling.detaljer.map { simulertDetalj ->
                        TolketDetalj.from(simulertDetalj, simulertUtbetaling.forfall)
                    }.filterNotNull()
                    TolketUtbetaling.from(detaljer)
                }
            }
            false -> {
                listOf(TolketUtbetaling.IngenUtbetaling())
            }
        }

        TolketPeriode(
            it.fraOgMed,
            it.tilOgMed,
            utbetalinger,
        )
    }
}

data class TolketPeriode(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val utbetalinger: List<TolketUtbetaling>,
) {
    fun harFeilutbetalinger() = utbetalinger.any { it is TolketUtbetaling.Feilutbetaling }
}

sealed class TolketUtbetaling {
    abstract val tolketDetalj: List<TolketDetalj>

    abstract fun bruttobeløp(): Int

    companion object {
        fun from(tolketDetaljer: List<TolketDetalj>) = when {
            tolketDetaljer.erFeilutbetaling() -> {
                Feilutbetaling(tolketDetaljer)
            }
            tolketDetaljer.erTidligereUtbetalt() -> {
                val sum = tolketDetaljer.sumOf { it.beløp }
                when {
                    sum > 0 -> Etterbetaling(tolketDetaljer + TolketDetalj.Etterbetaling(sum))
                    sum == 0 -> UendretUtbetaling(tolketDetaljer + TolketDetalj.UendretUtbetaling(sum))
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
                exists { it is TolketDetalj.TidligereUtbetalt } &&
                exists { it is TolketDetalj.Ordinær }

        private fun List<TolketDetalj>.erTidligereUtbetalt() =
            count() == 2 &&
                exists { it is TolketDetalj.Ordinær } &&
                exists { it is TolketDetalj.TidligereUtbetalt }

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
    object IndikererFeilutbetaling : IllegalStateException("Indikasjon på feilutbetaling, men detaljer for feilutbetaling mangler.")
}

sealed class TolketDetalj {
    abstract val beløp: Int

    companion object {
        fun from(simulertDetaljer: SimulertDetaljer, forfall: LocalDate) = when {
            simulertDetaljer.erTidligereUtbetalt() -> {
                TidligereUtbetalt(beløp = simulertDetaljer.belop)
            }
            simulertDetaljer.erFeilutbetaling() -> {
                Feilutbetaling(beløp = simulertDetaljer.belop)
            }
            simulertDetaljer.erØnsketUtbetalt() -> {
                Ordinær(beløp = simulertDetaljer.belop, forfall, simulertDetaljer.faktiskFraOgMed)
            }
            else -> null
        }

        private fun SimulertDetaljer.erFeilutbetaling() = klasseType == KlasseType.FEIL

        private fun SimulertDetaljer.erTidligereUtbetalt() = klasseType == KlasseType.YTEL &&
            klassekode == KlasseKode.SUUFORE &&
            typeSats == "" &&
            antallSats == 0 &&
            tilbakeforing &&
            sats == 0 &&
            belop < 0

        private fun SimulertDetaljer.erØnsketUtbetalt() = klasseType == KlasseType.YTEL &&
            klassekode == KlasseKode.SUUFORE &&
            typeSats == "MND" &&
            antallSats == 1 &&
            sats > 0 &&
            belop > 0
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
