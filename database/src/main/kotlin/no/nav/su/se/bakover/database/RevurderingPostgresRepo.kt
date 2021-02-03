package no.nav.su.se.bakover.database

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.database.beregning.PersistertBeregning
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.behandling.BeregnetRevurdering
import no.nav.su.se.bakover.domain.behandling.OpprettetRevurdering
import no.nav.su.se.bakover.domain.behandling.Revurdering
import no.nav.su.se.bakover.domain.behandling.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.behandling.SimulertRevurdering
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.util.UUID
import javax.sql.DataSource

interface RevurderingRepo {
    fun hent(id: UUID): Revurdering?
    fun lagre(revurdering: Revurdering)
}

enum class RevurderingsType {
    OPPRETTET,
    BEREGNET,
    SIMULERT,
    TIL_ATTESTERING,
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
            is RevurderingTilAttestering -> lagre(revurdering)
        }
    }

    fun Row.toRevurdering(): Revurdering {
        val id = uuid("id")
        val periode = string("periode").let { objectMapper.readValue<Periode>(it) }
        val opprettet = tidspunkt("opprettet")
        val tilRevurdering = behandlingRepo.hentBehandling(uuid("behandlingId"))!!
        val beregning = stringOrNull("beregning")?.let { objectMapper.readValue<PersistertBeregning>(it) }
        val simulering = stringOrNull("simulering")?.let { objectMapper.readValue<Simulering>(it) }
        val saksbehandler = string("saksbehandler")
        val oppgaveId = stringOrNull("oppgaveId")

        return when (RevurderingsType.valueOf(string("revurderingsType"))) {
            RevurderingsType.TIL_ATTESTERING -> RevurderingTilAttestering(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                beregning = beregning!!,
                simulering = simulering!!,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!)
            )
            RevurderingsType.SIMULERT -> SimulertRevurdering(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                beregning = beregning!!,
                simulering = simulering!!,
                saksbehandler = Saksbehandler(saksbehandler)
            )
            RevurderingsType.BEREGNET -> BeregnetRevurdering(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering, beregning = beregning!!, saksbehandler = Saksbehandler(saksbehandler)
            )
            RevurderingsType.OPPRETTET -> OpprettetRevurdering(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = Saksbehandler(saksbehandler)
            )
        }
    }

    private fun lagre(revurdering: OpprettetRevurdering) =
        dataSource.withSession { session ->
            (
                """
                    insert into revurdering
                        (id, opprettet, behandlingId, periode, beregning, simulering, saksbehandler, oppgaveId, revurderingsType)
                    values
                        (:id, :opprettet, :behandlingId, to_json(:periode::json), null, null, :saksbehandler, :oppgaveId, :revurderingsType)
                """.trimIndent()
                ).oppdatering(
                mapOf(
                    "id" to revurdering.id,
                    "periode" to objectMapper.writeValueAsString(revurdering.periode),
                    "opprettet" to revurdering.opprettet,
                    "behandlingId" to revurdering.tilRevurdering.id,
                    "saksbehandler" to revurdering.saksbehandler.navIdent,
                    "revurderingsType" to RevurderingsType.OPPRETTET.toString()
                ),
                session
            )
        }

    private fun lagre(revurdering: BeregnetRevurdering) =
        dataSource.withSession { session ->
            (
                """
                    update 
                        revurdering 
                    set 
                        beregning = to_json(:beregning::json), 
                        simulering = null, 
                        revurderingsType = :revurderingsType,
                        saksbehandler = :saksbehandler
                    where 
                        id = :id
                """.trimIndent()
                ).oppdatering(
                mapOf(
                    "id" to revurdering.id,
                    "saksbehandler" to revurdering.saksbehandler.navIdent,
                    "beregning" to objectMapper.writeValueAsString(revurdering.beregning),
                    "revurderingsType" to RevurderingsType.BEREGNET.toString()
                ),
                session
            )
        }

    private fun lagre(revurdering: SimulertRevurdering) =
        dataSource.withSession { session ->
            (
                """
                    update 
                        revurdering 
                    set 
                        saksbehandler = :saksbehandler,
                        beregning = to_json(:beregning::json), 
                        simulering = to_json(:simulering::json), 
                        revurderingsType = :revurderingsType 
                    where 
                        id = :id
                """.trimIndent()
                ).oppdatering(
                mapOf(
                    "id" to revurdering.id,
                    "saksbehandler" to revurdering.saksbehandler.navIdent,
                    "beregning" to objectMapper.writeValueAsString(revurdering.beregning),
                    "simulering" to objectMapper.writeValueAsString(revurdering.simulering),
                    "revurderingsType" to RevurderingsType.SIMULERT.toString()
                ),
                session
            )
        }

    private fun lagre(revurdering: RevurderingTilAttestering) =
        dataSource.withSession { session ->
            (
                """
                    update 
                        revurdering 
                    set 
                        saksbehandler = :saksbehandler,
                        beregning = to_json(:beregning::json), 
                        simulering = to_json(:simulering::json), 
                        revurderingsType = :revurderingsType, 
                        oppgaveId = :oppgaveId
                    where 
                        id = :id
                """.trimIndent()
                ).oppdatering(
                mapOf(
                    "id" to revurdering.id,
                    "saksbehandler" to revurdering.saksbehandler.navIdent,
                    "beregning" to objectMapper.writeValueAsString(revurdering.beregning),
                    "simulering" to objectMapper.writeValueAsString(revurdering.simulering),
                    "oppgaveId" to revurdering.oppgaveId.toString(),
                    "revurderingsType" to RevurderingsType.TIL_ATTESTERING.toString()
                ),
                session
            )
        }
}
