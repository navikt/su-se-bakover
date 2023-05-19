package no.nav.su.se.bakover.kontrollsamtale.infrastructure.persistence

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.database.jobcontext.JobContextDb
import no.nav.su.se.bakover.domain.jobcontext.NameAndLocalDateId
import no.nav.su.se.bakover.kontrollsamtale.domain.UtløptFristForKontrollsamtaleContext
import no.nav.su.se.bakover.kontrollsamtale.infrastructure.persistence.UtløptFristForKontrollsamtaleDb.FeiletDb.Companion.toDb
import no.nav.su.se.bakover.kontrollsamtale.infrastructure.persistence.UtløptFristForKontrollsamtaleDb.FeiletDb.Companion.toDomain
import java.time.LocalDate
import java.util.UUID

data class UtløptFristForKontrollsamtaleDb(
    val id: String,
    val jobName: String,
    val dato: String,
    val opprettet: Tidspunkt,
    val endret: Tidspunkt,
    val prosessert: Set<UUID>,
    val møtt: Set<UUID>,
    val ikkeMøtt: Set<UUID>,
    val feilet: Set<FeiletDb>,
) : JobContextDb {
    override fun id(): String {
        return id
    }
    override fun toJson(): String {
        return serialize(this)
    }

    override fun toDomain(): UtløptFristForKontrollsamtaleContext {
        return UtløptFristForKontrollsamtaleContext(
            id = NameAndLocalDateId(
                name = jobName,
                date = LocalDate.parse(dato),
            ),
            opprettet = opprettet,
            endret = endret,
            prosessert = prosessert,
            ikkeMøtt = ikkeMøtt,
            feilet = feilet.toDomain(),
        )
    }

    companion object {
        fun UtløptFristForKontrollsamtaleContext.toDb(): UtløptFristForKontrollsamtaleDb {
            return UtløptFristForKontrollsamtaleDb(
                id = id().value(),
                jobName = id().name,
                dato = id().date.toString(),
                opprettet = opprettet(),
                endret = endret(),
                prosessert = prosessert(),
                møtt = møtt(),
                ikkeMøtt = ikkeMøtt(),
                feilet = feilet().toDb(),
            )
        }

        fun fromDbJson(json: String): UtløptFristForKontrollsamtaleContext {
            return deserialize<UtløptFristForKontrollsamtaleDb>(json).toDomain()
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
}
