package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.periode.Periode
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
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

fun List<MånedBeløp>.sorterPåPeriode(): List<MånedBeløp> = this.sortedBy { it.periode }

@JvmInline
value class Beløp private constructor(
    private val value: Int,
) {
    companion object {
        operator fun invoke(int: Int): Beløp {
            return Beløp(abs(int))
        }

        fun zero(): Beløp {
            return invoke(0)
        }
    }

    operator fun plus(other: Beløp): Beløp {
        return invoke(value + other.value)
    }

    fun sum(): Int {
        return value
    }

    fun tusenseparert(): String {
        return NumberFormat.getNumberInstance(Locale("nb", "NO")).format(value)
    }
}
