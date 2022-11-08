package no.nav.su.se.bakover.domain.jobcontext

import no.nav.su.se.bakover.common.periode.Periode
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

    fun tilPeriode(): Periode {
        return Periode.create(
            fraOgMed = yearMonth.atDay(1),
            tilOgMed = yearMonth.atEndOfMonth(),
        )
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
