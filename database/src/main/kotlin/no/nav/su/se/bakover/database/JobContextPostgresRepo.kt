package no.nav.su.se.bakover.database

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import kotliquery.Row
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.JobContextPostgresRepo.JobContextDb.SendPåminnelseNyStønadsperiodeContextDb.Companion.toDb
import no.nav.su.se.bakover.database.JobContextPostgresRepo.JobContextDb.SendPåminnelseNyStønadsperiodeContextDb.Companion.toDomain
import no.nav.su.se.bakover.database.JobContextPostgresRepo.JobContextDb.SendPåminnelseNyStønadsperiodeContextDb.Companion.toJson
import no.nav.su.se.bakover.database.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.domain.JobContext
import no.nav.su.se.bakover.domain.JobContextId
import no.nav.su.se.bakover.domain.JobContextRepo
import no.nav.su.se.bakover.domain.NameAndYearMonthId
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.SendPåminnelseNyStønadsperiodeContext
import java.time.YearMonth

internal class JobContextPostgresRepo(
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

    override fun lagre(jobContext: JobContext, transactionContext: TransactionContext) {
        when (jobContext) {
            is SendPåminnelseNyStønadsperiodeContext -> {
                jobContext.toDb()
            }
        }.let {
            transactionContext.withSession { session ->
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
                        "context" to it.toJson(),
                    ),
                    session,
                )
            }
        }
    }

    private fun Row.toJobContext(): JobContext {
        return string("context").toDomain()
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
        ) : JobContextDb() {
            companion object {
                fun SendPåminnelseNyStønadsperiodeContext.toDb(): SendPåminnelseNyStønadsperiodeContextDb {
                    return SendPåminnelseNyStønadsperiodeContextDb(
                        id = id().value(),
                        jobName = id().jobName,
                        yearMonth = id().yearMonth,
                        opprettet = opprettet(),
                        endret = endret(),
                        prosessert = prosessert().map { it.nummer },
                        sendt = sendt().map { it.nummer },
                    )
                }

                fun SendPåminnelseNyStønadsperiodeContextDb.toJson(): String {
                    return serialize(this)
                }

                fun String.toDomain(): SendPåminnelseNyStønadsperiodeContext {
                    return deserialize<SendPåminnelseNyStønadsperiodeContextDb>(this).let {
                        SendPåminnelseNyStønadsperiodeContext(
                            id = NameAndYearMonthId(
                                jobName = it.jobName,
                                yearMonth = it.yearMonth,
                            ),
                            opprettet = it.opprettet,
                            endret = it.endret,
                            prosessert = it.prosessert.map { Saksnummer(it) }.toSet(),
                            sendt = it.sendt.map { Saksnummer(it) }.toSet(),
                        )
                    }
                }
            }
        }
    }
}
