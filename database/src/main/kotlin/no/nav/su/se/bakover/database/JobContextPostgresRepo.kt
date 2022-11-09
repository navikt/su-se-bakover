package no.nav.su.se.bakover.database

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.persistence.hent
import no.nav.su.se.bakover.common.persistence.insert
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.JobContextPostgresRepo.FeiletDb.Companion.toDb
import no.nav.su.se.bakover.database.JobContextPostgresRepo.FeiletDb.Companion.toDomain
import no.nav.su.se.bakover.database.JobContextPostgresRepo.SendPåminnelseNyStønadsperiodeContextDb.Companion.toDb
import no.nav.su.se.bakover.domain.jobcontext.JobContext
import no.nav.su.se.bakover.domain.jobcontext.JobContextId
import no.nav.su.se.bakover.domain.jobcontext.JobContextRepo
import no.nav.su.se.bakover.domain.jobcontext.NameAndLocalDateId
import no.nav.su.se.bakover.domain.jobcontext.NameAndYearMonthId
import no.nav.su.se.bakover.domain.jobcontext.SendPåminnelseNyStønadsperiodeContext
import no.nav.su.se.bakover.domain.kontrollsamtale.UtløptFristForKontrollsamtaleContext
import no.nav.su.se.bakover.domain.sak.Saksnummer
import java.lang.IllegalArgumentException
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

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
                deserialize<JobContextDb>(it.string("context")).toDomain()
            }
        } as T?
    }

    override fun lagre(jobContext: JobContext, transactionContext: TransactionContext) {
        when (jobContext) {
            is SendPåminnelseNyStønadsperiodeContext -> {
                jobContext.toDb()
            }

            is UtløptFristForKontrollsamtaleContext -> {
                jobContext.toDb()
            }

            else -> {
                throw IllegalArgumentException("Ukjent type:$jobContext")
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
                        "id" to it.id(),
                        "context" to it.toJson(),
                    ),
                    session,
                )
            }
        }
    }

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
    )
    @JsonSubTypes(
        JsonSubTypes.Type(
            value = SendPåminnelseNyStønadsperiodeContextDb::class,
            name = "SendPåminnelseNyStønadsperiode",
        ),
        JsonSubTypes.Type(
            value = UtløptFristForKontrollsamtaleDb::class,
            name = "HåndterUtløptFristForKontrollsamtale",
        ),
    )
    sealed class JobContextDb {
        abstract fun id(): String
        abstract fun toJson(): String

        abstract fun toDomain(): JobContext
    }

    data class SendPåminnelseNyStønadsperiodeContextDb(
        val id: String,
        val jobName: String,
        val yearMonth: YearMonth,
        val opprettet: Tidspunkt,
        val endret: Tidspunkt,
        val prosessert: List<Long>,
        val sendt: List<Long>,
    ) : JobContextDb() {
        override fun id(): String {
            return id
        }

        override fun toJson(): String {
            return serialize(this)
        }

        override fun toDomain(): SendPåminnelseNyStønadsperiodeContext {
            return SendPåminnelseNyStønadsperiodeContext(
                id = NameAndYearMonthId(
                    name = jobName,
                    yearMonth = yearMonth,
                ),
                opprettet = opprettet,
                endret = endret,
                prosessert = prosessert.map { Saksnummer(it) }.toSet(),
                sendt = sendt.map { Saksnummer(it) }.toSet(),
            )
        }

        companion object {
            fun SendPåminnelseNyStønadsperiodeContext.toDb(): SendPåminnelseNyStønadsperiodeContextDb {
                return SendPåminnelseNyStønadsperiodeContextDb(
                    id = id().value(),
                    jobName = id().name,
                    yearMonth = id().yearMonth,
                    opprettet = opprettet(),
                    endret = endret(),
                    prosessert = prosessert().map { it.nummer },
                    sendt = sendt().map { it.nummer },
                )
            }
        }
    }

    data class UtløptFristForKontrollsamtaleDb(
        val id: String,
        val jobName: String,
        val dato: String,
        val opprettet: Tidspunkt,
        val endret: Tidspunkt,
        // Ønsker beholde ordering på vei inn og ut av basen.
        val prosessert: List<UUID>,
        val møtt: List<UUID>,
        val ikkeMøtt: List<UUID>,
        val feilet: List<FeiletDb>,
    ) : JobContextDb() {
        override fun id(): String {
            return id
        }
        override fun toJson(): String {
            return serialize(this)
        }

        override fun toDomain(): JobContext {
            return UtløptFristForKontrollsamtaleContext(
                id = NameAndLocalDateId(
                    name = jobName,
                    date = LocalDate.parse(dato),
                ),
                opprettet = opprettet,
                endret = endret,
                prosessert = prosessert.toSet(),
                ikkeMøtt = ikkeMøtt.toSet(),
                feilet = feilet.toSet().toDomain(),
            )
        }
    }

    data class FeiletDb(
        val id: UUID,
        val retries: Int,
        val feil: String,
        val oppgaveId: String?,
    ) {

        fun toDomain(): UtløptFristForKontrollsamtaleContext.Feilet {
            return UtløptFristForKontrollsamtaleContext.Feilet(
                id = id,
                retries = retries,
                feil = feil,
                oppgaveId = oppgaveId,
            )
        }
        companion object {

            fun Set<UtløptFristForKontrollsamtaleContext.Feilet>.toDb(): Set<FeiletDb> {
                return map { it.toDb() }.toSet()
            }
            fun UtløptFristForKontrollsamtaleContext.Feilet.toDb(): FeiletDb {
                return FeiletDb(id, retries, feil, oppgaveId)
            }

            fun Set<FeiletDb>.toDomain(): Set<UtløptFristForKontrollsamtaleContext.Feilet> {
                return map { it.toDomain() }.toSet()
            }
        }
    }
    companion object {
        fun UtløptFristForKontrollsamtaleContext.toDb(): UtløptFristForKontrollsamtaleDb {
            return UtløptFristForKontrollsamtaleDb(
                id = id().value(),
                jobName = id().name,
                dato = id().date.toString(),
                opprettet = opprettet(),
                endret = endret(),
                prosessert = prosessert().toList(),
                møtt = møtt().toList(),
                ikkeMøtt = ikkeMøtt().toList(),
                feilet = feilet().toDb().toList(),
            )
        }
    }
}
