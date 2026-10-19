package no.nav.su.se.bakover.database.historisk

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotliquery.Row
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresTransactionContext.Companion.withTransaction
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdAttestering
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdBeregning
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdering
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingId
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingRepo
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingStatus
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingsvedtak
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurderingsvedtakId
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdVedtaksbrevvalg
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskeVedtakSomRevurderesMånedsvis
import no.nav.su.se.bakover.domain.historisk.revurdering.IverksatteMånedsresultater
import no.nav.su.se.bakover.domain.historisk.revurdering.KunneIkkeOppretteHistoriskInfotrygdRevurdering
import java.util.UUID

class HistoriskInfotrygdRevurderingPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : HistoriskInfotrygdRevurderingRepo {
    /**
     * Brukes bare av lokal seed, som erstatter hele det historiske datasettet ved oppstart.
     * Produksjonsflyten skal aldri slette behandlinger når en import slettes.
     */
    fun slettAlleForLokalSeed() {
        dbMetrics.timeQuery("slettHistoriskeInfotrygdRevurderingerForLokalSeed") {
            sessionFactory.withTransaction { tx ->
                """
                    DELETE FROM dokument_distribusjon
                    WHERE dokumentId IN (
                        SELECT id FROM dokument WHERE historisk_revurdering_id IS NOT NULL
                    )
                """.trimIndent().oppdatering(emptyMap(), tx)
                """
                    DELETE FROM dokument WHERE historisk_revurdering_id IS NOT NULL
                """.trimIndent().oppdatering(emptyMap(), tx)
                """
                    DELETE FROM mottaker WHERE referanse_type = 'HISTORISK_INFOTRYGD_REVURDERING'
                """.trimIndent().oppdatering(emptyMap(), tx)
                """
                    DELETE FROM historisk_infotrygd_revurderingsvedtak
                    WHERE revurdering_id IN (
                        SELECT id FROM historisk_infotrygd_revurdering
                    )
                """.trimIndent().oppdatering(emptyMap(), tx)
                """
                    DELETE FROM historisk_infotrygd_revurdering
                    WHERE id IS NOT NULL
                """.trimIndent().oppdatering(emptyMap(), tx)
            }
        }
    }

    override fun opprett(
        revurdering: HistoriskInfotrygdRevurdering,
    ): Either<KunneIkkeOppretteHistoriskInfotrygdRevurdering, HistoriskInfotrygdRevurdering> =
        dbMetrics.timeQuery("opprettHistoriskInfotrygdRevurdering") {
            sessionFactory.withTransaction { tx ->
                låsSak(revurdering.sakId, tx)
                finnOverlappendeÅpenBehandling(revurdering, tx)?.let { eksisterende ->
                    return@withTransaction KunneIkkeOppretteHistoriskInfotrygdRevurdering
                        .OverlapperÅpenBehandling(
                            eksisterendeRevurderingId = eksisterende.id,
                            sakId = eksisterende.sakId,
                        )
                        .left()
                }
                lagreNyBehandling(revurdering, tx)
                revurdering.right()
            }
        }

    override fun lagre(
        revurdering: HistoriskInfotrygdRevurdering,
        transactionContext: TransactionContext,
    ) {
        dbMetrics.timeQuery("lagreHistoriskInfotrygdRevurdering") {
            transactionContext.withTransaction { tx ->
                """
                UPDATE historisk_infotrygd_revurdering
                SET status = :status,
                    saksbehandler = :saksbehandler,
                    oppdatert = :oppdatert,
                    begrunnelse = CASE WHEN :status = 'AVSLUTTET' THEN :avslutningsbegrunnelse ELSE begrunnelse END,
                    vedtaksbrevvalg = :vedtaksbrevvalg,
                    vedtaksbrev_fritekst = :vedtaksbrev_fritekst,
                    forhandsvarsel = CAST(:forhandsvarsel AS JSONB),
                    beregning = CAST(:historisk_beregning_json AS JSONB),
                    attesteringer = CAST(:attesteringer AS JSONB)
                WHERE id = :id
                """.trimIndent().oppdatering(
                    mapOf(
                        "id" to revurdering.id.value,
                        "status" to revurdering.status.name,
                        "saksbehandler" to revurdering.saksbehandler.navIdent,
                        "oppdatert" to revurdering.oppdatert,
                        "avslutningsbegrunnelse" to revurdering.avslutningsbegrunnelse,
                        "vedtaksbrevvalg" to revurdering.vedtaksbrevvalg.tilDbverdi(),
                        "vedtaksbrev_fritekst" to revurdering.vedtaksbrevFritekst,
                        "forhandsvarsel" to revurdering.forhåndsvarsel.serializeForhåndsvarsel(),
                        "historisk_beregning_json" to revurdering.beregning?.let {
                            HistoriskInfotrygdBeregningDbJson.fromDomain(it).serialize()
                        },
                        "attesteringer" to revurdering.attesteringer.serializeAttesteringer(),
                    ),
                    tx,
                )
            }
        }
    }

