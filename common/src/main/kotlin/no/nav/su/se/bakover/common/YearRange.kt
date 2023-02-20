package no.nav.su.se.bakover.common

import java.time.Year

data class YearRange(override val start: Year, override val endInclusive: Year) : ClosedRange<Year>, Collection<Year> {

    override val size: Int = java.time.temporal.ChronoUnit.YEARS.between(start,endInclusive.plusYears(1)).let {
        Math.toIntExact(it)
    }

    override fun isEmpty(): Boolean {
        return super.isEmpty()
    }

    override fun containsAll(elements: Collection<Year>): Boolean {
        return this.map { it }.containsAll(elements)
    }

    override fun contains(@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE") element: Year): Boolean {
        // Begge interfacene har samme metode med forskjellig navn på parameter.
        return super.contains(element)
    }

    override fun iterator(): Iterator<Year> {
        return object : Iterator<Year> {

            var step = start

            override fun hasNext(): Boolean {
                return step <= endInclusive
            }

            override fun next(): Year {
                return start.also {
                    step = step.plusYears(1)
                }
            }
        }
    }
}

operator fun Year.inc(): Year = this.plusYears(1)

operator fun Year.rangeTo(endInclusive: Year): YearRange = YearRange(this,endInclusive)
fun Year.until(endExclusive:Year) = YearRange(this,endExclusive.minusYears(1))
