package tilbakekreving.infrastructure.repo.sammendrag

import kotliquery.Row
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Behandlingssammendrag
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withOptionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunktOrNull
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import tilbakekreving.domain.kravgrunnlag.repo.BehandlingssammendragKravgrunnlagOgTilbakekrevingRepo
import tilbakekreving.infrastructure.repo.AvbruttTilbakekrevingsbehandlingHendelsestype
import tilbakekreving.infrastructure.repo.IverksattTilbakekrevingsbehandlingHendelsestype
import tilbakekreving.infrastructure.repo.alleTilbakekrevingsbehandlingHendelser
import tilbakekreving.infrastructure.repo.kravgrunnlag.toKravgrunnlagStatus
import tilbakekreving.infrastructure.repo.toBehandlingssamendragStatus
import tilbakekreving.infrastructure.repo.toTilbakekrevingHendelsestype
import java.time.LocalDate

class BehandlingssammendragKravgrunnlagOgTilbakekrevingPostgresRepo(
    private val dbMetrics: DbMetrics,
    val sessionFactory: SessionFactory,
) : BehandlingssammendragKravgrunnlagOgTilbakekrevingRepo {

    override fun hentÅpne(
        sessionContext: SessionContext?,
    ): List<Behandlingssammendrag> {
        return hentBehandlingssammendrag(
            sessionContext = sessionContext,
        ) { row ->
            val saksnummer = Saksnummer(row.long("saksnummer"))
            val periode = Periode.create(
                fraOgMed = LocalDate.parse(row.string("fraOgMed")),
                tilOgMed = LocalDate.parse(row.string("tilOgMed")),
            )
            val tilbakekrevingstype: Hendelsestype? = row.stringOrNull("tilbakekrevingstype")
                ?.toTilbakekrevingHendelsestype()
            val kravgrunnlagstatus = row.string("status").toKravgrunnlagStatus()
            val kravgrunnlagTidspunkt = row.tidspunktOrNull("statusTidspunkt") ?: row.tidspunkt("kravgrunnlagTidspunkt")
            val tilbakekrevingstidspunkt = row.tidspunktOrNull("tilbakekrevingstidspunkt")
            when {
                tilbakekrevingstype == null && kravgrunnlagstatus.erAvsluttet() -> null
                tilbakekrevingstype != null && tilbakekrevingstype in avsluttetTilbakekrevingstyper -> null
                tilbakekrevingstype == null && !kravgrunnlagstatus.erAvsluttet() -> Behandlingssammendrag(
                    saksnummer = saksnummer,
                    periode = periode,
                    behandlingstype = Behandlingssammendrag.Behandlingstype.KRAVGRUNNLAG,
                    behandlingStartet = kravgrunnlagTidspunkt,
                    status = Behandlingssammendrag.Behandlingsstatus.ÅPEN,
                )
                else -> Behandlingssammendrag(
                    saksnummer = saksnummer,
                    periode = periode,
                    behandlingstype = Behandlingssammendrag.Behandlingstype.TILBAKEKREVING,
                    behandlingStartet = tilbakekrevingstidspunkt!!,
                    status = tilbakekrevingstype!!.toBehandlingssamendragStatus(),
                )
            }
        }
    }

    private val avsluttetTilbakekrevingstyper = listOf(AvbruttTilbakekrevingsbehandlingHendelsestype, IverksattTilbakekrevingsbehandlingHendelsestype)

    override fun hentFerdige(
        sessionContext: SessionContext?,
    ): List<Behandlingssammendrag> {
        return hentBehandlingssammendrag(
            sessionContext = sessionContext,
        ) { row ->
            val saksnummer = Saksnummer(row.long("saksnummer"))
            val periode = Periode.create(
                fraOgMed = LocalDate.parse(row.string("fraOgMed")),
                tilOgMed = LocalDate.parse(row.string("tilOgMed")),
            )
            val tilbakekrevingstype: Hendelsestype? = row.stringOrNull("tilbakekrevingstype")
                ?.toTilbakekrevingHendelsestype()
            val kravgrunnlagstatus = row.string("status").toKravgrunnlagStatus()
            val kravgrunnlagTidspunkt = row.tidspunktOrNull("statusTidspunkt") ?: row.tidspunkt("kravgrunnlagTidspunkt")
            val tilbakekrevingstidspunkt = row.tidspunktOrNull("tilbakekrevingstidspunkt")
            when {
                tilbakekrevingstype == null && !kravgrunnlagstatus.erAvsluttet() -> null
                tilbakekrevingstype != null && tilbakekrevingstype !in avsluttetTilbakekrevingstyper -> null
                tilbakekrevingstype == AvbruttTilbakekrevingsbehandlingHendelsestype -> Behandlingssammendrag(
                    saksnummer = saksnummer,
                    periode = periode,
                    behandlingstype = Behandlingssammendrag.Behandlingstype.TILBAKEKREVING,
                    behandlingStartet = tilbakekrevingstidspunkt,
                    status = Behandlingssammendrag.Behandlingsstatus.AVBRUTT,
                )
                tilbakekrevingstype == null && kravgrunnlagstatus.erAvsluttet() -> Behandlingssammendrag(
                    saksnummer = saksnummer,
                    periode = periode,
                    behandlingstype = Behandlingssammendrag.Behandlingstype.KRAVGRUNNLAG,
                    behandlingStartet = kravgrunnlagTidspunkt,
                    status = Behandlingssammendrag.Behandlingsstatus.AVSLUTTET,
                )
                else -> Behandlingssammendrag(
                    saksnummer = saksnummer,
                    periode = periode,
                    behandlingstype = Behandlingssammendrag.Behandlingstype.TILBAKEKREVING,
                    behandlingStartet = tilbakekrevingstidspunkt!!,
                    status = tilbakekrevingstype!!.toBehandlingssamendragStatus(),
                )
            }
        }
    }

    private val alleTilbakekrevingsbehandlingstyper =
        alleTilbakekrevingsbehandlingHendelser.joinToString { "'${it.value}'" }

    private fun hentBehandlingssammendrag(
        sessionContext: SessionContext?,
        rowMapping: (Row) -> Behandlingssammendrag?,
    ): List<Behandlingssammendrag> {
        return dbMetrics.timeQuery("hentBehandlingssammendrag") {
            sessionContext.withOptionalSession(sessionFactory) { session ->
                """
                -- Henter alle kravgrunnlag som er knyttet til en sak og eksternVedtakId, og som er det siste kravgrunnlaget innenfor en sak og eksternVedtakId.    
                WITH KravgrunnlagGruppertPåVedtakId AS (
                SELECT
                    h.sakId,
                    (h.data->'kravgrunnlag'->>'eksternTidspunkt')::timestamptz AS kravgrunnlagTidspunkt,
                    (h.data->'kravgrunnlag'->>'status') AS status,
                    (h.data->'kravgrunnlag'->>'eksternVedtakId') AS eksternVedtakId,
                    h.hendelseId,
                     (SELECT MIN((elements->>'fraOgMed')::date)
                     FROM jsonb_array_elements(h.data->'kravgrunnlag'->'grunnlagsperioder') AS elements) AS fraOgMed,
                    (SELECT MAX((elements->>'tilOgMed')::date)
                     FROM jsonb_array_elements(h.data->'kravgrunnlag'->'grunnlagsperioder') AS elements) AS tilOgMed
                FROM
                    hendelse h
                INNER JOIN (
                    SELECT
                        sakId,
                        (data->'kravgrunnlag'->>'eksternVedtakId') as eksternVedtakId,
                        MAX((data->'kravgrunnlag'->>'eksternTidspunkt')::timestamptz) AS max_eksternTidspunkt
                    FROM
                        hendelse
                    WHERE
                        type = 'KNYTTET_KRAVGRUNNLAG_TIL_SAK' AND
                        data ?? 'kravgrunnlag'
                    GROUP BY sakId, (data->'kravgrunnlag'->>'eksternVedtakId')
                ) AS latest_hendelse ON h.sakId = latest_hendelse.sakId
                    -- TODO jah: Dette er en svakhet. Dersom flere med samme eksternVedtakId har dette tidspunktet, vil vi hente alle.
                    AND (data->'kravgrunnlag'->>'eksternTidspunkt')::timestamptz = latest_hendelse.max_eksternTidspunkt
                    AND (data->'kravgrunnlag'->>'eksternVedtakId') = latest_hendelse.eksternVedtakId
                    WHERE h.type = 'KNYTTET_KRAVGRUNNLAG_TIL_SAK'
                ),
                KravgrunnlagStatusGruppertPåVedtakId AS (
                SELECT
                    h.sakId,
                    (h.data->>'eksternVedtakId') as eksternVedtakId,
                    (h.data->>'eksternTidspunkt')::timestamptz AS statusTidspunkt,
                    h.data->>'status' AS status
                FROM
                    hendelse h
                INNER JOIN (
                    SELECT
                        sakId,
                        (data->>'eksternVedtakId') as eksternVedtakId,
                        MAX((data->>'eksternTidspunkt')::timestamptz) AS max_eksternTidspunkt
                    FROM
                        hendelse
                    WHERE
                        type = 'KNYTTET_KRAVGRUNNLAG_TIL_SAK' AND
                        data ?? 'status'
                    GROUP BY sakId, (data->>'eksternVedtakId')
                ) AS latest_hendelse ON h.sakId = latest_hendelse.sakId
                    -- TODO jah: Dette er en svakhet. Dersom flere med samme eksternVedtakId har dette tidspunktet, vil vi hente alle.
                    AND (h.data->>'eksternTidspunkt')::timestamptz = latest_hendelse.max_eksternTidspunkt
                    AND (h.data->>'eksternVedtakId') = latest_hendelse.eksternVedtakId
                    WHERE h.type = 'KNYTTET_KRAVGRUNNLAG_TIL_SAK'
                ),
                SisteOppdaterteKravgrunnlag AS (
                    SELECT
                        (h.data->>'behandlingsId')::uuid AS behandlingsId,
                        (h.data->>'kravgrunnlagPåSakHendelseId')::uuid AS kravgrunnlagPåSakHendelseId
                    FROM
                       hendelse h
                    INNER JOIN (
                        SELECT
                            (data->>'behandlingsId') AS behandlingsId,
                            MAX(versjon) AS max_versjon
                        FROM
                            hendelse
                        WHERE
                            type = 'OPPDATERT_KRAVGRUNNLAG'
                        GROUP BY (data->>'behandlingsId')
                    ) AS subquery
                    ON
                        (h.data->>'behandlingsId') = subquery.behandlingsId
                        AND (h.data->>'behandlingsId') = subquery.behandlingsId
                ),
                SisteTilbakekrevingsbehandlingHendelse AS (
                    SELECT
                        h.sakId,
                        h.type as tilbakekrevingstype,
                        (h.data->>'behandlingsId')::uuid AS behandlingsId,
                        h.hendelsestidspunkt AS tilbakekrevingstidspunkt,
                        COALESCE(lkr.kravgrunnlagPåSakHendelseId, (ht.data->>'kravgrunnlagPåSakHendelseId')::uuid) AS kravgrunnlagPåSakHendelseId
                    FROM
                        hendelse h
                    INNER JOIN (
                        SELECT
                            (data->>'behandlingsId') AS behandlingsId,
                            MAX(versjon) AS max_versjon
                        FROM
                            hendelse
                        WHERE
                            type IN ('OPPRETTET_TILBAKEKREVINGSBEHANDLING', 'FORHÅNDSVARSLET_TILBAKEKREVINGSBEHANDLING', 'VURDERT_TILBAKEKREVINGSBEHANDLING', 'OPPDATERT_VEDTAKSBREV_TILBAKEKREVINGSBEHANDLING', 'TILBAKEKREVINGSBEHANDLING_TIL_ATTESTERING', 'UNDERKJENT_TILBAKEKREVINGSBEHANDLING', 'IVERKSATT_TILBAKEKREVINGSBEHANDLING', 'AVBRUTT_TILBAKEKREVINGSBEHANDLING', 'OPPDATERT_KRAVGRUNNLAG', 'NOTAT_TILBAKEKREVINGSBEHANDLING')
                            GROUP BY (data->>'behandlingsId')
                    ) AS subquery ON h.versjon = subquery.max_versjon AND (h.data->>'behandlingsId') = subquery.behandlingsId
                    LEFT JOIN hendelse ht ON (h.data->>'behandlingsId')::uuid = (ht.data->>'behandlingsId')::uuid AND ht.type = 'OPPRETTET_TILBAKEKREVINGSBEHANDLING'
                    LEFT JOIN SisteOppdaterteKravgrunnlag lkr ON (h.data->>'behandlingsId')::uuid = lkr.behandlingsId
                )
                SELECT
                    kravgrunnlag.sakId,
                    sak.saksnummer,
                    CASE
                        WHEN status.statusTidspunkt > kravgrunnlag.kravgrunnlagTidspunkt THEN status.status
                        ELSE kravgrunnlag.status
                    END AS status,
                    kravgrunnlag.kravgrunnlagTidspunkt,
                    kravgrunnlag.fraOgMed,
                    kravgrunnlag.tilOgMed,
                    status.statusTidspunkt,
                    sth.tilbakekrevingstype,
                    sth.tilbakekrevingstidspunkt
                FROM
                    KravgrunnlagGruppertPåVedtakId kravgrunnlag
                JOIN
                    sak ON kravgrunnlag.sakId = sak.id
                LEFT JOIN
                    KravgrunnlagStatusGruppertPåVedtakId status ON kravgrunnlag.sakId = status.sakId
                    AND kravgrunnlag.eksternVedtakId = status.eksternVedtakId
                LEFT JOIN
                    SisteTilbakekrevingsbehandlingHendelse sth ON sth.kravgrunnlagPåSakHendelseId = kravgrunnlag.hendelseId
                    AND sth.sakId = kravgrunnlag.sakId
                GROUP BY
                    kravgrunnlag.sakId,
                    sak.saksnummer,
                    kravgrunnlag.status,
                    kravgrunnlag.kravgrunnlagTidspunkt,
                    kravgrunnlag.fraOgMed,
                    kravgrunnlag.tilOgMed,
                    status.status,
                    status.statusTidspunkt,
                    sth.tilbakekrevingstype,
                    sth.tilbakekrevingstidspunkt
                """.trimIndent().hentListe(
                    session = session,
                    rowMapping = rowMapping,
                ).filterNotNull()
            }
        }
    }
}

