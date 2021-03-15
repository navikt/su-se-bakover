package no.nav.su.se.bakover.database.revurdering

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.beregning.PersistertBeregning
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.database.vedtak.VedtakPosgresRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import java.util.UUID
import javax.sql.DataSource

interface RevurderingRepo {
    fun hent(id: UUID): Revurdering?
    fun hent(id: UUID, session: Session): Revurdering?
    fun hentRevurderingForUtbetaling(utbetalingId: UUID30): IverksattRevurdering?
    fun hentEventuellTidligereAttestering(id: UUID): Attestering?
    fun lagre(revurdering: Revurdering)
}

enum class RevurderingsType {
    OPPRETTET,
    BEREGNET_INNVILGET,
    BEREGNET_AVSLAG,
    SIMULERT,
    TIL_ATTESTERING,
    IVERKSATT,
    UNDERKJENT,
}

internal class RevurderingPostgresRepo(
    private val dataSource: DataSource,
    internal val søknadsbehandlingRepo: SøknadsbehandlingRepo,
) : RevurderingRepo {
    private val vedtakRepo: VedtakRepo = VedtakPosgresRepo(dataSource, søknadsbehandlingRepo, this)

    override fun hent(id: UUID): Revurdering? =
        dataSource.withSession { session ->
            hent(id, session)
        }

    override fun hent(id: UUID, session: Session): Revurdering? =
        dataSource.withSession(session) { s ->
            """
                SELECT *
                FROM revurdering
                WHERE id = :id
            """.trimIndent()
                .hent(mapOf("id" to id), s) { row ->
                    row.toRevurdering(s)
                }
        }

    override fun hentRevurderingForUtbetaling(utbetalingId: UUID30): IverksattRevurdering? =
        dataSource.withSession { session ->
            "select * from revurdering where utbetalingId = :utbetalingId"
                .hent(mapOf("utbetalingId" to utbetalingId), session) {
                    it.toRevurdering(session) as? IverksattRevurdering
                }
        }

    override fun hentEventuellTidligereAttestering(id: UUID): Attestering? =
        dataSource.withSession { session ->
            "select * from revurdering where id = :id"
                .hent(mapOf("id" to id), session) { row ->
                    row.stringOrNull("attestering")?.let {
                        objectMapper.readValue(it)
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
            is UnderkjentRevurdering -> lagre(revurdering)
        }
    }

    internal fun hentRevurderingerForSak(sakId: UUID, session: Session): List<Revurdering> =
        """
            SELECT
                r.*
            FROM
                revurdering r
                INNER JOIN behandling_vedtak bv
                    ON r.vedtakSomRevurderesId = bv.vedtakId
            WHERE bv.sakid=:sakId
        """.trimIndent()
            .hentListe(mapOf("sakId" to sakId), session) {
                it.toRevurdering(session)
            }

    private fun Row.toRevurdering(session: Session): Revurdering {
        val id = uuid("id")
        val periode = string("periode").let { objectMapper.readValue<Periode>(it) }
        val opprettet = tidspunkt("opprettet")
        val tilRevurdering = vedtakRepo.hent(uuid("vedtakSomRevurderesId"), session)!! as Vedtak.InnvilgetStønad
        val beregning = stringOrNull("beregning")?.let { objectMapper.readValue<PersistertBeregning>(it) }
        val simulering = stringOrNull("simulering")?.let { objectMapper.readValue<Simulering>(it) }
        val saksbehandler = string("saksbehandler")
        val oppgaveId = stringOrNull("oppgaveid")
        val attestering = stringOrNull("attestering")?.let { objectMapper.readValue<Attestering>(it) }
        val utbetalingId = stringOrNull("utbetalingid")

        val iverksattJournalpostId = stringOrNull("iverksattJournalpostId")?.let { JournalpostId(it) }
        val iverksattBrevbestillingId = stringOrNull("iverksattBrevbestillingId")?.let { BrevbestillingId(it) }

        return when (RevurderingsType.valueOf(string("revurderingsType"))) {
            RevurderingsType.UNDERKJENT -> UnderkjentRevurdering(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = Saksbehandler(saksbehandler),
                beregning = beregning!!,
                simulering = simulering!!,
                oppgaveId = OppgaveId(oppgaveId!!),
                attestering = attestering!!,
            )
            RevurderingsType.IVERKSATT -> IverksattRevurdering(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = Saksbehandler(saksbehandler),
                beregning = beregning!!,
                simulering = simulering!!,
                oppgaveId = OppgaveId(oppgaveId!!),
                attestering = attestering!!,
                utbetalingId = UUID30.fromString(utbetalingId!!),
                eksterneIverksettingsteg = JournalføringOgBrevdistribusjon.fromId(
                    iverksattJournalpostId,
                    iverksattBrevbestillingId
                )
            )
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
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!)
            )
            RevurderingsType.BEREGNET_INNVILGET -> BeregnetRevurdering.Innvilget(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                beregning = beregning!!,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!)
            )
            RevurderingsType.BEREGNET_AVSLAG -> BeregnetRevurdering.Avslag(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                beregning = beregning!!,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!)
            )
            RevurderingsType.OPPRETTET -> OpprettetRevurdering(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = Saksbehandler(saksbehandler),
                oppgaveId = OppgaveId(oppgaveId!!)
            )
        }
    }

    private fun lagre(revurdering: OpprettetRevurdering) =
        dataSource.withSession { session ->
            (
                """
                    insert into revurdering
                        (id, opprettet, periode, beregning, simulering, saksbehandler, oppgaveId, revurderingsType, attestering, utbetalingId, iverksattjournalpostid, iverksattbrevbestillingid, vedtakSomRevurderesId)
                    values
                        (:id, :opprettet, to_json(:periode::json), null, null, :saksbehandler, :oppgaveId, '${RevurderingsType.OPPRETTET}', null, null, null, null, :vedtakSomRevurderesId)
                        ON CONFLICT(id) do update set
                        id=:id,
                        opprettet=:opprettet,
                        periode=to_json(:periode::json),
                        beregning=null,
                        simulering=null,
                        saksbehandler=:saksbehandler,
                        oppgaveId=:oppgaveId,
                        revurderingsType='${RevurderingsType.OPPRETTET}',
                        attestering=null, utbetalingId=null,
                        iverksattjournalpostid=null,
                        iverksattbrevbestillingid=null,
                        vedtakSomRevurderesId=:vedtakSomRevurderesId
                """.trimIndent()
                ).oppdatering(
                mapOf(
                    "id" to revurdering.id,
                    "periode" to objectMapper.writeValueAsString(revurdering.periode),
                    "opprettet" to revurdering.opprettet,
                    "saksbehandler" to revurdering.saksbehandler.navIdent,
                    "oppgaveId" to revurdering.oppgaveId.toString(),
                    "vedtakSomRevurderesId" to revurdering.tilRevurdering.id
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
                    "revurderingsType" to when (revurdering) {
                        is BeregnetRevurdering.Innvilget -> RevurderingsType.BEREGNET_INNVILGET.toString()
                        is BeregnetRevurdering.Avslag -> RevurderingsType.BEREGNET_AVSLAG.toString()
                    }
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
                        revurderingsType = '${RevurderingsType.SIMULERT}'
                    where
                        id = :id
                """.trimIndent()
                ).oppdatering(
                mapOf(
                    "id" to revurdering.id,
                    "saksbehandler" to revurdering.saksbehandler.navIdent,
                    "beregning" to objectMapper.writeValueAsString(revurdering.beregning),
                    "simulering" to objectMapper.writeValueAsString(revurdering.simulering),
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
                        revurderingsType = '${RevurderingsType.TIL_ATTESTERING}',
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
                        revurderingsType = '${RevurderingsType.IVERKSATT}',
                        oppgaveId = :oppgaveId,
                        attestering = to_json(:attestering::json),
                        utbetalingId = :utbetalingId,
                        iverksattjournalpostid = :iverksattjournalpostid,
                        iverksattbrevbestillingid = :iverksattbrevbestillingid
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
                    "attestering" to objectMapper.writeValueAsString(revurdering.attestering),
                    "utbetalingId" to revurdering.utbetalingId,
                    "iverksattjournalpostid" to JournalføringOgBrevdistribusjon.iverksattJournalpostId(
                        revurdering.eksterneIverksettingsteg
                    )?.toString(),
                    "iverksattbrevbestillingid" to JournalføringOgBrevdistribusjon.iverksattBrevbestillingId(
                        revurdering.eksterneIverksettingsteg
                    )?.toString(),
                ),
                session
            )
        }

    private fun lagre(revurdering: UnderkjentRevurdering) =
        dataSource.withSession { session ->
            (
                """
                    update
                        revurdering
                    set
                        revurderingsType = '${RevurderingsType.UNDERKJENT}',
                        oppgaveId = :oppgaveId,
                        attestering = to_json(:attestering::json)
                    where
                        id = :id
                """.trimIndent()
                ).oppdatering(
                mapOf(
                    "id" to revurdering.id,
                    "oppgaveId" to revurdering.oppgaveId.toString(),
                    "attestering" to objectMapper.writeValueAsString(revurdering.attestering),
                ),
                session
            )
        }
}
