package no.nav.su.se.bakover.domain.oppdrag.simulering

import arrow.core.extensions.list.foldable.exists
import java.time.LocalDate

data class TolketSimulering(
    private val simulering: Simulering,
) {
    val simulertePerioder = simulering.periodeList.map {
        TolketPeriode(
            it.fraOgMed,
            it.tilOgMed,
            it.utbetaling.map { simulertUtbetaling ->
                val detaljer = simulertUtbetaling.detaljer.map { simulertDetalj ->
                    TolketDetalj.from(simulertDetalj)
                }.filterNotNull()
                TolketUtbetaling.from(detaljer)
            },
        )
    }
}

data class TolketPeriode(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val utbetaling: List<TolketUtbetaling>,
)

sealed class TolketUtbetaling {
    abstract val tolketDetalj: List<TolketDetalj>

    companion object {
        fun from(tolketDetaljer: List<TolketDetalj>) = when {
            tolketDetaljer.erFeilutbetaling() -> {
                Feilutbetaling(tolketDetaljer)
            }
            tolketDetaljer.erEtterbetaling() -> {
                Etterbetaling(tolketDetaljer + TolketDetalj.Etterbetaling(tolketDetaljer.sumBy { it.beløp }))
            }
            tolketDetaljer.erOrdinær() -> {
                Ordinær(tolketDetaljer)
            }
            else -> throw IllegalStateException("Fikk ikke til")
        }

        private fun List<TolketDetalj>.erFeilutbetaling() = any { it is TolketDetalj.Tilbakekreving }

        private fun List<TolketDetalj>.erEtterbetaling() = count() == 2 &&
            exists { it is TolketDetalj.Ordinær } && exists { it is TolketDetalj.TidligereUtbetalt }

        private fun List<TolketDetalj>.erOrdinær() = count() == 1 &&
            all { it is TolketDetalj.Ordinær }
    }

    data class Ordinær(
        override val tolketDetalj: List<TolketDetalj>,
    ) : TolketUtbetaling()

    data class Feilutbetaling(
        override val tolketDetalj: List<TolketDetalj>,
    ) : TolketUtbetaling()

    data class Etterbetaling(
        override val tolketDetalj: List<TolketDetalj>,
    ) : TolketUtbetaling()
}

sealed class TolketDetalj {
    abstract val beløp: Int

    companion object {
        fun from(simulertDetaljer: SimulertDetaljer) = when {
            simulertDetaljer.erTidligereUtbetalt() -> {
                TidligereUtbetalt(beløp = simulertDetaljer.belop)
            }
            simulertDetaljer.erTilbakekreving() -> {
                Tilbakekreving(beløp = simulertDetaljer.belop)
            }
            simulertDetaljer.erØnsketUtbetalt() -> {
                Ordinær(beløp = simulertDetaljer.belop)
            }
            else -> null
        }

        private fun SimulertDetaljer.erTilbakekreving() = klasseType == KlasseType.FEIL

        private fun SimulertDetaljer.erØnsketUtbetalt() = klasseType == KlasseType.YTEL &&
            klassekode == KlasseKode.SUUFORE &&
            typeSats == "MND" &&
            antallSats == 1 &&
            sats > 0 &&
            belop > 0

        private fun SimulertDetaljer.erTidligereUtbetalt() = klasseType == KlasseType.YTEL &&
            klassekode == KlasseKode.SUUFORE &&
            typeSats == "" &&
            antallSats == 0 &&
            tilbakeforing &&
            sats == 0 &&
            belop < 0
    }

    data class Etterbetaling(
        override val beløp: Int,
    ) : TolketDetalj()

    data class Tilbakekreving(
        override val beløp: Int,
    ) : TolketDetalj()

    data class Ordinær(
        override val beløp: Int,
    ) : TolketDetalj()

    data class TidligereUtbetalt(
        override val beløp: Int,
    ) : TolketDetalj()
}
