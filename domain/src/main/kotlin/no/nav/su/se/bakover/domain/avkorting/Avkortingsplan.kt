package no.nav.su.se.bakover.domain.avkorting

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
    init {
        check(beregning.getFradrag().none { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold })
    }

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
            return minOf(saldo(), beløpsgrense)
        }

        tilbakebetalingsperiodeOgBeløpsgrense
            .filter { it.second > 0 }
            .let { filtrert ->
                filtrert.forEach { (periode, beløpsgrense) ->
                    if (saldo() > 0) {
                        tilbakebetalinger.add(
                            PeriodeOgBeløp(
                                periode = periode,
                                beløp = kalkulerMaksbeløp(beløpsgrense),
                            ),
                        )
                    } else {
                        return@let
                    }
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
