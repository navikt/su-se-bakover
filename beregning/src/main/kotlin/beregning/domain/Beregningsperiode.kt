package beregning.domain

import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode

data class Beregningsperiode(
    private val periode: Periode,
    private val strategy: BeregningStrategy,
) {
    fun periode(): Periode {
        return periode
    }

    fun månedsoversikt(): Map<Måned, BeregningStrategy> {
        return periode.måneder().associateWith { strategy }
    }
}