    override fun hent(id: HistoriskInfotrygdRevurderingId): HistoriskInfotrygdRevurdering? =
        dbMetrics.timeQuery("hentHistoriskInfotrygdRevurdering") {
            sessionFactory.withSession { session ->
                val behandling = """
                    SELECT *
                    FROM historisk_infotrygd_revurdering
                    WHERE id = :id
                """.trimIndent().hent(mapOf("id" to id.value), session) { it.tilBehandlingRad() }
                    ?: return@withSession null

                behandling.toDomain()
            }
        }

    override fun hentForSak(sakId: UUID): List<HistoriskInfotrygdRevurdering> =
        dbMetrics.timeQuery("hentHistoriskeInfotrygdRevurderingerForSak") {
            sessionFactory.withSession { session ->
                """
                    SELECT *
                    FROM historisk_infotrygd_revurdering
                    WHERE sak_id = :sak_id
                    ORDER BY opprettet DESC, id
                """.trimIndent().hentListe(
                    mapOf("sak_id" to sakId),
                    session,
                ) { it.tilBehandlingRad().toDomain() }
            }
        }

    override fun lagreVedtak(vedtak: HistoriskInfotrygdRevurderingsvedtak) {
        dbMetrics.timeQuery("lagreHistoriskInfotrygdRevurderingsvedtak") {
            sessionFactory.withTransaction { tx ->
                """
                    INSERT INTO historisk_infotrygd_revurderingsvedtak (
                        id, revurdering_id, utbetaling_id, iverksatt, attestant, beregning
                    ) VALUES (
                        :id, :revurdering_id, :utbetaling_id, :iverksatt, :attestant,
                        CAST(:historisk_beregning_json AS JSONB)
                    )
                """.trimIndent().insert(
                    mapOf(
                        "id" to vedtak.id.value,
                        "revurdering_id" to vedtak.revurderingId.value,
                        "utbetaling_id" to vedtak.utbetalingId,
                        "iverksatt" to vedtak.iverksatt,
                        "attestant" to vedtak.attestant.navIdent,
                        "historisk_beregning_json" to
                            HistoriskInfotrygdBeregningDbJson.fromDomain(vedtak.beregning).serialize(),
                    ),
                    tx,
                )
            }
        }
    }

    override fun finnesVedtakForRevurdering(id: HistoriskInfotrygdRevurderingId): Boolean =
        dbMetrics.timeQuery("finnesHistoriskInfotrygdRevurderingsvedtak") {
            sessionFactory.withSession { session ->
                """
                    SELECT 1 FROM historisk_infotrygd_revurderingsvedtak
                    WHERE revurdering_id = :revurdering_id
                    LIMIT 1
                """.trimIndent().hent(mapOf("revurdering_id" to id.value), session) { true } ?: false
            }
        }