/*
Test CTE KravgrunnlagGruppertPåVedtakId:

drop table if exists hendelse;
DROP TABLE IF EXISTS sak;

CREATE TABLE sak (
  id uuid,
  saksnummer bigint
);

CREATE TABLE hendelse (
  sakId uuid,
  hendelseId uuid,
  type text,
  versjon INT,
  hendelsestidspunkt timestamptz,
  data JSONB
);
INSERT INTO sak (id, saksnummer) VALUES
('c5833c99-9d03-4620-99ea-7a88dd5c124d', 1),
('ad40e2f2-d25e-4dc9-bf1c-60f0327f0308', 2),
('b97dca96-8b3d-4114-939c-f11126078bf7', 3);

INSERT INTO hendelse (sakId, hendelseId, versjon, type, hendelsestidspunkt, data) VALUES
('c5833c99-9d03-4620-99ea-7a88dd5c124d', 'e5fe7b27-7173-4431-a025-ca66d4f006e5', 1, 'KNYTTET_KRAVGRUNNLAG_TIL_SAK', '2021-01-01T00:00:00Z', '{"kravgrunnlag": {"status":"NY", "eksternTidspunkt": "2021-01-01T12:00:00Z", "eksternVedtakId": "EV1", "grunnlagsperioder": [{"fraOgMed": "2021-01-01", "tilOgMed": "2021-01-31"}]}}'),
('c5833c99-9d03-4620-99ea-7a88dd5c124d', 'd7b31757-ea3b-4d04-b457-4b3d34132c31', 2, 'OPPRETTET_TILBAKEKREVINGSBEHANDLING', '2021-01-01T00:00:01Z', '{"behandlingsId": "3ec1f54e-845c-4b3f-9988-7e7895b4e73c", "kravgrunnlagPåSakHendelseId": "e5fe7b27-7173-4431-a025-ca66d4f006e5"}'),
('c5833c99-9d03-4620-99ea-7a88dd5c124d', '783372b7-8a7d-48dd-8801-3b72c959ae02', 3, 'KNYTTET_KRAVGRUNNLAG_TIL_SAK', '2021-01-01T00:00:02Z', '{"status":"SPER", "eksternTidspunkt": "2021-01-01T13:00:00Z", "eksternVedtakId": "EV1"}'),
('c5833c99-9d03-4620-99ea-7a88dd5c124d', '122af0b4-aeaa-4953-a0e2-4d764aee2334', 4, 'KNYTTET_KRAVGRUNNLAG_TIL_SAK', '2021-01-01T00:00:03Z', '{"kravgrunnlag": {"status":"ENDR", "eksternTidspunkt": "2021-01-01T14:00:00Z", "eksternVedtakId": "EV1", "grunnlagsperioder": [{"fraOgMed": "2021-01-01", "tilOgMed": "2021-01-31"}]}}'),
('c5833c99-9d03-4620-99ea-7a88dd5c124d', 'b423f626-bd52-468e-a89b-b70d31dc322a', 5, 'OPPDATERT_KRAVGRUNNLAG', '2021-01-01T00:00:04Z', '{"behandlingsId": "3ec1f54e-845c-4b3f-9988-7e7895b4e73c", "kravgrunnlagPåSakHendelseId": "122af0b4-aeaa-4953-a0e2-4d764aee2334"}'),
('c5833c99-9d03-4620-99ea-7a88dd5c124d', '9267a656-3c6b-443a-ad5e-7858a452766b', 6, 'KNYTTET_KRAVGRUNNLAG_TIL_SAK', '2021-01-01T00:00:05Z', '{"status":"SPER", "eksternTidspunkt": "2021-01-01T15:00:00Z", "eksternVedtakId": "EV1"}'),
('c5833c99-9d03-4620-99ea-7a88dd5c124d', '3ae57568-8649-40eb-9609-2cd7068a2e81', 7, 'KNYTTET_KRAVGRUNNLAG_TIL_SAK', '2021-01-01T00:00:06Z', '{"kravgrunnlag": {"status":"ENDR", "eksternTidspunkt": "2021-01-01T16:00:00Z", "eksternVedtakId": "EV1", "grunnlagsperioder": [{"fraOgMed": "2021-01-01", "tilOgMed": "2021-01-31"}]}}'),
('c5833c99-9d03-4620-99ea-7a88dd5c124d', '1131b694-91fb-410d-91f9-b56883de9468', 8, 'OPPDATERT_KRAVGRUNNLAG', '2021-01-01T00:00:07Z', '{"behandlingsId": "3ec1f54e-845c-4b3f-9988-7e7895b4e73c", "kravgrunnlagPåSakHendelseId": "3ae57568-8649-40eb-9609-2cd7068a2e81"}'),

('ad40e2f2-d25e-4dc9-bf1c-60f0327f0308', '4158b8df-c219-48c5-8bf4-5c46881abb98', 1, 'KNYTTET_KRAVGRUNNLAG_TIL_SAK', '2021-01-01T00:00:08Z', '{"kravgrunnlag": {"status":"NY", "eksternTidspunkt": "2021-01-01T12:00:00Z", "eksternVedtakId": "EV2", "grunnlagsperioder": [{"fraOgMed": "2021-02-01", "tilOgMed": "2021-02-28"}]}}'),
('ad40e2f2-d25e-4dc9-bf1c-60f0327f0308', 'a801a6d9-1f9f-4ca6-ba53-e4b78f42bf68', 2, 'KNYTTET_KRAVGRUNNLAG_TIL_SAK', '2021-01-01T00:00:09Z', '{"status":"SPER", "eksternTidspunkt": "2021-01-01T13:00:00Z", "eksternVedtakId": "EV2"}'),
('ad40e2f2-d25e-4dc9-bf1c-60f0327f0308', '10e600b5-4d0f-4c43-9b4a-e6c082d17d58', 3, 'KNYTTET_KRAVGRUNNLAG_TIL_SAK', '2021-01-01T00:00:10Z', '{"status":"AVSL", "eksternTidspunkt": "2021-01-01T14:00:00Z", "eksternVedtakId": "EV2"}'),

('b97dca96-8b3d-4114-939c-f11126078bf7', 'd470f733-3c61-4604-95f9-cc752da7b83e', 1, 'KNYTTET_KRAVGRUNNLAG_TIL_SAK', '2021-01-01T00:00:11Z', '{"kravgrunnlag": {"status":"NY", "eksternTidspunkt": "2021-01-01T12:00:00Z", "eksternVedtakId": "EV3", "grunnlagsperioder": [{"fraOgMed": "2021-03-01", "tilOgMed": "2021-03-31"}]}}');

 */
