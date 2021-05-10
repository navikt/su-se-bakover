package no.nav.su.se.bakover.database.vedtak

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.beregning.PersistertBeregning
import no.nav.su.se.bakover.database.beregning.toSnapshot
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
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
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakType
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

interface VedtakRepo {
    fun hentForSakId(sakId: UUID, session: Session? = null): List<Vedtak>
    fun hent(id: UUID, session: Session? = null): Vedtak?
    fun hentAktive(dato: LocalDate, session: Session? = null): List<Vedtak.EndringIYtelse>
    fun lagre(vedtak: Vedtak)
    fun hentForUtbetaling(utbetalingId: UUID30): Vedtak.EndringIYtelse
    fun hentUtenJournalpost(): List<Vedtak>
    fun hentUtenBrevbestilling(): List<Vedtak>
}

internal class VedtakPosgresRepo(
    private val dataSource: DataSource,
    private val søknadsbehandlingRepo: SøknadsbehandlingRepo,
    private val revurderingRepo: RevurderingRepo,
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

    override fun lagre(vedtak: Vedtak) =
        when (vedtak) {
            is Vedtak.EndringIYtelse -> lagre(vedtak)
            is Vedtak.Avslag -> lagre(vedtak)
            is Vedtak.IngenEndringIYtelse -> lagre(vedtak)
        }

    override fun hentForUtbetaling(utbetalingId: UUID30): Vedtak.EndringIYtelse {
        return dataSource.withSession { session ->
            """
                SELECT *
                FROM vedtak
                WHERE utbetalingId = :utbetalingId
            """.trimIndent()
                .hent(mapOf("utbetalingId" to utbetalingId), session) {
                    it.toVedtak(session)
                } as Vedtak.EndringIYtelse
        }
    }

    override fun hentUtenJournalpost(): List<Vedtak> {
        return dataSource.withSession { session ->
            """
                SELECT *
                FROM vedtak
                WHERE iverksattJournalpostId is null and iverksattBrevbestillingId is null
            """.trimIndent()
                .hentListe(emptyMap(), session) {
                    it.toVedtak(session)
                }
        }
    }

    override fun hentUtenBrevbestilling(): List<Vedtak> {
        return dataSource.withSession { session ->
            """
                SELECT *
                FROM vedtak
                WHERE iverksattJournalpostId is not null and iverksattBrevbestillingId is null
            """.trimIndent()
                .hentListe(emptyMap(), session) {
                    it.toVedtak(session)
                }
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

    override fun hentAktive(dato: LocalDate, session: Session?): List<Vedtak.EndringIYtelse> =
        dataSource.withSession(session) { s ->
            """
            select * from vedtak 
            where fraogmed <= :dato
              and tilogmed >= :dato
            order by fraogmed, tilogmed, opprettet

            """.trimIndent()
                .hentListe(mapOf("dato" to dato), s) {
                    it.toVedtak(s)
                }.filterIsInstance<Vedtak.EndringIYtelse>()
        }

    private fun Row.toVedtak(session: Session): Vedtak {
        val id = uuid("id")
        val opprettet = tidspunkt("opprettet")

        val periode = Periode.create(
            fraOgMed = localDate("fraOgMed"),
            tilOgMed = localDate("tilOgMed"),
        )
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
        val vedtakType = VedtakType.valueOf(string("vedtaktype"))

        return when (vedtakType) {
            VedtakType.SØKNAD,
            VedtakType.ENDRING,
            VedtakType.OPPHØR,
            -> {
                Vedtak.EndringIYtelse(
                    id = id,
                    opprettet = opprettet,
                    periode = periode,
                    behandling = behandling,
                    behandlingsinformasjon = behandlingsinformasjon,
                    beregning = beregning!!,
                    simulering = simulering!!,
                    saksbehandler = saksbehandler,
                    attestant = attestant,
                    utbetalingId = utbetalingId!!,
                    journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.fromId(
                        iverksattJournalpostId,
                        iverksattBrevbestillingId,
                    ),
                    vedtakType = vedtakType,
                )
            }
            VedtakType.AVSLAG -> {
                if (beregning != null) {
                    Vedtak.Avslag.AvslagBeregning(
                        id = id,
                        opprettet = opprettet,
                        behandling = behandling,
                        behandlingsinformasjon = behandlingsinformasjon,
                        beregning = beregning,
                        saksbehandler = saksbehandler,
                        attestant = attestant,
                        journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.fromId(
                            iverksattJournalpostId,
                            iverksattBrevbestillingId,
                        ),
                        periode = periode,
                    )
                } else {
                    Vedtak.Avslag.AvslagVilkår(
                        id = id,
                        opprettet = opprettet,
                        behandling = behandling,
                        behandlingsinformasjon = behandlingsinformasjon,
                        saksbehandler = saksbehandler,
                        attestant = attestant,
                        journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.fromId(
                            iverksattJournalpostId,
                            iverksattBrevbestillingId,
                        ),
                        periode = periode,
                    )
                }
            }
            VedtakType.INGEN_ENDRING -> Vedtak.IngenEndringIYtelse(
                id = id,
                opprettet = opprettet,
                periode = periode,
                behandling = behandling,
                behandlingsinformasjon = behandlingsinformasjon,
                beregning = beregning!!,
                saksbehandler = saksbehandler,
                attestant = attestant,
                journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.fromId(
                    iverksattJournalpostId,
                    iverksattBrevbestillingId,
                ),
            )
        }
    }

    private fun lagre(vedtak: Vedtak.EndringIYtelse) {
        dataSource.withTransaction { tx ->
            """
                INSERT INTO vedtak(
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
                    iverksattbrevbestillingid,
                    vedtaktype
                ) VALUES (
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
                    :iverksattbrevbestillingId,
                    :vedtaktype
                ) ON CONFLICT(id) DO UPDATE SET
                    iverksattjournalpostid = :iverksattjournalpostId,
                    iverksattbrevbestillingid = :iverksattbrevbestillingId
            """.trimIndent()
                .insert(
                    mapOf(
                        "id" to vedtak.id,
                        "opprettet" to vedtak.opprettet,
                        "fraOgMed" to vedtak.periode.fraOgMed,
                        "tilOgMed" to vedtak.periode.tilOgMed,
                        "saksbehandler" to vedtak.saksbehandler,
                        "attestant" to vedtak.attestant,
                        "utbetalingid" to vedtak.utbetalingId,
                        "simulering" to objectMapper.writeValueAsString(vedtak.simulering),
                        "beregning" to objectMapper.writeValueAsString(vedtak.beregning.toSnapshot()),
                        "behandlingsinformasjon" to objectMapper.writeValueAsString(vedtak.behandlingsinformasjon),
                        "iverksattjournalpostId" to JournalføringOgBrevdistribusjon.iverksattJournalpostId(
                            vedtak.journalføringOgBrevdistribusjon,
                        )?.toString(),
                        "iverksattbrevbestillingId" to JournalføringOgBrevdistribusjon.iverksattBrevbestillingId(
                            vedtak.journalføringOgBrevdistribusjon,
                        )?.toString(),
                        "vedtaktype" to vedtak.vedtakType,
                    ),
                    tx,
                )
            lagreBehandlingVedtakKnytning(vedtak, tx)
        }
    }

    private fun lagre(vedtak: Vedtak.Avslag) {
        val beregning = when (vedtak) {
            is Vedtak.Avslag.AvslagBeregning -> vedtak.beregning
            is Vedtak.Avslag.AvslagVilkår -> null
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
                    iverksattbrevbestillingid,
                    vedtaktype
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
                    :iverksattbrevbestillingId,
                    :vedtaktype
                )  ON CONFLICT(id) DO UPDATE SET
                    iverksattjournalpostid = :iverksattjournalpostId,
                    iverksattbrevbestillingid = :iverksattbrevbestillingId
            """.trimIndent()
                .insert(
                    mapOf(
                        "id" to vedtak.id,
                        "opprettet" to vedtak.opprettet,
                        "fraOgMed" to vedtak.periode.fraOgMed,
                        "tilOgMed" to vedtak.periode.tilOgMed,
                        "saksbehandler" to vedtak.saksbehandler,
                        "attestant" to vedtak.attestant,
                        "beregning" to beregning?.let { objectMapper.writeValueAsString(it.toSnapshot()) },
                        "behandlingsinformasjon" to objectMapper.writeValueAsString(vedtak.behandlingsinformasjon),
                        "iverksattjournalpostId" to JournalføringOgBrevdistribusjon.iverksattJournalpostId(
                            vedtak.journalføringOgBrevdistribusjon,
                        )?.toString(),
                        "iverksattbrevbestillingId" to JournalføringOgBrevdistribusjon.iverksattBrevbestillingId(
                            vedtak.journalføringOgBrevdistribusjon,
                        )?.toString(),
                        "vedtaktype" to vedtak.vedtakType,
                    ),
                    tx,
                )
            lagreBehandlingVedtakKnytning(vedtak, tx)
        }
    }

    private fun lagre(vedtak: Vedtak.IngenEndringIYtelse) {
        dataSource.withTransaction { tx ->
            """
                INSERT INTO vedtak(
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
                    iverksattbrevbestillingid,
                    vedtaktype
                ) VALUES (
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
                    :iverksattbrevbestillingId,
                    :vedtaktype
                ) ON CONFLICT(id) DO UPDATE SET
                    iverksattjournalpostid = :iverksattjournalpostId,
                    iverksattbrevbestillingid = :iverksattbrevbestillingId
            """.trimIndent()
                .insert(
                    mapOf(
                        "id" to vedtak.id,
                        "opprettet" to vedtak.opprettet,
                        "fraOgMed" to vedtak.periode.fraOgMed,
                        "tilOgMed" to vedtak.periode.tilOgMed,
                        "saksbehandler" to vedtak.saksbehandler,
                        "attestant" to vedtak.attestant,
                        "utbetalingid" to null,
                        "simulering" to null,
                        "beregning" to objectMapper.writeValueAsString(vedtak.beregning.toSnapshot()),
                        "behandlingsinformasjon" to objectMapper.writeValueAsString(vedtak.behandlingsinformasjon),
                        "iverksattjournalpostId" to JournalføringOgBrevdistribusjon.iverksattJournalpostId(
                            vedtak.journalføringOgBrevdistribusjon,
                        )?.toString(),
                        "iverksattbrevbestillingId" to JournalføringOgBrevdistribusjon.iverksattBrevbestillingId(
                            vedtak.journalføringOgBrevdistribusjon,
                        )?.toString(),
                        "vedtaktype" to vedtak.vedtakType,
                    ),
                    tx,
                )
            lagreBehandlingVedtakKnytning(vedtak, tx)
        }
    }

    private fun lagreBehandlingVedtakKnytning(vedtak: Vedtak, session: Session) {
        val knytning = when (vedtak.behandling) {
            is Revurdering ->
                BehandlingVedtakKnytning.ForRevurdering(
                    vedtakId = vedtak.id,
                    sakId = vedtak.behandling.sakId,
                    revurderingId = vedtak.behandling.id,
                )
            is Søknadsbehandling ->
                BehandlingVedtakKnytning.ForSøknadsbehandling(
                    vedtakId = vedtak.id,
                    sakId = vedtak.behandling.sakId,
                    søknadsbehandlingId = vedtak.behandling.id,
                )
            else ->
                throw IllegalArgumentException("vedtak.behandling er av ukjent type. Den må være en revurdering eller en søknadsbehandling.")
        }

        val map = mapOf(
            "id" to knytning.id,
            "vedtakId" to knytning.vedtakId,
            "sakId" to knytning.sakId,
        ).plus(
            when (knytning) {
                is BehandlingVedtakKnytning.ForSøknadsbehandling ->
                    mapOf(
                        "soknadsbehandlingId" to knytning.søknadsbehandlingId,
                        "revurderingId" to null,
                    )
                is BehandlingVedtakKnytning.ForRevurdering ->
                    mapOf(
                        "soknadsbehandlingId" to null,
                        "revurderingId" to knytning.revurderingId,
                    )
            },
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
                ) ON CONFLICT ON CONSTRAINT unique_vedtakid DO NOTHING
        """.trimIndent()
            .insert(
                map,
                session,
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
                session,
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
                            søknadsbehandlingId = UUID.fromString(søknadsbehandlingId),
                        )
                    }
                    revurderingId != null && søknadsbehandlingId == null -> {
                        BehandlingVedtakKnytning.ForRevurdering(
                            id = id,
                            vedtakId = vedtakId2,
                            sakId = sakId,
                            revurderingId = UUID.fromString(revurderingId),
                        )
                    }
                    else -> {
                        throw IllegalStateException(
                            "Fant ugyldig behandling-vedtak-knytning. søknadsbehandlingId=$søknadsbehandlingId, revurderingId=$revurderingId. Èn og nøyaktig èn av dem må være satt.",
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
            val søknadsbehandlingId: UUID,
        ) : BehandlingVedtakKnytning()

        data class ForRevurdering(
            override val id: UUID = UUID.randomUUID(),
            override val vedtakId: UUID,
            override val sakId: UUID,
            val revurderingId: UUID,
        ) : BehandlingVedtakKnytning()
    }
}
