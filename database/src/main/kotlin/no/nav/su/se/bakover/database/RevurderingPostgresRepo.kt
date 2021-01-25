package no.nav.su.se.bakover.database

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.database.beregning.PersistertBeregning
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.behandling.BeregnetRevurdering
import no.nav.su.se.bakover.domain.behandling.OpprettetRevurdering
import no.nav.su.se.bakover.domain.behandling.Revurdering
import no.nav.su.se.bakover.domain.behandling.SimulertRevurdering
import no.nav.su.se.bakover.domain.behandling.TilAttesteringRevurdering
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.util.UUID
import javax.sql.DataSource

interface RevurderingRepo {
    fun hent(id: UUID): Revurdering?
    fun lagre(revurdering: Revurdering)
}

internal class RevurderingPostgresRepo(
    private val dataSource: DataSource,
    private val behandlingRepo: BehandlingRepo
) : RevurderingRepo {
    override fun hent(id: UUID): Revurdering? =
        dataSource.withSession { session ->
            "select * from revurdering where id = :id"
                .hent(mapOf("id" to id), session) { row ->
                    row.toRevurdering()
                }
        }

    override fun lagre(revurdering: Revurdering) {
        when (revurdering) {
            is OpprettetRevurdering -> lagre(revurdering)
            is BeregnetRevurdering -> lagre(revurdering)
            is SimulertRevurdering -> lagre(revurdering)
            is TilAttesteringRevurdering -> lagre(revurdering)
            else -> throw RuntimeException("UKJENT REVURDERING KANKE LAGRE")
        }
    }

    fun Row.toRevurdering(): Revurdering {
        val id = uuid("id")
        val opprettet = tidspunkt("opprettet")
        val tilRevurdering = behandlingRepo.hentBehandling(uuid("behandlingId"))!!
        val beregning = stringOrNull("beregning")?.let { objectMapper.readValue<PersistertBeregning>(it) }
        val simulering = stringOrNull("simulering")?.let { objectMapper.readValue<Simulering>(it) }
        val saksbehandler = string("saksbehandler")
        val oppgaveId = stringOrNull("oppgaveId")
        return when {
            oppgaveId != null && simulering != null && beregning != null -> TilAttesteringRevurdering(
                id = id,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                beregning = beregning,
                simulering = simulering,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId)
            )
            simulering != null && beregning != null -> SimulertRevurdering(
                id = id,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                beregning = beregning,
                simulering = simulering,
                saksbehandler = Saksbehandler(saksbehandler)
            )
            beregning != null -> BeregnetRevurdering(
                id = id,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering, beregning = beregning, saksbehandler = Saksbehandler(saksbehandler)
            )
            else -> OpprettetRevurdering(
                id = id,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = Saksbehandler(saksbehandler)
            )
        }
    }

    private fun lagre(revurdering: OpprettetRevurdering) =
        dataSource.withSession { session ->
            (
                "insert into revurdering (id, opprettet, behandlingId, beregning, simulering, saksbehandler, oppgaveId) values (:id, :opprettet, :behandlingId, null, null, :saksbehandler, :oppgaveId) on conflict (id) do " +
                    "update set beregning = null, simulering = null"
                ).oppdatering(
                mapOf(
                    "id" to revurdering.id,
                    "opprettet" to revurdering.opprettet,
                    "behandlingId" to revurdering.tilRevurdering.id,
                    "saksbehandler" to revurdering.saksbehandler.navIdent
                ),
                session
            )
        }

    private fun lagre(revurdering: BeregnetRevurdering) =
        dataSource.withSession { session ->
            (
                "insert into revurdering (id, opprettet, behandlingId, beregning, simulering, saksbehandler, oppgaveId) values (:id, :opprettet, :behandlingId, to_json(:beregning::json), null, :saksbehandler, :oppgaveId) on conflict (id) do " +
                    "update set beregning = to_json(:beregning::json), simulering = null"
                ).oppdatering(
                mapOf(
                    "id" to revurdering.id,
                    "opprettet" to revurdering.opprettet,
                    "behandlingId" to revurdering.tilRevurdering.id,
                    "beregning" to objectMapper.writeValueAsString(revurdering.beregning),
                    "saksbehandler" to revurdering.saksbehandler.navIdent
                ),
                session
            )
        }

    private fun lagre(revurdering: SimulertRevurdering) =
        dataSource.withSession { session ->
            (
                "insert into revurdering (id, opprettet, behandlingId, beregning, simulering, saksbehandler, oppgaveId) values (:id, :opprettet, :behandlingId, to_json(:beregning::json), to_json(:simulering::json), :saksbehandler, :oppgaveId) on conflict (id) do " +
                    "update set beregning = to_json(:beregning::json), simulering = to_json(:simulering::json)"
                ).oppdatering(
                mapOf(
                    "id" to revurdering.id,
                    "opprettet" to revurdering.opprettet,
                    "behandlingId" to revurdering.tilRevurdering.id,
                    "beregning" to objectMapper.writeValueAsString(revurdering.beregning),
                    "simulering" to objectMapper.writeValueAsString(revurdering.simulering),
                    "saksbehandler" to revurdering.saksbehandler.navIdent
                ),
                session
            )
        }

    private fun lagre(revurdering: TilAttesteringRevurdering) =
        dataSource.withSession { session ->
            (
                "insert into revurdering (id, opprettet, behandlingId, beregning, simulering, saksbehandler, oppgaveId) values (:id, :opprettet, :behandlingId, to_json(:beregning::json), to_json(:simulering::json), :saksbehandler, :oppgaveId) on conflict (id) do " +
                    "update set beregning = to_json(:beregning::json), simulering = to_json(:simulering::json), oppgaveId = :oppgaveId"
                ).oppdatering(
                mapOf(
                    "id" to revurdering.id,
                    "opprettet" to revurdering.opprettet,
                    "behandlingId" to revurdering.tilRevurdering.id,
                    "beregning" to objectMapper.writeValueAsString(revurdering.beregning),
                    "simulering" to objectMapper.writeValueAsString(revurdering.simulering),
                    "saksbehandler" to revurdering.saksbehandler.navIdent,
                    "oppgaveId" to revurdering.oppgaveId.toString()
                ),
                session
            )
        }
}
