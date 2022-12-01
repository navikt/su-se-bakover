package no.nav.su.se.bakover.domain.avkorting

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.application.Beløp
import no.nav.su.se.bakover.common.application.MånedBeløp
import no.nav.su.se.bakover.common.application.Månedsbeløp
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.tilMåned
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import java.time.Clock
import java.util.UUID

internal class Avkortingsplan(
    private val feilutbetaltBeløp: Int,
    beregning: Beregning,
    private val clock: Clock,
) {
    init {
        check(beregning.getFradrag().none { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold }) { "Beregning inneholder allerede fradrag av type: ${Fradragstype.AvkortingUtenlandsopphold}. Gamle fradrag må fjenres før ny beregning kan gjennomføres." }
    }

    private val tilbakebetalinger: Månedsbeløp = lagTilbakebetalingsplan(beregning)

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
        return if (feilutbetaltBeløp == tilbakebetalinger.sum()) {
            tilbakebetalinger.månedbeløp.map {
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
        } else {
            KunneIkkeLageAvkortingsplan.AvkortingErUfullstendig.left()
        }
    }

    sealed class KunneIkkeLageAvkortingsplan {
        object AvkortingErUfullstendig : KunneIkkeLageAvkortingsplan()
    }
}
