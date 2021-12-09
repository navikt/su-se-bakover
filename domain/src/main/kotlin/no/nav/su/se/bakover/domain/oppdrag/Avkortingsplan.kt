package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import java.time.Clock
import java.util.UUID
import kotlin.math.abs

internal class Avkortingsplan constructor(
    feilutbetalinger: List<Pair<Periode, Int>>,
    beregning: Beregning,
    private val clock: Clock,
) {
    private data class PeriodeOgBeløp(
        val periode: Periode,
        val beløp: Int,
    )

    private val feilutbetalinger: MutableList<PeriodeOgBeløp> = feilutbetalinger.map { (periode, beløp) ->
        PeriodeOgBeløp(periode, abs(beløp))
    }.toMutableList()

    private val tilbakebetalinger: List<PeriodeOgBeløp> = lagTilbakebetalingsplan(beregning)

    private fun lagTilbakebetalingsplan(
        beregning: Beregning,
    ): List<PeriodeOgBeløp> {
        return beregning.getMånedsberegninger()
            .map { it.periode to it.getSumYtelse() }
            .let { lagTilbakebetalingsplan(tilbakebetalingsperiodeOgBeløpsgrense = it) }
    }

    private fun lagTilbakebetalingsplan(
        tilbakebetalingsperiodeOgBeløpsgrense: List<Pair<Periode, Int>>,
    ): List<PeriodeOgBeløp> {
        val tilbakebetalinger: MutableList<PeriodeOgBeløp> = mutableListOf()

        fun saldo() = feilutbetalinger.sumOf { it.beløp } - tilbakebetalinger.sumOf { it.beløp }

        fun kalkulerMaksbeløp(beløpsgrense: Int): Int {
            return if (saldo() >= beløpsgrense) {
                beløpsgrense
            } else {
                saldo()
            }
        }

        tilbakebetalingsperiodeOgBeløpsgrense.filter { it.second > 0 }
            .let { filtrert ->
                var idx = 0
                while (saldo() > 0 && idx <= filtrert.lastIndex) {
                    filtrert[idx].let { (periode, beløpsgrense) ->
                        tilbakebetalinger.add(
                            PeriodeOgBeløp(
                                periode = periode,
                                beløp = kalkulerMaksbeløp(beløpsgrense),
                            ),
                        )
                    }
                    idx++
                }
            }
        return tilbakebetalinger
    }

    fun lagFradrag(): List<Grunnlag.Fradragsgrunnlag> {
        return tilbakebetalinger.map {
            Grunnlag.Fradragsgrunnlag.create(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                fradrag = FradragFactory.ny(
                    type = Fradragstype.AvkortingUtenlandsopphold,
                    månedsbeløp = it.beløp.toDouble(),
                    periode = it.periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            )
        }
    }
}
