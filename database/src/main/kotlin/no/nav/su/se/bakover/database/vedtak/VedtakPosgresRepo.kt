package no.nav.su.se.bakover.database.vedtak

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.JournalføringOgBrevdistribusjonMapper
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.beregning.PersistertBeregning
import no.nav.su.se.bakover.database.beregning.toSnapshot
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.database.uuid30OrNull
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.database.withTransaction
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import java.util.UUID
import javax.sql.DataSource

interface VedtakRepo {
    fun hentForSakId(sakId: UUID, session: Session? = null): List<Vedtak>
    fun hent(id: UUID, session: Session? = null): Vedtak?
    fun lagre(vedtak: Vedtak)
    fun oppdaterJournalpostForSøknadsbehandling(søknadsbehandlingId: UUID, journalpostId: JournalpostId)
    fun oppdaterBrevbestillingIdForSøknadsbehandling(søknadsbehandlingId: UUID, brevbestillingId: BrevbestillingId)
    fun oppdaterJournalpostForRevurdering(revurderingId: UUID, journalpostId: JournalpostId)
    fun oppdaterBrevbestillingIdForRevurdering(revurderingId: UUID, brevbestillingId: BrevbestillingId)
    fun hentForUtbetaling(utbetalingId: UUID30): Vedtak.InnvilgetStønad
}

