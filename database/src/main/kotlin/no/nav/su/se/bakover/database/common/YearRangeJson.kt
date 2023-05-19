package no.nav.su.se.bakover.database.common

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.YearRange
import java.time.Year

data class YearRangeJson(val fra: Int, val til: Int) {
    fun toYearRange(): YearRange = YearRange(Year.of(fra), Year.of(til))

    companion object {
        fun YearRange.toStringifiedYearRangeJson(): String = this.toYearRangeJson().let { serialize(it) }

        fun YearRange.toYearRangeJson(): YearRangeJson = YearRangeJson(this.start.value, this.endInclusive.value)

        fun toYearRange(json: String): YearRange = deserialize<YearRangeJson>(json).toYearRange()
    }
}
