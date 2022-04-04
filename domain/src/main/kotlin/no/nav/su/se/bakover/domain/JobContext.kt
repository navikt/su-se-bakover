package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.persistence.TransactionContext
import java.time.Clock
import java.time.YearMonth

sealed class JobContext {

    abstract fun id(): JobContextId

    companion object {
        enum class Typer {
            SendPåminnelseNyStønadsperiode
        }
    }
}

data class SendPåminnelseNyStønadsperiodeContext(
    private val clock: Clock,
    private val id: NameAndYearMonthId = genererIdForTidspunkt(clock),
    private val opprettet: Tidspunkt = Tidspunkt.now(clock),
    private val endret: Tidspunkt = opprettet,
    private val prosessert: Set<Saksnummer> = emptySet(),
    private val sendt: Set<Saksnummer> = emptySet(),
) : JobContext() {

    override fun id(): NameAndYearMonthId {
        return id
    }

    fun opprettet(): Tidspunkt {
        return opprettet
    }

    fun endret(): Tidspunkt {
        return endret
    }

    fun prosessert(saksnummer: Saksnummer): SendPåminnelseNyStønadsperiodeContext {
        return copy(prosessert = prosessert + saksnummer)
    }

    fun prosessert(): Set<Saksnummer> {
        return prosessert
    }

    fun sendt(): Set<Saksnummer> {
        return sendt
    }

    fun sendt(saksnummer: Saksnummer): SendPåminnelseNyStønadsperiodeContext {
        return prosessert(saksnummer).copy(sendt = sendt + saksnummer)
    }

    fun oppsummering(): String {
        return """
            ${"\n"}
            Oppsummering av jobb: ${id.jobName},
            Måned: ${id.yearMonth},
            Opprettet: $opprettet,
            Endret: $endret,
            Prosessert: $prosessert,
            Sendt: $sendt
        """.trimIndent()
    }

    companion object {
        fun genererIdForTidspunkt(clock: Clock): NameAndYearMonthId {
            return NameAndYearMonthId(
                jobName = type().toString(),
                yearMonth = YearMonth.now(clock),
            )
        }

        fun type(): JobContext.Companion.Typer {
            return JobContext.Companion.Typer.SendPåminnelseNyStønadsperiode
        }
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
}

interface JobContextRepo {
    fun <T : JobContext> hent(id: JobContextId): T?
    fun lagre(jobContext: JobContext, context: TransactionContext)
}
