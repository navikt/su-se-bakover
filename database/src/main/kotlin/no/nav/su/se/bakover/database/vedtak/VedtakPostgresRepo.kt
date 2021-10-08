package no.nav.su.se.bakover.database.vedtak

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.beregning.deserialiserBeregning
import no.nav.su.se.bakover.database.beregning.serialiserBeregning
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.revurdering.RevurderingPostgresRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingPostgresRepo
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.database.uuid30OrNull
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.database.withTransaction
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

internal enum class VedtakType {
    SØKNAD, // Innvilget Søknadsbehandling                  -> EndringIYtelse
    AVSLAG, // Avslått Søknadsbehandling                    -> Avslag
    ENDRING, // Revurdering innvilget                       -> EndringIYtelse
    INGEN_ENDRING, // Revurdering mellom 2% og 10% endring  -> IngenEndringIYtelse
    OPPHØR, // Revurdering ført til opphør                  -> EndringIYtelse
    STANS_AV_YTELSE,
    GJENOPPTAK_AV_YTELSE,
}

interface VedtakRepo {
    fun hentForSakId(sakId: UUID): List<Vedtak>
    fun hentAktive(dato: LocalDate): List<Vedtak.EndringIYtelse>
    fun lagre(vedtak: Vedtak)
    fun hentForUtbetaling(utbetalingId: UUID30): Vedtak?
}

