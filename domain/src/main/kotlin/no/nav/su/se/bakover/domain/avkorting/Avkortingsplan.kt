package no.nav.su.se.bakover.domain.avkorting

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Beløp
import no.nav.su.se.bakover.common.MånedBeløp
import no.nav.su.se.bakover.common.Månedsbeløp
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.tilMåned
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import java.time.Clock
import java.util.UUID

internal class Avkortingsplan(
    private val feilutbetaltBeløp: Int,
    beregningUtenAvkorting: Beregning,
    private val clock: Clock,
) {
    init {
        check(beregningUtenAvkorting.getFradrag().none { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold }) { "Beregning inneholder allerede fradrag av type: ${Fradragstype.AvkortingUtenlandsopphold}. Gamle fradrag må fjenres før ny beregning kan gjennomføres." }
    }

    private val tilbakebetalinger: Månedsbeløp = lagTilbakebetalingsplan(beregningUtenAvkorting)

    private fun lagTilbakebetalingsplan(
        beregning: Beregning,
    ): Månedsbeløp {
        return beregning.getMånedsberegninger()
            .map { it.periode to it.getSumYtelse() }
            .let { lagTilbakebetalingsplan(tilbakebetalingsperiodeOgBeløpsgrense = it) }
    }

    private fun lagTilbakebetalingsplan(
        tilbakebetalingsperiodeOgBeløpsgrense: List<Pair<Periode, Int>>,
    ): Månedsbeløp {
        val tilbakebetalinger: MutableList<MånedBeløp> = mutableListOf()

        fun saldo() = feilutbetaltBeløp - tilbakebetalinger.sumOf { it.beløp.sum() }

        fun kalkulerMaksbeløp(beløpsgrense: Int): Int {
            return minOf(saldo(), beløpsgrense)
        }

        tilbakebetalingsperiodeOgBeløpsgrense
            .filter { it.second > 0 }
            .let { filtrert ->
                filtrert.forEach { (periode, beløpsgrense) ->
                    if (saldo() > 0) {
                        tilbakebetalinger.add(
                            MånedBeløp(
                                periode = periode.tilMåned(),
                                beløp = Beløp(kalkulerMaksbeløp(beløpsgrense)),
                            ),
                        )
                    } else {
                        return@let
                    }
                }
            }

        return Månedsbeløp(tilbakebetalinger)
    }

    fun lagFradrag(): Either<KunneIkkeLageAvkortingsplan, List<Grunnlag.Fradragsgrunnlag>> {
        if (feilutbetaltBeløp != tilbakebetalinger.sum()) {
            return KunneIkkeLageAvkortingsplan.AvkortingErUfullstendig.left()
        }
        return tilbakebetalinger.månedbeløp.map {
            Grunnlag.Fradragsgrunnlag.create(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                fradrag = FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.AvkortingUtenlandsopphold,
                    månedsbeløp = it.beløp.sum().toDouble(),
                    periode = it.periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            )
        }.right()
    }

    sealed class KunneIkkeLageAvkortingsplan {
        object AvkortingErUfullstendig : KunneIkkeLageAvkortingsplan()
    }
}