    override fun hentIverksatteMånedsresultater(
        sakId: UUID,
        periode: Periode,
    ): List<IverksatteMånedsresultater> =
        dbMetrics.timeQuery("hentIverksatteHistoriskeInfotrygdMånedsresultater") {
            sessionFactory.withSession { session ->
                """
                    SELECT v.id, v.iverksatt, v.beregning
                    FROM historisk_infotrygd_revurderingsvedtak v
                    JOIN historisk_infotrygd_revurdering r ON r.id = v.revurdering_id
                    WHERE r.sak_id = :sak_id
                      AND r.fra_og_med <= :til_og_med
                      AND r.til_og_med >= :fra_og_med
                    ORDER BY v.iverksatt, v.id
                """.trimIndent().hentListe(
                    mapOf(
                        "sak_id" to sakId,
                        "fra_og_med" to periode.fraOgMed,
                        "til_og_med" to periode.tilOgMed,
                    ),
                    session,
                ) { row ->
                    IverksatteMånedsresultater(
                        vedtakId = HistoriskInfotrygdRevurderingsvedtakId(row.uuid("id")),
                        iverksatt = row.tidspunkt("iverksatt"),
                        månedsresultater = HistoriskInfotrygdBeregningDbJson
                            .deserialize(row.string("beregning"))
                            .månedsresultater
                            .filterKeys { it overlapper periode },
                    )
                }
            }
        }

    override fun defaultTransactionContext(): TransactionContext = sessionFactory.newTransactionContext()

    private fun lagreNyBehandling(revurdering: HistoriskInfotrygdRevurdering, session: Session) {
        """
            INSERT INTO historisk_infotrygd_revurdering (
                id, sak_id, projeksjon_id, fra_og_med, til_og_med, status, saksbehandler,
                opprettet, oppdatert, vedtak_som_revurderes_maanedsvis,
                beregning, attesteringer, vedtaksbrevvalg, vedtaksbrev_fritekst,
                forhandsvarsel
            ) VALUES (
                :id, :sak_id, :projeksjon_id, :fra_og_med, :til_og_med, :status, :saksbehandler,
                :opprettet, :oppdatert,
                CAST(:vedtak_som_revurderes_maanedsvis AS JSONB), CAST(:historisk_beregning_json AS JSONB),
                CAST(:attesteringer AS JSONB), :vedtaksbrevvalg, :vedtaksbrev_fritekst,
                CAST(:forhandsvarsel AS JSONB)
            )
        """.trimIndent().insert(
            mapOf(
                "id" to revurdering.id.value,
                "sak_id" to revurdering.sakId,
                "projeksjon_id" to revurdering.projeksjonId,
                "fra_og_med" to revurdering.periode.fraOgMed,
                "til_og_med" to revurdering.periode.tilOgMed,
                "status" to revurdering.status.name,
                "saksbehandler" to revurdering.saksbehandler.navIdent,
                "opprettet" to revurdering.opprettet,
                "oppdatert" to revurdering.oppdatert,
                "vedtak_som_revurderes_maanedsvis" to
                    HistoriskeVedtakSomRevurderesMånedsvisDbJson
                        .fromDomain(revurdering.vedtakSomRevurderesMånedsvis)
                        .serialize(),
                "historisk_beregning_json" to revurdering.beregning?.let {
                    HistoriskInfotrygdBeregningDbJson.fromDomain(it).serialize()
                },
                "attesteringer" to revurdering.attesteringer.serializeAttesteringer(),
                "vedtaksbrevvalg" to revurdering.vedtaksbrevvalg.tilDbverdi(),
                "vedtaksbrev_fritekst" to revurdering.vedtaksbrevFritekst,
                "forhandsvarsel" to revurdering.forhåndsvarsel.serializeForhåndsvarsel(),
            ),
            session,
        )
    }

    private fun låsSak(sakId: UUID, session: Session) {
        val låstSakId = """
            SELECT id
            FROM sak
            WHERE id = :sak_id
            FOR UPDATE
        """.trimIndent().hent(mapOf("sak_id" to sakId), session) { it.uuid("id") }
        check(låstSakId == sakId) { "Fant ikke sak som skulle låses" }
    }

    private fun finnOverlappendeÅpenBehandling(
        revurdering: HistoriskInfotrygdRevurdering,
        session: Session,
    ): HistoriskInfotrygdRevurdering? = """
        SELECT *
        FROM historisk_infotrygd_revurdering
        WHERE sak_id = :sak_id
          AND status NOT IN ('ATTESTERT', 'AVSLUTTET')
          AND fra_og_med <= :til_og_med
          AND til_og_med >= :fra_og_med
        LIMIT 1
    """.trimIndent().hent(
        mapOf(
            "sak_id" to revurdering.sakId,
            "fra_og_med" to revurdering.periode.fraOgMed,
            "til_og_med" to revurdering.periode.tilOgMed,
        ),
        session,
    ) { it.tilBehandlingRad().toDomain() }

