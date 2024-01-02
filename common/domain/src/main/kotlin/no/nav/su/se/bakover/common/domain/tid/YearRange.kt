package no.nav.su.se.bakover.common.tid

import java.time.Clock
import java.time.Year

data class YearRange(override val start: Year, override val endInclusive: Year) : ClosedRange<Year>, Collection<Year> {

    constructor(start: Int, endInclusive: Int) : this(Year.of(start), Year.of(endInclusive))

    init {
        require(start <= endInclusive) { "Start-året må være før, eller lik slutt-året" }
    }

    override val size: Int = java.time.temporal.ChronoUnit.YEARS.between(start, endInclusive.plusYears(1)).let {
        Math.toIntExact(it)
    }

    override fun isEmpty(): Boolean = super.isEmpty()
    override fun containsAll(elements: Collection<Year>): Boolean = this.map { it }.containsAll(elements)

    // Begge interfacene har samme metode med forskjellig navn på parameter.
    override fun contains(@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE") element: Year): Boolean =
        super.contains(element)

    override fun iterator(): Iterator<Year> = object : Iterator<Year> {
        var step = start
        override fun hasNext(): Boolean = step <= endInclusive
        override fun next(): Year = step.also {
            step = step.plusYears(1)
        }
    }

    infix fun inneholder(other: YearRange): Boolean =
        this.minOf { it.value } <= other.minOf { it.value } && this.maxOf { it.value } >= other.maxOf { it.value }

    override fun toString(): String {
        return "YearRange(${start.value},${endInclusive.value})"
    }

    companion object {
        fun now(clock: Clock) {
            Year.now(clock).let {
                YearRange(it, it)
            }
        }
    }
}

operator fun Year.inc(): Year = this.plusYears(1)

operator fun Year.rangeTo(endInclusive: Year): YearRange = YearRange(this, endInclusive)
fun Year.until(endExclusive: Year) = YearRange(this, endExclusive.minusYears(1))
fun Year.toRange() = YearRange(this, this)

fun List<Year>.toYearRange(): YearRange = when {
    this.isEmpty() -> throw IllegalArgumentException("Kan ikke lage en YearRange med 0 elementer")
    else -> this.sorted().let {
        require(it.isIncrementingByOne())
        YearRange(it.first(), it.last())
    }
}

fun List<Year>.isIncrementingByOne(): Boolean = this.isNotEmpty() && this.zipWithNext().all { (a, b) -> a.inc() == b }

fun YearRange.krympTilØvreGrense(øvreGrense: Year): YearRange {
    return if (øvreGrense < start) {
        YearRange(øvreGrense, øvreGrense)
    } else {
        YearRange(start, min(endInclusive, øvreGrense))
    }
}

/**
 * Utivder nedre grense hvis nedre grense er før start.
 */
fun YearRange.utvidNedreGrense(nedreGrense: Year): YearRange {
    return if (nedreGrense >= start) {
        this
    } else {
        YearRange(nedreGrense, endInclusive)
    }
}

fun YearRange.krymptTilNedreGrense(nedreGrense: Year): YearRange {
    return if (nedreGrense > endInclusive) {
        YearRange(nedreGrense, nedreGrense)
    } else {
        YearRange(max(start, nedreGrense), endInclusive)
    }
}

fun min(y1: Year, y2: Year): Year = if (y1.value <= y2.value) y1 else y2
fun max(y1: Year, y2: Year): Year = if (y1.value > y2.value) y1 else y2

fun min(a: YearRange, b: YearRange): YearRange = when {
    a.start < b.start -> a
    a.start > b.start -> b
    else -> if (a.endInclusive <= b.endInclusive) a else b
}
