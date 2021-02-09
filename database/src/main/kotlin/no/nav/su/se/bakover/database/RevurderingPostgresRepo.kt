package no.nav.su.se.bakover.database

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.beregning.PersistertBeregning
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import java.util.UUID
import javax.sql.DataSource

interface RevurderingRepo {
    fun hent(id: UUID): Revurdering?
    fun hentRevurderingForBehandling(behandlingId: UUID): Revurdering?
    fun hentRevurderingForUtbetaling(utbetalingId: UUID30): IverksattRevurdering?
    fun lagre(revurdering: Revurdering)
}

enum class RevurderingsType {
    OPPRETTET,
    BEREGNET,
    SIMULERT,
    TIL_ATTESTERING,
    IVERKSATT,
}

internal class RevurderingPostgresRepo(
    private val dataSource: DataSource,
    internal val søknadsbehandlingRepo: SøknadsbehandlingRepo
) : RevurderingRepo {
    override fun hent(id: UUID): Revurdering? =
        dataSource.withSession { session ->
            "select * from revurdering where id = :id"
                .hent(mapOf("id" to id), session) { row ->
                    row.toRevurdering()
                }
        }

    override fun hentRevurderingForBehandling(behandlingId: UUID): Revurdering? =
        dataSource.withSession { session ->
            "select * from revurdering where behandlingId = :behandlingId"
                .hent(mapOf("behandlingId" to behandlingId), session) {
                    it.toRevurdering()
                }
        }

    override fun hentRevurderingForUtbetaling(utbetalingId: UUID30): IverksattRevurdering? =
        dataSource.withSession { session ->
            "select * from revurdering where utbetalingId = :utbetalingId"
                .hent(mapOf("utbetalingId" to utbetalingId), session) {
                    when (val revurdering = it.toRevurdering()) {
                        is IverksattRevurdering -> revurdering
                        else -> null
                    }
                }
        }

    override fun lagre(revurdering: Revurdering) {
        when (revurdering) {
            is OpprettetRevurdering -> lagre(revurdering)
            is BeregnetRevurdering -> lagre(revurdering)
            is SimulertRevurdering -> lagre(revurdering)
            is RevurderingTilAttestering -> lagre(revurdering)
            is IverksattRevurdering -> lagre(revurdering)
        }
    }

    internal fun hentRevurderingerForSak(sakId: UUID, session: Session): List<Revurdering> =
        "select r.*, b.sakid from revurdering r inner join behandling b on r.behandlingid = b.id where b.sakid=:sakId"
            .hentListe(mapOf("sakId" to sakId), session) {
                it.toRevurdering()
            }

    fun Row.toRevurdering(): Revurdering {
        val id = uuid("id")
        val periode = string("periode").let { objectMapper.readValue<Periode>(it) }
        val opprettet = tidspunkt("opprettet")
        val tilRevurdering = søknadsbehandlingRepo.hent(uuid("behandlingId"))!!
        val beregning = stringOrNull("beregning")?.let { objectMapper.readValue<PersistertBeregning>(it) }
        val simulering = stringOrNull("simulering")?.let { objectMapper.readValue<Simulering>(it) }
        val saksbehandler = string("saksbehandler")
        val oppgaveId = stringOrNull("oppgaveId")
        val attestant = stringOrNull("attestant")
        val utbetalingId = stringOrNull("utbetalingId")

        return when (RevurderingsType.valueOf(string("revurderingsType"))) {
            RevurderingsType.IVERKSATT -> IverksattRevurdering(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering as Søknadsbehandling.Iverksatt.Innvilget, // TODO AVOID CAST
                saksbehandler = Saksbehandler(saksbehandler),
                beregning = beregning!!,
                simulering = simulering!!,
                oppgaveId = OppgaveId(oppgaveId!!),
                attestant = NavIdentBruker.Attestant(attestant!!),
                utbetalingId = UUID30.fromString(utbetalingId!!)
            )
            RevurderingsType.TIL_ATTESTERING -> RevurderingTilAttestering(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering as Søknadsbehandling.Iverksatt.Innvilget, // TODO AVOID CAST
                beregning = beregning!!,
                simulering = simulering!!,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!)
            )
            RevurderingsType.SIMULERT -> SimulertRevurdering(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering as Søknadsbehandling.Iverksatt.Innvilget, // TODO AVOID CAST,
                beregning = beregning!!,
                simulering = simulering!!,
                saksbehandler = Saksbehandler(saksbehandler)
            )
            RevurderingsType.BEREGNET -> BeregnetRevurdering(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering as Søknadsbehandling.Iverksatt.Innvilget, // TODO AVOID CAST,
                beregning = beregning!!,
                saksbehandler = Saksbehandler(saksbehandler)
            )
            RevurderingsType.OPPRETTET -> OpprettetRevurdering(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering as Søknadsbehandling.Iverksatt.Innvilget, // TODO AVOID CAST,
                saksbehandler = Saksbehandler(saksbehandler)
            )
        }
    }

    private fun lagre(revurdering: OpprettetRevurdering) =
        dataSource.withSession { session ->
            (
                """
                    insert into revurdering
                        (id, opprettet, behandlingId, periode, beregning, simulering, saksbehandler, oppgaveId, revurderingsType, attestant, utbetalingId)
                    values
                        (:id, :opprettet, :behandlingId, to_json(:periode::json), null, null, :saksbehandler, :oppgaveId, :revurderingsType, null, null)
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

    private fun lagre(revurdering: IverksattRevurdering) =
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
                        oppgaveId = :oppgaveId,
                        attestant = :attestant,
                        utbetalingId = :utbetalingId
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
                    "revurderingsType" to RevurderingsType.IVERKSATT.toString(),
                    "attestant" to revurdering.attestant.navIdent,
                    "utbetalingId" to revurdering.utbetalingId
                ),
                session
            )
        }
}
