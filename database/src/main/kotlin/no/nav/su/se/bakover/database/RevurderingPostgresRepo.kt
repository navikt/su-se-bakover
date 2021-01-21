package no.nav.su.se.bakover.database

import no.nav.su.se.bakover.domain.behandling.Revurdering
import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.database.beregning.PersistertBeregning
import no.nav.su.se.bakover.domain.behandling.BeregnetRevurdering
import no.nav.su.se.bakover.domain.behandling.OpprettetRevurdering
import no.nav.su.se.bakover.domain.behandling.SimulertRevurdering
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
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
            else -> throw RuntimeException("UKJENT REVURDERING KANKE LAGRE")
        }
    }

    fun Row.toRevurdering(): Revurdering {
        val id = uuid("id")
        val opprettet = tidspunkt("opprettet")
        val tilRevurdering = behandlingRepo.hentBehandling(uuid("behandlingId"))!!
        val beregning = stringOrNull("beregning")?.let { objectMapper.readValue<PersistertBeregning>(it) }
        val simulering = stringOrNull("simulering")?.let { objectMapper.readValue<Simulering>(it) }
        return when {
            simulering != null && beregning != null -> SimulertRevurdering(id, opprettet, tilRevurdering, beregning, simulering)
            beregning != null -> BeregnetRevurdering(id, opprettet, tilRevurdering, beregning)
            else -> OpprettetRevurdering(id, opprettet, tilRevurdering)
        }
    }

    private fun lagre(revurdering: OpprettetRevurdering) =
        dataSource.withSession { session ->
            (
                "insert into revurdering (id, opprettet, behandlingId, beregning, simulering) values (:id, :opprettet, :behandlingId, null, null) on conflict (id) do " +
                    "update set opprettet = :opprettet, behandlingId = :behandlingId, beregning = null, simulering = null"
                ).oppdatering(
                mapOf(
                    "id" to revurdering.id,
                    "opprettet" to revurdering.opprettet,
                    "behandlingId" to revurdering.tilRevurdering.id
                ),
                session
            )
        }

    private fun lagre(revurdering: BeregnetRevurdering) =
        dataSource.withSession { session ->
            (
                "insert into revurdering (id, opprettet, behandlingId, beregning, simulering) values (:id, :opprettet, :behandlingId, to_json(:beregning::json), null) on conflict (id) do " +
                    "update set opprettet = :opprettet, behandlingId = :behandlingId, beregning = to_json(:beregning::json), simulering = null"
                ).oppdatering(
                mapOf(
                    "id" to revurdering.id,
                    "opprettet" to revurdering.opprettet,
                    "behandlingId" to revurdering.tilRevurdering.id,
                    "beregning" to objectMapper.writeValueAsString(revurdering.beregning)
                ),
                session
            )
        }

    private fun lagre(revurdering: SimulertRevurdering) =
        dataSource.withSession { session ->
            (
                "insert into revurdering (id, opprettet, behandlingId, beregning, simulering) values (:id, :opprettet, :behandlingId, to_json(:beregning::json), to_json(:simulering::json)) on conflict (id) do " +
                    "update set opprettet = :opprettet, behandlingId = :behandlingId, beregning = to_json(:beregning::json), simulering = to_json(:simulering::json)"
                ).oppdatering(
                mapOf(
                    "id" to revurdering.id,
                    "opprettet" to revurdering.opprettet,
                    "behandlingId" to revurdering.tilRevurdering.id,
                    "beregning" to objectMapper.writeValueAsString(revurdering.beregning),
                    "simulering" to objectMapper.writeValueAsString(revurdering.simulering)
                ),
                session
            )
        }
}