    private fun Row.tilBehandlingRad() = BehandlingRad(
        id = HistoriskInfotrygdRevurderingId(uuid("id")),
        sakId = uuid("sak_id"),
        projeksjonId = uuid("projeksjon_id"),
        periode = Periode.create(localDate("fra_og_med"), localDate("til_og_med")),
        status = HistoriskInfotrygdRevurderingStatus.valueOf(string("status")),
        saksbehandler = NavIdentBruker.Saksbehandler(string("saksbehandler")),
        opprettet = tidspunkt("opprettet"),
        oppdatert = tidspunkt("oppdatert"),
        avslutningsbegrunnelse = if (string("status") == HistoriskInfotrygdRevurderingStatus.AVSLUTTET.name) {
            stringOrNull("begrunnelse")
        } else {
            null
        },
        vedtaksbrevvalg = string("vedtaksbrevvalg").tilVedtaksbrevvalg(),
        vedtaksbrevFritekst = stringOrNull("vedtaksbrev_fritekst"),
        vedtakSomRevurderesMånedsvis = HistoriskeVedtakSomRevurderesMånedsvisDbJson.deserialize(
            string("vedtak_som_revurderes_maanedsvis"),
        ),
        beregning = stringOrNull("beregning")?.let(HistoriskInfotrygdBeregningDbJson::deserialize),
        attesteringer = string("attesteringer").deserializeAttesteringer(),
        forhåndsvarsel = string("forhandsvarsel").deserializeForhåndsvarsel(),
    )

    private data class BehandlingRad(
        val id: HistoriskInfotrygdRevurderingId,
        val sakId: UUID,
        val projeksjonId: UUID,
        val periode: Periode,
        val status: HistoriskInfotrygdRevurderingStatus,
        val saksbehandler: NavIdentBruker.Saksbehandler,
        val opprettet: Tidspunkt,
        val oppdatert: Tidspunkt,
        val avslutningsbegrunnelse: String?,
        val vedtaksbrevvalg: HistoriskInfotrygdVedtaksbrevvalg,
        val vedtaksbrevFritekst: String?,
        val vedtakSomRevurderesMånedsvis: HistoriskeVedtakSomRevurderesMånedsvis,
        val beregning: HistoriskInfotrygdBeregning?,
        val attesteringer: List<HistoriskInfotrygdAttestering>,
        val forhåndsvarsel: no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdForhåndsvarsel,
    ) {
        fun toDomain() =
            HistoriskInfotrygdRevurdering(
                id = id,
                sakId = sakId,
                projeksjonId = projeksjonId,
                periode = periode,
                status = status,
                saksbehandler = saksbehandler,
                opprettet = opprettet,
                oppdatert = oppdatert,
                avslutningsbegrunnelse = avslutningsbegrunnelse,
                vedtaksbrevvalg = vedtaksbrevvalg,
                vedtaksbrevFritekst = vedtaksbrevFritekst,
                vedtakSomRevurderesMånedsvis = vedtakSomRevurderesMånedsvis,
                beregning = beregning,
                attesteringer = attesteringer,
                forhåndsvarsel = forhåndsvarsel,
            )
    }
}

private fun HistoriskInfotrygdVedtaksbrevvalg.tilDbverdi(): String = when (this) {
    HistoriskInfotrygdVedtaksbrevvalg.IKKE_VALGT -> "IKKE_VALGT"
    HistoriskInfotrygdVedtaksbrevvalg.SEND -> "SEND"
    HistoriskInfotrygdVedtaksbrevvalg.IKKE_SEND -> "IKKE_SEND"
}

private fun String.tilVedtaksbrevvalg(): HistoriskInfotrygdVedtaksbrevvalg = when (this) {
    "IKKE_VALGT" -> HistoriskInfotrygdVedtaksbrevvalg.IKKE_VALGT
    "SEND" -> HistoriskInfotrygdVedtaksbrevvalg.SEND
    "IKKE_SEND" -> HistoriskInfotrygdVedtaksbrevvalg.IKKE_SEND
    else -> error("Ukjent historisk vedtaksbrevvalg: $this")
}
