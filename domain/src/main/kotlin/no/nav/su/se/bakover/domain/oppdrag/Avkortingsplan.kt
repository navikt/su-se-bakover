package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import java.util.UUID

class Avkortingsplan constructor(
    feilutbetalinger: List<Pair<Periode, Int>>,
    beregning: Beregning,
) {
    private data class Objekt(
        val periode: Periode,
        val beløp: Int,
    )

    private val feilutbetalinger: MutableList<Objekt> = feilutbetalinger.map { (periode, beløp) ->
        Objekt(periode, kotlin.math.abs(beløp))
    }.toMutableList()

    private val tilbakebetalinger: List<Objekt> = lagTilbakebetalingsplan(beregning)

    private fun lagTilbakebetalingsplan(
        beregning: Beregning,
    ): List<Objekt> {
        return beregning.getMånedsberegninger()
            .map { it.periode to it.getSumYtelse() }
            .let { lagTilbakebetalingsplan(tilbakebetalingsperiodeOgBeløpsgrense = it) }
    }

    private fun lagTilbakebetalingsplan(
        tilbakebetalingsperiodeOgBeløpsgrense: List<Pair<Periode, Int>>,
    ): List<Objekt> {
        val tilbakebetalinger: MutableList<Objekt> = mutableListOf()

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
                do {
                    filtrert[idx].let { (periode, beløpsgrense) ->
                        tilbakebetalinger.add(
                            Objekt(
                                periode = periode,
                                beløp = kalkulerMaksbeløp(beløpsgrense),
                            ),
                        )
                    }
                } while (saldo() > 0 && ++idx <= filtrert.lastIndex)
            }
        return tilbakebetalinger
    }

    fun lagFradrag(): List<Grunnlag.Fradragsgrunnlag> {
        return tilbakebetalinger.map {
            Grunnlag.Fradragsgrunnlag.create(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                fradrag = FradragFactory.ny(
                    type = Fradragstype.BidragEtterEkteskapsloven,
                    månedsbeløp = it.beløp.toDouble(),
                    periode = it.periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            )
        }
    }
}
