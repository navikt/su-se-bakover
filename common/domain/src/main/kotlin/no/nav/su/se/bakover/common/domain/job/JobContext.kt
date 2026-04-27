package no.nav.su.se.bakover.common.domain.job

import java.time.LocalDate
import java.time.YearMonth

interface JobContext {
    fun id(): JobContextId
}

interface JobContextId {
    fun value(): String
}

data class NameAndYearMonthId(
    val name: String,
    val yearMonth: YearMonth,
) : JobContextId {
    override fun value(): String {
        return """$name$yearMonth"""
    }
}

data class NameAndLocalDateId(
    val name: String,
    val date: LocalDate,
) : JobContextId {
    override fun value(): String {
        return """$name$date"""
    }
}
