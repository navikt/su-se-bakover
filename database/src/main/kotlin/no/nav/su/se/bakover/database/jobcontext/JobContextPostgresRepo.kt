package no.nav.su.se.bakover.database.jobcontext

import no.nav.su.se.bakover.common.domain.job.JobContext
import no.nav.su.se.bakover.common.domain.job.JobContextId
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.persistence.TransactionContext

class JobContextPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
) {
    fun <T : JobContext> hent(
        id: JobContextId,
        deserialize: (String) -> T,
    ): T? {
        return sessionFactory.withSession { session ->
            """select * from job_context where id = :id""".hent(
                mapOf(
                    "id" to id.value(),
                ),
                session,
            ) {
                deserialize(it.string("context"))
            }
        }
    }

    fun <T : JobContext> lagre(
        jobContext: T,
        serialize: (T) -> String,
        transactionContext: TransactionContext,
    ) {
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
                    "id" to jobContext.id().value(),
                    "context" to serialize(jobContext),
                ),
                session,
            )
        }
    }
}
