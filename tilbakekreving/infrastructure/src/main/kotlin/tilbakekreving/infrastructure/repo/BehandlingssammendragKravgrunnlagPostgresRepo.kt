package tilbakekreving.infrastructure.repo

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
import tilbakekreving.domain.kravgrunnlag.repo.BehandlingssammendragKravgrunnlagRepo
import tilbakekreving.infrastructure.repo.kravgrunnlag.KnyttetKravgrunnlagTilSakHendelsestype
import tilbakekreving.infrastructure.repo.kravgrunnlag.toKravgrunnlagStatus
import java.time.LocalDate

class BehandlingssammendragKravgrunnlagPostgresRepo(
    private val dbMetrics: DbMetrics,
    val sessionFactory: SessionFactory,
) : BehandlingssammendragKravgrunnlagRepo {

    override fun hentBehandlingssammendrag(
        sessionContext: SessionContext?,
    ): List<Behandlingssammendrag> {
        return dbMetrics.timeQuery("hentKravgrunnlagOgIverksatteTilbakekrevinger") {
            sessionContext.withOptionalSession(sessionFactory) { session ->
                """
                WITH SisteKravgrunnlag AS (
                SELECT
                    h.sakId,
                    (h.data->'kravgrunnlag'->>'eksternTidspunkt')::timestamptz AS kravgrunnlagTidspunkt,
                    (h.data->'kravgrunnlag'->>'status') AS status,
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
                        MAX((data->'kravgrunnlag'->>'eksternTidspunkt')::timestamptz) AS max_eksternTidspunkt
                    FROM
                        hendelse
                    WHERE
                        type = '$KnyttetKravgrunnlagTilSakHendelsestype' AND
                        data ?? 'kravgrunnlag'
                    GROUP BY sakId
                ) AS latest_hendelse ON h.sakId = latest_hendelse.sakId
                    AND (data->'kravgrunnlag'->>'eksternTidspunkt')::timestamptz = latest_hendelse.max_eksternTidspunkt
                ),
                SisteStatus AS (
                SELECT
                    h.sakId,
                    (h.data->>'eksternTidspunkt')::timestamptz AS statusTidspunkt,
                    h.data->>'status' AS status
                FROM
                    hendelse h
                INNER JOIN (
                    SELECT
                        sakId,
                        MAX((data->>'eksternTidspunkt')::timestamptz) AS max_eksternTidspunkt
                    FROM
                        hendelse
                    WHERE
                        type = '$KnyttetKravgrunnlagTilSakHendelsestype' AND
                        data ?? 'status'
                    GROUP BY sakId
                ) AS latest_hendelse ON h.sakId = latest_hendelse.sakId
                    AND (h.data->>'eksternTidspunkt')::timestamptz = latest_hendelse.max_eksternTidspunkt
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
                    bool_or(hendelseIverksatt.hendelseId IS NOT NULL) AS erIverksatt
                FROM
                    SisteKravgrunnlag kravgrunnlag
                JOIN
                    sak ON kravgrunnlag.sakId = sak.id
                LEFT JOIN
                    SisteStatus status ON kravgrunnlag.sakId = status.sakId
                LEFT JOIN
                    hendelse hendelseOpprettetOrOppdatert ON kravgrunnlag.hendelseid = (hendelseOpprettetOrOppdatert.data->>'kravgrunnlagPåSakHendelseId')::uuid AND
                    hendelseOpprettetOrOppdatert.type IN ('$OpprettetTilbakekrevingsbehandlingHendelsestype', '$OppdatertKravgrunnlagPåTilbakekrevingHendelse')
                LEFT JOIN
                    hendelse hendelseIverksatt ON hendelseOpprettetOrOppdatert.data->>'behandlingsId' = hendelseIverksatt.data->>'behandlingsId' AND
                    hendelseIverksatt.type = '$IverksattTilbakekrevingsbehandlingHendelsestype'
                GROUP BY
                    kravgrunnlag.sakId,
                    kravgrunnlag.kravgrunnlagTidspunkt,
                    kravgrunnlag.status,
                    sak.saksnummer,
                    kravgrunnlag.fraOgMed,
                    kravgrunnlag.tilOgMed,
                    status.status,
                    status.statusTidspunkt
                """.trimIndent().hentListe(
                    session = session,
                ) { row ->
                    Behandlingssammendrag(
                        saksnummer = Saksnummer(row.long("saksnummer")),
                        periode = Periode.create(
                            fraOgMed = LocalDate.parse(row.string("fraOgMed")),
                            tilOgMed = LocalDate.parse(row.string("tilOgMed")),
                        ),
                        behandlingstype = Behandlingssammendrag.Behandlingstype.KRAVGRUNNLAG,
                        behandlingStartet = row.tidspunktOrNull("statusTidspunkt") ?: row.tidspunkt("kravgrunnlagTidspunkt"),
                        status = row.string("status").toKravgrunnlagStatus().let {
                            if (it.erAvsluttet()) {
                                Behandlingssammendrag.Behandlingsstatus.AVSLUTTET
                            } else if (row.boolean("erIverksatt")) {
                                Behandlingssammendrag.Behandlingsstatus.IVERKSATT
                            } else {
                                Behandlingssammendrag.Behandlingsstatus.ÅPEN
                            }
                        },
                    )
                }
            }
        }
    }
}
