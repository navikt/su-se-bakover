package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.periode.Periode
import java.time.LocalDate
import kotlin.math.abs

data class Månedsbeløp(
    val månedbeløp: List<MånedBeløp>,
) {
    init {
        require(
            månedbeløp.none { a ->
                månedbeløp.minus(a).any { b -> a.periode overlapper b.periode }
            },
        ) { "Det kan kun eksistere 1 element for hver måned" }
    }

    fun sum(): Int {
        return månedbeløp.sumOf { it.beløp.sum() }
    }

    fun senesteDato(): LocalDate {
        return månedbeløp.maxOf { it.periode.tilOgMed }
    }
}

data class MånedBeløp(
    val periode: Periode,
    val beløp: Beløp,
) {
    init {
        require(periode.getAntallMåneder() == 1) { "Periode kan kun være 1 måned lang" }
    }

    fun sum(): Int {
        return beløp.sum()
    }
}

@JvmInline
value class Beløp private constructor(
    private val value: Int,
) {
    companion object {
        operator fun invoke(int: Int): Beløp {
            return Beløp(abs(int))
        }
    }

    fun sum(): Int {
        return value
    }
}
