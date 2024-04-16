package beregning.domain

import arrow.core.NonEmptyList
import arrow.core.toNonEmptyListOrNull
import no.nav.su.se.bakover.common.domain.tid.periode.EmptyPerioder
import no.nav.su.se.bakover.common.domain.tid.periode.NonEmptyPerioder
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

fun List<Beregningsperiode>.perioder() = this.toNonEmptyListOrNull()?.perioder() ?: EmptyPerioder

fun NonEmptyList<Beregningsperiode>.perioder() = NonEmptyPerioder.create(this.map { it.periode() })