internal class VedtakPostgresRepo(
    private val dataSource: DataSource,
    private val søknadsbehandlingRepo: SøknadsbehandlingPostgresRepo,
    private val revurderingRepo: RevurderingPostgresRepo,
    private val dbMetrics: DbMetrics,
) : VedtakRepo {

    override fun hentForSakId(sakId: UUID): List<Vedtak> {
        return dbMetrics.timeQuery("hentVedtakForSakId") {
            dataSource.withSession { session -> hentForSakId(sakId, session) }
        }
    }

    internal fun hentForSakId(sakId: UUID, session: Session): List<Vedtak> =
        """
            SELECT v.*
            FROM vedtak v
            JOIN behandling_vedtak bv ON bv.vedtakid = v.id
            WHERE bv.sakId = :sakId
        """.trimIndent()
            .hentListe(mapOf("sakId" to sakId), session) {
                it.toVedtak(session)
            }

    override fun lagre(vedtak: Vedtak) =
        when (vedtak) {
            is Vedtak.EndringIYtelse -> lagre(vedtak)
            is Vedtak.Avslag -> lagre(vedtak)
            is Vedtak.IngenEndringIYtelse -> lagre(vedtak)
        }

    override fun hentForUtbetaling(utbetalingId: UUID30): Vedtak? {
        return dataSource.withSession { session ->
            """
                SELECT *
                FROM vedtak
                WHERE utbetalingId = :utbetalingId
            """.trimIndent()
                .hent(mapOf("utbetalingId" to utbetalingId), session) {
                    it.toVedtak(session)
                }
        }
    }

    internal fun hent(id: UUID, session: Session) =
        """
            select * from vedtak where id = :id
        """.trimIndent()
            .hent(mapOf("id" to id), session) {
                it.toVedtak(session)
            }

    override fun hentAktive(dato: LocalDate): List<Vedtak.EndringIYtelse> =
        dataSource.withSession { session ->
            """
            select * from vedtak 
            where fraogmed <= :dato
              and tilogmed >= :dato
            order by fraogmed, tilogmed, opprettet

            """.trimIndent()
                .hentListe(mapOf("dato" to dato), session) {
                    it.toVedtak(session)
                }.filterIsInstance<Vedtak.EndringIYtelse>()
        }

    private fun Row.toVedtak(session: Session): Vedtak {
        val id = uuid("id")
        val opprettet = tidspunkt("opprettet")

        val periode = Periode.create(
            fraOgMed = localDate("fraOgMed"),
            tilOgMed = localDate("tilOgMed"),
        )
        val behandling: Behandling = when (val knytning = hentBehandlingVedtakKnytning(id, session)) {
            is BehandlingVedtakKnytning.ForSøknadsbehandling ->
                søknadsbehandlingRepo.hent(knytning.søknadsbehandlingId, session)!!
            is BehandlingVedtakKnytning.ForRevurdering ->
                revurderingRepo.hent(knytning.revurderingId, session)!!
            else ->
                throw IllegalStateException("Fant ikke knytning mellom vedtak og søknadsbehandling/revurdering.")
        }

        val saksbehandler = stringOrNull("saksbehandler")?.let { NavIdentBruker.Saksbehandler(it) }!!
        val attestant = stringOrNull("attestant")?.let { NavIdentBruker.Attestant(it) }!!
        val utbetalingId = uuid30OrNull("utbetalingId")
        val beregning = deserialiserBeregning(stringOrNull("beregning"))
        val simulering = stringOrNull("simulering")?.let { objectMapper.readValue<Simulering>(it) }

        return when (VedtakType.valueOf(string("vedtaktype"))) {
            VedtakType.SØKNAD -> {
                Vedtak.EndringIYtelse.InnvilgetSøknadsbehandling(
                    id = id,
                    opprettet = opprettet,
                    behandling = behandling as Søknadsbehandling.Iverksatt.Innvilget,
                    saksbehandler = saksbehandler,
                    attestant = attestant,
                    periode = periode,
                    beregning = beregning!!,
                    simulering = simulering!!,
                    utbetalingId = utbetalingId!!,
                )
            }
            VedtakType.ENDRING -> {
                Vedtak.EndringIYtelse.InnvilgetRevurdering(
                    id = id,
                    opprettet = opprettet,
                    behandling = behandling as IverksattRevurdering.Innvilget,
                    saksbehandler = saksbehandler,
                    attestant = attestant,
                    periode = periode,
                    beregning = beregning!!,
                    simulering = simulering!!,
                    utbetalingId = utbetalingId!!,
                )
            }
            VedtakType.OPPHØR -> {
                Vedtak.EndringIYtelse.OpphørtRevurdering(
                    id = id,
                    opprettet = opprettet,
                    behandling = behandling as IverksattRevurdering.Opphørt,
                    saksbehandler = saksbehandler,
                    attestant = attestant,
                    periode = periode,
                    beregning = beregning!!,
                    simulering = simulering!!,
                    utbetalingId = utbetalingId!!,
                )
            }
            VedtakType.AVSLAG -> {
                if (beregning != null) {
                    Vedtak.Avslag.AvslagBeregning(
                        id = id,
                        opprettet = opprettet,
                        // AVSLAG gjelder kun for søknadsbehandling
                        behandling = behandling as Søknadsbehandling.Iverksatt.Avslag.MedBeregning,
                        beregning = beregning,
                        saksbehandler = saksbehandler,
                        attestant = attestant,
                        periode = periode,
                    )
                } else {
                    Vedtak.Avslag.AvslagVilkår(
                        id = id,
                        opprettet = opprettet,
                        // AVSLAG gjelder kun for søknadsbehandling
                        behandling = behandling as Søknadsbehandling.Iverksatt.Avslag.UtenBeregning,
                        saksbehandler = saksbehandler,
                        attestant = attestant,
                        periode = periode,
                    )
                }
            }
            VedtakType.INGEN_ENDRING -> Vedtak.IngenEndringIYtelse(
                id = id,
                opprettet = opprettet,
                behandling = behandling as IverksattRevurdering.IngenEndring,
                saksbehandler = saksbehandler,
                attestant = attestant,
                periode = periode,
                beregning = beregning!!,
            )
            VedtakType.STANS_AV_YTELSE -> Vedtak.EndringIYtelse.StansAvYtelse(
                id = id,
                opprettet = opprettet,
                behandling = behandling as StansAvYtelseRevurdering.IverksattStansAvYtelse,
                saksbehandler = saksbehandler,
                attestant = attestant,
                periode = periode,
                simulering = simulering!!,
                utbetalingId = utbetalingId!!,
            )
            VedtakType.GJENOPPTAK_AV_YTELSE -> Vedtak.EndringIYtelse.GjenopptakAvYtelse(
                id = id,
                opprettet = opprettet,
                behandling = behandling as GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse,
                saksbehandler = saksbehandler,
                attestant = attestant,
                periode = periode,
                simulering = simulering!!,
                utbetalingId = utbetalingId!!,
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
                    utbetalingid,
                    simulering,
                    beregning,
                    vedtaktype
                ) VALUES (
                    :id,
                    :opprettet,
                    :fraOgMed,
                    :tilOgMed,
                    :saksbehandler,
                    :attestant,
                    :utbetalingid,
                    to_json(:simulering::json),
                    to_json(:beregning::json),
                    :vedtaktype
                )
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
                        "beregning" to when (vedtak) {
                            is Vedtak.EndringIYtelse.GjenopptakAvYtelse ->
                                null
                            is Vedtak.EndringIYtelse.StansAvYtelse ->
                                null
                            is Vedtak.EndringIYtelse.InnvilgetRevurdering ->
                                serialiserBeregning(vedtak.beregning)
                            is Vedtak.EndringIYtelse.InnvilgetSøknadsbehandling ->
                                serialiserBeregning(vedtak.beregning)
                            is Vedtak.EndringIYtelse.OpphørtRevurdering ->
                                serialiserBeregning(vedtak.beregning)
                        },
                        "vedtaktype" to when (vedtak) {
                            is Vedtak.EndringIYtelse.GjenopptakAvYtelse -> VedtakType.GJENOPPTAK_AV_YTELSE
                            is Vedtak.EndringIYtelse.InnvilgetRevurdering -> VedtakType.ENDRING
                            is Vedtak.EndringIYtelse.InnvilgetSøknadsbehandling -> VedtakType.SØKNAD
                            is Vedtak.EndringIYtelse.OpphørtRevurdering -> VedtakType.OPPHØR
                            is Vedtak.EndringIYtelse.StansAvYtelse -> VedtakType.STANS_AV_YTELSE
                        },
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
                    utbetalingid,
                    simulering,
                    beregning,
                    vedtaktype
                ) values (
                    :id,
                    :opprettet,
                    :fraOgMed,
                    :tilOgMed,
                    :saksbehandler,
                    :attestant,
                    :utbetalingid,
                    to_json(:simulering::json),
                    to_json(:beregning::json),
                    :vedtaktype
                )
            """.trimIndent()
                .insert(
                    mapOf(
                        "id" to vedtak.id,
                        "opprettet" to vedtak.opprettet,
                        "fraOgMed" to vedtak.periode.fraOgMed,
                        "tilOgMed" to vedtak.periode.tilOgMed,
                        "saksbehandler" to vedtak.saksbehandler,
                        "attestant" to vedtak.attestant,
                        "beregning" to beregning?.let { serialiserBeregning(it) },
                        "vedtaktype" to VedtakType.AVSLAG,
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
                    utbetalingid,
                    simulering,
                    beregning,
                    vedtaktype
                ) VALUES (
                    :id,
                    :opprettet,
                    :fraOgMed,
                    :tilOgMed,
                    :saksbehandler,
                    :attestant,
                    :utbetalingid,
                    to_json(:simulering::json),
                    to_json(:beregning::json),
                    :vedtaktype
                )
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
                        "beregning" to serialiserBeregning(vedtak.beregning),
                        "vedtaktype" to VedtakType.INGEN_ENDRING,
                    ),
                    tx,
                )
            lagreBehandlingVedtakKnytning(vedtak, tx)
        }
    }

    private fun lagreBehandlingVedtakKnytning(vedtak: Vedtak, session: Session) {
        val knytning = when (vedtak.behandling) {
            is AbstraktRevurdering ->
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

    private fun hentBehandlingVedtakKnytning(vedtakId: UUID, session: Session): BehandlingVedtakKnytning? =
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
