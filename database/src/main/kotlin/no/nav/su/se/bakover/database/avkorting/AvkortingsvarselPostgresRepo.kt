package no.nav.su.se.bakover.database.avkorting

import kotliquery.Row
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serializeNullable
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.PostgresSessionFactory
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.TransactionalSession
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.avkorting.AvkortingsvarselRepo
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import java.util.UUID

internal class AvkortingsvarselPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : AvkortingsvarselRepo {

    enum class Status {
        SKAL_AVKORTES,
        AVKORTET,
        ANNULLERT,
    }

    internal fun lagre(avkortingsvarsel: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes, tx: TransactionalSession) {
        insert(
            id = avkortingsvarsel.id,
            opprettet = avkortingsvarsel.opprettet,
            sakId = avkortingsvarsel.sakId,
            revurderingId = avkortingsvarsel.revurderingId,
            simulering = avkortingsvarsel.simulering,
            tx = tx,
        )
    }

    internal fun lagre(avkortingsvarsel: Avkortingsvarsel.Utenlandsopphold.Avkortet, tx: TransactionalSession) {
        hent(id = avkortingsvarsel.id, session = tx).also {
            check(it is Avkortingsvarsel.Utenlandsopphold.SkalAvkortes) { "Avkortingsvarsel ${avkortingsvarsel.id} er i ugyldig tilstand for å kunne oppdateres" }
        }
        oppdater(
            id = avkortingsvarsel.id,
            status = Status.AVKORTET,
            behandlingId = avkortingsvarsel.behandlingId,
            tx = tx,
        )
    }

    internal fun lagre(avkortingsvarsel: Avkortingsvarsel.Utenlandsopphold.Annullert, tx: TransactionalSession) {
        hent(id = avkortingsvarsel.id, session = tx).also {
            check(it is Avkortingsvarsel.Utenlandsopphold.SkalAvkortes) { "Avkortingsvarsel ${avkortingsvarsel.id} er i ugyldig tilstand for å kunne oppdateres" }
        }
        oppdater(
            id = avkortingsvarsel.id,
            status = Status.ANNULLERT,
            behandlingId = avkortingsvarsel.behandlingId,
            tx = tx,
        )
    }

    private fun insert(
        id: UUID,
        opprettet: Tidspunkt,
        sakId: UUID,
        revurderingId: UUID,
        simulering: Simulering?,
        tx: TransactionalSession,
    ) {
        dbMetrics.timeQuery("insertAvkortingsvarsel") {
            """insert into avkortingsvarsel (
            id,
            opprettet,
            sakId,
            revurderingId,
            simulering,
            status
            ) values (
                :id,
                :opprettet,
                :sakId,
                :revurderingId,
                to_jsonb(:simulering::json),
                :status
             )
            """.trimMargin()
                .insert(
                    mapOf(
                        "id" to id,
                        "opprettet" to opprettet,
                        "sakId" to sakId,
                        "revurderingId" to revurderingId,
                        "simulering" to serializeNullable(simulering),
                        "status" to Status.SKAL_AVKORTES.toString(),
                    ),
                    tx,
                )
        }
    }

    private fun oppdater(
        id: UUID,
        status: Status,
        behandlingId: UUID?,
        tx: TransactionalSession,
    ) {
        dbMetrics.timeQuery("oppdaterAvkortingsvarsel") {
            """
            update avkortingsvarsel set
                status = :status,
                behandlingId = :behandlingId
            where id = :id
            """.trimIndent()
                .oppdatering(
                    mapOf(
                        "id" to id,
                        "status" to status.toString(),
                        "behandlingId" to behandlingId,
                    ),
                    tx,
                )
        }
    }

    override fun hentUtestående(sakId: UUID): Avkortingsvarsel {
        return sessionFactory.withSession { session ->
            hentUteståendeAvkorting(
                sakId = sakId,
                session = session,
            )
        }
    }

    internal fun hentUteståendeAvkorting(
        sakId: UUID,
        session: Session,
    ): Avkortingsvarsel {
        return dbMetrics.timeQuery("hentUteståendeAvkorting") {
            """select * from avkortingsvarsel where sakid = :sakid and status = :status""".hentListe(
                mapOf(
                    "sakid" to sakId,
                    "status" to "${Status.SKAL_AVKORTES}",
                ),
                session,
            ) {
                it.toAvkortingsvarsel() as Avkortingsvarsel.Utenlandsopphold.SkalAvkortes
            }.let {
                when (it.size) {
                    0 -> Avkortingsvarsel.Ingen
                    1 -> it.first()
                    else -> throw IllegalStateException("Skal maksimalt kunne ha 1 utestående avkorting")
                }
            }
        }
    }

    override fun hent(id: UUID): Avkortingsvarsel? {
        return sessionFactory.withSession { session ->
            hent(
                id = id,
                session = session,
            )
        }
    }

    fun hent(id: UUID, session: Session): Avkortingsvarsel? {
        return dbMetrics.timeQuery("hentAvkortingsvarsel") {
            """select * from avkortingsvarsel where id = :id""".hent(
                mapOf(
                    "id" to id,
                ),
                session,
            ) {
                it.toAvkortingsvarsel()
            }
        }
    }

    private fun Row.toAvkortingsvarsel(): Avkortingsvarsel.Utenlandsopphold {
        val opprettet = Avkortingsvarsel.Utenlandsopphold.Opprettet(
            id = uuid("id"),
            sakId = uuid("sakId"),
            revurderingId = uuid("revurderingId"),
            opprettet = tidspunkt("opprettet"),
            simulering = deserialize(string("simulering")),
        )
        return when (Status.valueOf(string("status"))) {
            Status.SKAL_AVKORTES -> {
                opprettet.skalAvkortes()
            }
            Status.AVKORTET -> {
                opprettet.skalAvkortes().avkortet(uuid("behandlingId"))
            }
            Status.ANNULLERT -> {
                opprettet.skalAvkortes().annuller(uuid("behandlingId"))
            }
        }
    }
}
