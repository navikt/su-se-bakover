package no.nav.su.se.bakover.database

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import java.util.UUID

internal class AvkortingsvarselPostgresRepo {

    enum class Status {
        OPPRETTET,
        SKAL_AVKORTES,
        AVKORTET
    }

    private fun lagre(avkortingsvarsel: Avkortingsvarsel.Utenlandsopphold.SkalAvkortes, tx: TransactionalSession) {
        oppdater(
            id = avkortingsvarsel.id,
            status = Status.SKAL_AVKORTES,
            søknadsbehandlingId = null,
            tx = tx,
        )
    }

    internal fun lagre(avkortingsvarsel: Avkortingsvarsel.Utenlandsopphold.Avkortet, tx: TransactionalSession) {
        oppdater(
            id = avkortingsvarsel.id,
            status = Status.AVKORTET,
            søknadsbehandlingId = avkortingsvarsel.søknadsbehandlingId,
            tx = tx,
        )
    }

    fun lagre(revurderingId: UUID, avkortingsvarsel: Avkortingsvarsel, tx: TransactionalSession) {
        when (avkortingsvarsel) {
            is Avkortingsvarsel.Ingen -> {
                slettForRevurdering(revurderingId, tx)
            }
            is Avkortingsvarsel.Utenlandsopphold.Avkortet -> {
                lagre(avkortingsvarsel, tx)
            }
            is Avkortingsvarsel.Utenlandsopphold.Opprettet -> {
                slettForRevurdering(revurderingId, tx)
                insert(
                    id = avkortingsvarsel.id,
                    opprettet = avkortingsvarsel.opprettet,
                    sakId = avkortingsvarsel.sakId,
                    revurderingId = avkortingsvarsel.revurderingId,
                    simulering = avkortingsvarsel.simulering,
                    feilutbetalingslinje = avkortingsvarsel.feilutbetalingslinje,
                    tx = tx,
                )
            }
            is Avkortingsvarsel.Utenlandsopphold.SkalAvkortes -> {
                lagre(avkortingsvarsel, tx)
            }
        }
    }

    private fun insert(
        id: UUID,
        opprettet: Tidspunkt,
        sakId: UUID,
        revurderingId: UUID,
        simulering: Simulering?,
        feilutbetalingslinje: Avkortingsvarsel.Utenlandsopphold.Feilutbetalingslinje?,
        tx: TransactionalSession,
    ) {
        """insert into avkortingsvarsel (
            id, 
            opprettet, 
            sakId, 
            revurderingId,
            simulering, 
            feilutbetalingslinje,
            status
            ) values (
                :id, 
                :opprettet, 
                :sakId, 
                :revurderingId,
                to_jsonb(:simulering::json), 
                to_jsonb(:feilutbetalingslinje::json),
                :status
             )""".trimMargin()
            .insert(
                mapOf(
                    "id" to id,
                    "opprettet" to opprettet,
                    "sakId" to sakId,
                    "revurderingId" to revurderingId,
                    "simulering" to simulering?.let { objectMapper.writeValueAsString(it) },
                    "feilutbetalingslinje" to feilutbetalingslinje?.let { objectMapper.writeValueAsString(it) },
                    "status" to Status.OPPRETTET.toString(),
                ),
                tx,
            )
    }

    private fun oppdater(
        id: UUID,
        status: Status,
        søknadsbehandlingId: UUID?,
        tx: TransactionalSession,
    ) {
        """
            update avkortingsvarsel set
                status = :status,
                søknadsbehandlingId = :soknadsbehandlingId
            where id = :id
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "id" to id,
                    "status" to status.toString(),
                    "soknadsbehandlingId" to søknadsbehandlingId,
                ),
                tx,
            )
    }

    fun hentUteståendeAvkorting(
        sakId: UUID,
        session: Session,
    ): Avkortingsvarsel {
        return """select * from avkortingsvarsel where sakid = :sakid and status = :status""".hentListe(
            mapOf(
                "sakid" to sakId,
                "status" to "${Status.SKAL_AVKORTES}",
            ),
            session,
        ) {
            it.toAvkortingsvarsel() as Avkortingsvarsel.Utenlandsopphold.SkalAvkortes
        }.singleOrNull() ?: Avkortingsvarsel.Ingen // skal maksimalt kunne ha 1 utestående til enhver tid
    }

    private fun slettForRevurdering(revurderingId: UUID, tx: TransactionalSession) {
        """delete from avkortingsvarsel where revurderingId = :revurderingId""".oppdatering(
            mapOf(
                "revurderingId" to revurderingId,
            ),
            tx,
        )
    }

    fun hentForRevurdering(revurderingId: UUID, session: Session): Avkortingsvarsel {
        return """select * from avkortingsvarsel where revurderingId = :revurderingId""".hent(
            mapOf(
                "revurderingId" to revurderingId,
            ),
            session,
        ) {
            it.toAvkortingsvarsel()
        } ?: Avkortingsvarsel.Ingen
    }

    private fun Row.toAvkortingsvarsel(): Avkortingsvarsel.Utenlandsopphold {
        val opprettet = Avkortingsvarsel.Utenlandsopphold.Opprettet(
            id = uuid("id"),
            sakId = uuid("sakId"),
            revurderingId = uuid("revurderingId"),
            opprettet = tidspunkt("opprettet"),
            simulering = string("simulering").let { objectMapper.readValue(it) },
            feilutbetalingslinje = string("feilutbetalingslinje").let { objectMapper.readValue(it) },
        )
        return when (Status.valueOf(string("status"))) {
            Status.OPPRETTET -> opprettet
            Status.SKAL_AVKORTES -> opprettet.skalAvkortes()
            Status.AVKORTET -> opprettet.skalAvkortes().avkortet(uuid("søknadsbehandlingId"))
        }
    }

    fun hentFullførtAvkorting(søknadsbehandlingId: UUID, session: Session): Avkortingsvarsel {
        return """select * from avkortingsvarsel where søknadsbehandlingId = :id""".hent(
            mapOf(
                "id" to søknadsbehandlingId,
            ),
            session,
        ) {
            it.toAvkortingsvarsel()
        } ?: Avkortingsvarsel.Ingen
    }
}
