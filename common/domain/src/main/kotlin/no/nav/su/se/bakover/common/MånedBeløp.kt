package no.nav.su.se.bakover.common

import no.nav.su.se.bakover.common.periode.Måned
import java.text.NumberFormat
import java.time.LocalDate
import kotlin.math.abs

data class Månedsbeløp(
    val månedbeløp: List<MånedBeløp>,
) : List<MånedBeløp> by månedbeløp {
    init {
        månedbeløp.map { it.periode }.let {
            require(
                it.distinct() == it,
            ) { "Det kan kun eksistere 1 element for hver måned" }
        }
    }

    fun sum(): Int {
        return månedbeløp.sumOf { it.beløp.sum() }
    }

    fun senesteDato(): LocalDate {
        return månedbeløp.maxOf { it.periode.tilOgMed }
    }

    fun måneder(): List<Måned> {
        return månedbeløp.map { it.periode }.distinct()
    }

    operator fun plus(other: Månedsbeløp): Månedsbeløp {
        return Månedsbeløp(månedbeløp + other.månedbeløp)
    }
}

data class MånedBeløp(
    /** TODO jah; Burde vært renamet til 'måned', men usikker på om den serialiseres til json i database etc. */
    val periode: Måned,
    val beløp: Beløp,
) {
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
        return NumberFormat.getNumberInstance(norwegianLocale).format(value)
    }
}