internal class VedtakPosgresRepo(
    private val dataSource: DataSource,
    private val søknadsbehandlingRepo: SøknadsbehandlingRepo,
    private val revurderingRepo: RevurderingRepo
) : VedtakRepo {
    override fun hentForSakId(sakId: UUID, session: Session?): List<Vedtak> =
        dataSource.withSession(session) { s ->
            """
            SELECT v.*
            FROM vedtak v
            JOIN behandling_vedtak bv ON bv.vedtakid = v.id
            WHERE bv.sakId = :sakId
            """.trimIndent()
                .hentListe(mapOf("sakId" to sakId), s) {
                    it.toVedtak(s)
                }
        }

    override fun lagre(vedtak: Vedtak) {
        when (vedtak) {
            is Vedtak.InnvilgetStønad -> lagre(vedtak)
            is Vedtak.AvslåttStønad -> lagre(vedtak)
        }
    }

    override fun oppdaterJournalpostForSøknadsbehandling(søknadsbehandlingId: UUID, journalpostId: JournalpostId) {
        dataSource.withSession {
            """
            UPDATE vedtak
                SET iverksattjournalpostid = :iverksattjournalpostid
            FROM behandling_vedtak
            WHERE behandling_vedtak.vedtakid = vedtak.id
                AND behandling_vedtak.søknadsbehandlingid = :soknadsbehandlingid
            """.trimIndent()
                .oppdatering(
                    mapOf(
                        "iverksattjournalpostid" to journalpostId,
                        "soknadsbehandlingid" to søknadsbehandlingId
                    ),
                    it
                )
        }
    }

    override fun oppdaterBrevbestillingIdForSøknadsbehandling(
        søknadsbehandlingId: UUID,
        brevbestillingId: BrevbestillingId
    ) {
        dataSource.withSession {
            """
            UPDATE vedtak
                SET iverksattbrevbestillingid = :iverksattbrevbestillingid
            FROM behandling_vedtak
            WHERE behandling_vedtak.vedtakid = vedtak.id
                AND behandling_vedtak.søknadsbehandlingid = :soknadsbehandlingid
            """.trimIndent()
                .oppdatering(
                    mapOf(
                        "iverksattbrevbestillingid" to brevbestillingId,
                        "soknadsbehandlingid" to søknadsbehandlingId
                    ),
                    it
                )
        }
    }

    override fun oppdaterJournalpostForRevurdering(revurderingId: UUID, journalpostId: JournalpostId) {
        dataSource.withSession {
            """
            UPDATE vedtak
                SET iverksattjournalpostid = :iverksattjournalpostid
            FROM behandling_vedtak
            WHERE behandling_vedtak.vedtakid = vedtak.id
                AND behandling_vedtak.revurderingId = :revurderingId
            """.trimIndent()
                .oppdatering(
                    mapOf(
                        "iverksattjournalpostid" to journalpostId,
                        "revurderingId" to revurderingId
                    ),
                    it
                )
        }
    }

    override fun oppdaterBrevbestillingIdForRevurdering(revurderingId: UUID, brevbestillingId: BrevbestillingId) {
        dataSource.withSession {
            """
            UPDATE vedtak
                SET iverksattbrevbestillingid = :iverksattbrevbestillingid
            FROM behandling_vedtak
            WHERE behandling_vedtak.vedtakid = vedtak.id
                AND behandling_vedtak.revurderingId = :revurderingId
            """.trimIndent()
                .oppdatering(
                    mapOf(
                        "iverksattbrevbestillingid" to brevbestillingId,
                        "revurderingId" to revurderingId
                    ),
                    it
                )
        }
    }

    override fun hentForUtbetaling(utbetalingId: UUID30): Vedtak.InnvilgetStønad {
        return dataSource.withSession { session ->
            """
                SELECT *
                FROM vedtak
                WHERE utbetalingId = :utbetalingId
            """.trimIndent()
                .hent(mapOf("utbetalingId" to utbetalingId), session) {
                    it.toVedtak(session)
                } as Vedtak.InnvilgetStønad
        }
    }

    override fun hent(id: UUID, session: Session?) =
        dataSource.withSession(session) { s ->
            """
            select * from vedtak where id = :id
            """.trimIndent()
                .hent(mapOf("id" to id), s) {
                    it.toVedtak(s)
                }
        }

    private fun Row.toVedtak(session: Session): Vedtak {
        val id = uuid("id")
        val opprettet = tidspunkt("opprettet")
        val fraOgMed = localDateOrNull("fraOgMed")
        val tilOgMed = localDateOrNull("tilOgMed")

        val behandling = when (val knytning = hentBehandlingVedtakKnytning(id, session)) {
            is BehandlingVedtakKnytning.ForSøknadsbehandling ->
                søknadsbehandlingRepo.hent(knytning.søknadsbehandlingId, session)!!
            is BehandlingVedtakKnytning.ForRevurdering ->
                revurderingRepo.hent(knytning.revurderingId, session)!!
            else ->
                throw IllegalStateException("Fant ikke knytning mellom vedtak og søknadsbehandling/revurdering.")
        }

        val saksbehandler = stringOrNull("saksbehandler")?.let { NavIdentBruker.Saksbehandler(it) }!!
        val attestant = stringOrNull("attestant")?.let { NavIdentBruker.Attestant(it) }!!
        val behandlingsinformasjon = objectMapper.readValue<Behandlingsinformasjon>(string("behandlingsinformasjon"))
        val utbetalingId = uuid30OrNull("utbetalingId")
        val beregning = stringOrNull("beregning")?.let { objectMapper.readValue<PersistertBeregning>(it) }
        val simulering = stringOrNull("simulering")?.let { objectMapper.readValue<Simulering>(it) }
        val iverksattJournalpostId = stringOrNull("iverksattJournalpostId")?.let { JournalpostId(it) }
        val iverksattBrevbestillingId = stringOrNull("iverksattBrevbestillingId")?.let { BrevbestillingId(it) }

        // TODO fix this hacky mapping
        return when {
            utbetalingId != null && beregning != null && simulering != null -> Vedtak.InnvilgetStønad(
                id = id,
                opprettet = opprettet,
                periode = Periode.create(fraOgMed!!, tilOgMed!!),
                behandling = behandling,
                behandlingsinformasjon = behandlingsinformasjon,
                beregning = beregning,
                simulering = simulering,
                saksbehandler = saksbehandler,
                attestant = attestant,
                utbetalingId = utbetalingId,
                eksterneIverksettingsteg = JournalføringOgBrevdistribusjonMapper.idToObject(
                    iverksattJournalpostId,
                    iverksattBrevbestillingId
                )
            )
            utbetalingId == null && beregning != null -> Vedtak.AvslåttStønad.MedBeregning(
                id = id,
                opprettet = opprettet,
                behandling = behandling,
                behandlingsinformasjon = behandlingsinformasjon,
                beregning = beregning,
                saksbehandler = saksbehandler,
                attestant = attestant,
                eksterneIverksettingsteg = JournalføringOgBrevdistribusjonMapper.idToObject(
                    iverksattJournalpostId,
                    iverksattBrevbestillingId
                )
            )
            utbetalingId == null && beregning == null -> Vedtak.AvslåttStønad.UtenBeregning(
                id = id,
                opprettet = opprettet,
                behandling = behandling,
                behandlingsinformasjon = behandlingsinformasjon,
                saksbehandler = saksbehandler,
                attestant = attestant,
                eksterneIverksettingsteg = JournalføringOgBrevdistribusjonMapper.idToObject(
                    iverksattJournalpostId,
                    iverksattBrevbestillingId
                )
            )
            else -> throw IllegalStateException("Alvorlig feil i mapping")
        }
    }

    private fun lagre(vedtak: Vedtak.InnvilgetStønad) {
        dataSource.withTransaction { tx ->
            """
                insert into vedtak(
                    id,
                    opprettet,
                    fraOgMed,
                    tilOgMed,
                    saksbehandler,
                    attestant,
                    behandlingsinformasjon,
                    utbetalingid,
                    simulering,
                    beregning,
                    iverksattjournalpostid,
                    iverksattbrevbestillingid
                ) values (
                    :id,
                    :opprettet,
                    :fraOgMed,
                    :tilOgMed,
                    :saksbehandler,
                    :attestant,
                    to_json(:behandlingsinformasjon::json),
                    :utbetalingid,
                    to_json(:simulering::json),
                    to_json(:beregning::json),
                    :iverksattjournalpostId,
                    :iverksattbrevbestillingId
                )
            """.trimIndent()
                .oppdatering(
                    mapOf(
                        "id" to vedtak.id,
                        "opprettet" to vedtak.opprettet,
                        "fraOgMed" to vedtak.periode.getFraOgMed(),
                        "tilOgMed" to vedtak.periode.getTilOgMed(),
                        "saksbehandler" to vedtak.saksbehandler,
                        "attestant" to vedtak.attestant,
                        "utbetalingid" to vedtak.utbetalingId,
                        "simulering" to objectMapper.writeValueAsString(vedtak.simulering),
                        "beregning" to objectMapper.writeValueAsString(vedtak.beregning.toSnapshot()),
                        "behandlingsinformasjon" to objectMapper.writeValueAsString(vedtak.behandlingsinformasjon),
                        "iverksattjournalpostId" to JournalføringOgBrevdistribusjonMapper.iverksattJournalpostId(
                            vedtak.eksterneIverksettingsteg
                        )?.toString(),
                        "iverksattbrevbestillingId" to JournalføringOgBrevdistribusjonMapper.iverksattBrevbestillingId(
                            vedtak.eksterneIverksettingsteg
                        )?.toString(),
                    ),
                    tx
                )

            lagreBehandlingVedtakKnytning(
                when (vedtak.behandling) {
                    is Revurdering ->
                        BehandlingVedtakKnytning.ForRevurdering(
                            vedtakId = vedtak.id,
                            sakId = vedtak.behandling.sakId,
                            revurderingId = vedtak.behandling.id
                        )
                    is Søknadsbehandling ->
                        BehandlingVedtakKnytning.ForSøknadsbehandling(
                            vedtakId = vedtak.id,
                            sakId = vedtak.behandling.sakId,
                            søknadsbehandlingId = vedtak.behandling.id
                        )
                    else ->
                        throw IllegalArgumentException("vedtak.behandling er av ukjent type. Den må være en revurdering eller en søknadsbehandling.")
                },
                tx
            )
        }
    }

    private fun lagre(vedtak: Vedtak.AvslåttStønad) {
        val beregning = when (vedtak) {
            is Vedtak.AvslåttStønad.MedBeregning -> vedtak.beregning
            is Vedtak.AvslåttStønad.UtenBeregning -> null
        }
        dataSource.withTransaction { tx ->
            """
                insert into vedtak(
                    id,
                    opprettet,
                    fraOgMed,
                    tilOgMed,
                    saksbehandler,
                    attestant,
                    behandlingsinformasjon,
                    utbetalingid,
                    simulering,
                    beregning,
                    iverksattjournalpostid,
                    iverksattbrevbestillingid
                ) values (
                    :id,
                    :opprettet,
                    :fraOgMed,
                    :tilOgMed,
                    :saksbehandler,
                    :attestant,
                    to_json(:behandlingsinformasjon::json),
                    :utbetalingid,
                    to_json(:simulering::json),
                    to_json(:beregning::json),
                    :iverksattjournalpostId,
                    :iverksattbrevbestillingId
                )
            """.trimIndent()
                .oppdatering(
                    mapOf(
                        "id" to vedtak.id,
                        "opprettet" to vedtak.opprettet,
                        "fraOgMed" to null,
                        "tilOgMed" to null,
                        "saksbehandler" to vedtak.saksbehandler,
                        "attestant" to vedtak.attestant,
                        "beregning" to beregning?.let { objectMapper.writeValueAsString(it.toSnapshot()) },
                        "behandlingsinformasjon" to objectMapper.writeValueAsString(vedtak.behandlingsinformasjon),
                        "iverksattjournalpostId" to JournalføringOgBrevdistribusjonMapper.iverksattJournalpostId(
                            vedtak.eksterneIverksettingsteg
                        )?.toString(),
                        "iverksattbrevbestillingId" to JournalføringOgBrevdistribusjonMapper.iverksattBrevbestillingId(
                            vedtak.eksterneIverksettingsteg
                        )?.toString(),
                    ),
                    tx
                )

            lagreBehandlingVedtakKnytning(
                when (vedtak.behandling) {
                    is Søknadsbehandling ->
                        BehandlingVedtakKnytning.ForSøknadsbehandling(
                            vedtakId = vedtak.id,
                            sakId = vedtak.behandling.sakId,
                            søknadsbehandlingId = vedtak.behandling.id
                        )
                    else -> throw IllegalArgumentException("Vedtak.behandling er av ukjent type. Støtter bare søknadsbehandling inntil videre")
                },
                tx
            )
        }
    }

    private fun lagreBehandlingVedtakKnytning(knytning: BehandlingVedtakKnytning, session: Session) {
        val map = mapOf(
            "id" to knytning.id,
            "vedtakId" to knytning.vedtakId,
            "sakId" to knytning.sakId,
        ).plus(
            when (knytning) {
                is BehandlingVedtakKnytning.ForSøknadsbehandling ->
                    mapOf(
                        "soknadsbehandlingId" to knytning.søknadsbehandlingId,
                        "revurderingId" to null
                    )
                is BehandlingVedtakKnytning.ForRevurdering ->
                    mapOf(
                        "soknadsbehandlingId" to null,
                        "revurderingId" to knytning.revurderingId
                    )
            }
        )
        """
                INSERT INTO behandling_vedtak
                (
                    id,
                    vedtakId,
                    sakId,
                    søknadsbehandlingId,
                    revurderingId
                ) VALUES (
                    :id,
                    :vedtakId,
                    :sakId,
                    :soknadsbehandlingId,
                    :revurderingId
                )
        """.trimIndent()
            .oppdatering(
                map,
                session
            )
    }

    private fun hentBehandlingVedtakKnytning(vedtakId: UUID, session: Session) =
        """
            SELECT *
            FROM behandling_vedtak
            WHERE vedtakId = :vedtakId
        """.trimIndent()
            .hent(
                mapOf("vedtakId" to vedtakId),
                session
            ) {
                val id = it.uuid("id")
                val vedtakId2 = it.uuid("vedtakId")
                val sakId = it.uuid("sakId")
                val søknadsbehandlingId = it.stringOrNull("søknadsbehandlingId")
                val revurderingId = it.stringOrNull("revurderingId")

                when {
                    revurderingId == null && søknadsbehandlingId != null -> {
                        BehandlingVedtakKnytning.ForSøknadsbehandling(
                            id = id,
                            vedtakId = vedtakId2,
                            sakId = sakId,
                            søknadsbehandlingId = UUID.fromString(søknadsbehandlingId)
                        )
                    }
                    revurderingId != null && søknadsbehandlingId == null -> {
                        BehandlingVedtakKnytning.ForRevurdering(
                            id = id,
                            vedtakId = vedtakId2,
                            sakId = sakId,
                            revurderingId = UUID.fromString(revurderingId)
                        )
                    }
                    else -> {
                        throw IllegalStateException(
                            "Fant ugyldig behandling-vedtak-knytning. søknadsbehandlingId=$søknadsbehandlingId, revurderingId=$revurderingId. Èn og nøyaktig èn av dem må være satt."
                        )
                    }
                }
            }

    private sealed class BehandlingVedtakKnytning {
        abstract val id: UUID
        abstract val vedtakId: UUID
        abstract val sakId: UUID

        data class ForSøknadsbehandling(
            override val id: UUID = UUID.randomUUID(),
            override val vedtakId: UUID,
            override val sakId: UUID,
            val søknadsbehandlingId: UUID
        ) : BehandlingVedtakKnytning()

        data class ForRevurdering(
            override val id: UUID = UUID.randomUUID(),
            override val vedtakId: UUID,
            override val sakId: UUID,
            val revurderingId: UUID
        ) : BehandlingVedtakKnytning()
    }
}
