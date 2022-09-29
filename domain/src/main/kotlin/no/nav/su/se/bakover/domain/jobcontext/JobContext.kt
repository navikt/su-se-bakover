package no.nav.su.se.bakover.domain.jobcontext

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.TransactionContext
import java.time.LocalDate
import java.time.YearMonth

sealed class JobContext {

    abstract fun id(): JobContextId

    enum class Typer {
        SendPåminnelseNyStønadsperiode,
        KontrollsamtaleFristUtløptContext,
    }
}

interface JobContextId {
    fun value(): String
}

data class NameAndYearMonthId(
    val jobName: String,
    val yearMonth: YearMonth,
) : JobContextId {
    override fun value(): String {
        return """$jobName$yearMonth"""
    }

    fun tilPeriode(): Periode {
        return Periode.create(
            fraOgMed = LocalDate.of(yearMonth.year, yearMonth.month, 1),
            tilOgMed = yearMonth.atEndOfMonth(),
        )
    }
}

data class NameAndLocalDateId(
    val jobName: String,
    val date: LocalDate,
) : JobContextId {
    override fun value(): String {
        return """$jobName$date"""
    }
}

interface JobContextRepo {
    fun <T : JobContext> hent(id: JobContextId): T?
    fun lagre(jobContext: JobContext, transactionContext: TransactionContext)
}
