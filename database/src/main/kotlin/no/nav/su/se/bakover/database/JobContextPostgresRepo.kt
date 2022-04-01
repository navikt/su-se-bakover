package no.nav.su.se.bakover.database

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.database.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.domain.JobContext
import no.nav.su.se.bakover.domain.JobContextId
import no.nav.su.se.bakover.domain.JobContextRepo
import no.nav.su.se.bakover.domain.NameAndYearMonthId
import no.nav.su.se.bakover.domain.Saksnummer
import java.time.Clock
import java.time.YearMonth

internal class JobContextPostgresRepo(
    private val clock: Clock,
    private val sessionFactory: PostgresSessionFactory,
) : JobContextRepo {

    @Suppress("UNCHECKED_CAST")
    override fun <T : JobContext> hent(id: JobContextId): T? {
        return sessionFactory.withSession { session ->
            """select * from job_context where id = :id""".hent(
                mapOf(
                    "id" to id.value(),
                ),
                session,
            ) {
                it.toJobContext()
            }
        } as? T
    }

    override fun lagre(jobContext: JobContext, context: TransactionContext) {
        when (jobContext) {
            is JobContext.SendPåminnelseNyStønadsperiodeContext -> {
                jobContext.toDb()
            }
        }.let {
            context.withSession { session ->
                """insert into job_context 
                    (
                        id, 
                        context
                    ) 
                    values 
                    (
                        :id, 
                        to_json(:context::json)
                    ) 
                    on conflict(id) do 
                    update set 
                        context = to_json(:context::json)
                """.trimIndent().insert(
                    mapOf(
                        "id" to it.id,
                        "context" to objectMapper.writeValueAsString(it),
                    ),
                    session,
                )
            }
        }
    }

    private fun Row.toJobContext(): JobContext {
        val context = string("context").let {
            objectMapper.readValue<JobContextDb.SendPåminnelseNyStønadsperiodeContextDb>(it)
        }

        return JobContext.SendPåminnelseNyStønadsperiodeContext(
            clock = clock,
            id = NameAndYearMonthId(
                jobName = context.jobName,
                yearMonth = context.yearMonth,
            ),
            opprettet = context.opprettet,
            endret = context.endret,
            prosessert = context.prosessert.map { Saksnummer(it) }.toSet(),
            sendt = context.sendt.map { Saksnummer(it) }.toSet(),
        )
    }

    private fun JobContext.SendPåminnelseNyStønadsperiodeContext.toDb(): JobContextDb.SendPåminnelseNyStønadsperiodeContextDb {
        return JobContextDb.SendPåminnelseNyStønadsperiodeContextDb(
            id = id().value(),
            jobName = id().jobName,
            yearMonth = id().yearMonth,
            opprettet = opprettet(),
            endret = endret(),
            prosessert = prosessert().map { it.nummer },
            sendt = sendt().map { it.nummer },
        )
    }

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
    )
    @JsonSubTypes(
        JsonSubTypes.Type(
            value = JobContextDb.SendPåminnelseNyStønadsperiodeContextDb::class,
            name = "SendPåminnelseNyStønadsperiode",
        ),
    )
    sealed class JobContextDb {

        data class SendPåminnelseNyStønadsperiodeContextDb(
            val id: String,
            val jobName: String,
            val yearMonth: YearMonth,
            val opprettet: Tidspunkt,
            val endret: Tidspunkt,
            val prosessert: List<Long>,
            val sendt: List<Long>,
        ) : JobContextDb()
    }
}
