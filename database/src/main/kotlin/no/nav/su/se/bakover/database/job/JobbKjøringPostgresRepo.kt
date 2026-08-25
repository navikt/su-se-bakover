package no.nav.su.se.bakover.database.job

import no.nav.su.se.bakover.common.domain.job.JobbKjøring
import no.nav.su.se.bakover.common.domain.job.JobbKjøringRepo
import no.nav.su.se.bakover.common.domain.job.JobbKjøringStatus
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import java.time.Duration

class JobbKjøringPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
) : JobbKjøringRepo {

    override fun lagre(jobbKjøring: JobbKjøring) {
        sessionFactory.withSession { session ->
            """
                insert into jobb_kjoring (id, jobb_navn, status, startet_tidspunkt, ferdig_tidspunkt, feilmelding, intervall_sekunder)
                values (:id, :jobbNavn, :status, :startetTidspunkt, :ferdigTidspunkt, :feilmelding, :intervallSekunder)
            """.trimIndent().insert(
                mapOf(
                    "id" to jobbKjøring.id,
                    "jobbNavn" to jobbKjøring.jobbNavn,
                    "status" to jobbKjøring.status.name,
                    "startetTidspunkt" to jobbKjøring.startetTidspunkt,
                    "ferdigTidspunkt" to jobbKjøring.ferdigTidspunkt,
                    "feilmelding" to jobbKjøring.feilmelding,
                    "intervallSekunder" to jobbKjøring.intervall.seconds,
                ),
                session,
            )
        }
    }

    override fun oppdater(jobbKjøring: JobbKjøring) {
        sessionFactory.withSession { session ->
            """
                update jobb_kjoring
                set status = :status,
                    ferdig_tidspunkt = :ferdigTidspunkt,
                    feilmelding = :feilmelding
                where id = :id
            """.trimIndent().oppdatering(
                mapOf(
                    "id" to jobbKjøring.id,
                    "status" to jobbKjøring.status.name,
                    "ferdigTidspunkt" to jobbKjøring.ferdigTidspunkt,
                    "feilmelding" to jobbKjøring.feilmelding,
                ),
                session,
            )
        }
    }

    override fun hentSistePerJobb(): List<JobbKjøring> {
        return sessionFactory.withSession { session ->
            """
                select distinct on (jobb_navn) *
                from jobb_kjoring
                order by jobb_navn, startet_tidspunkt desc
            """.trimIndent().hentListe(
                emptyMap(),
                session,
            ) { row ->
                JobbKjøring(
                    id = row.uuid("id"),
                    jobbNavn = row.string("jobb_navn"),
                    status = JobbKjøringStatus.valueOf(row.string("status")),
                    startetTidspunkt = row.instant("startet_tidspunkt"),
                    ferdigTidspunkt = row.instantOrNull("ferdig_tidspunkt"),
                    feilmelding = row.stringOrNull("feilmelding"),
                    intervall = Duration.ofSeconds(row.long("intervall_sekunder")),
                )
            }
        }
    }
}
