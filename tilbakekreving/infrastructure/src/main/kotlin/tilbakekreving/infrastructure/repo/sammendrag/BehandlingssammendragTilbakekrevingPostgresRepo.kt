package tilbakekreving.infrastructure.repo.sammendrag

import arrow.core.Tuple5
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Behandlingssammendrag
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withOptionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.kravgrunnlag.repo.BehandlingssammendragTilbakekrevingRepo
import tilbakekreving.infrastructure.repo.AvbruttTilbakekrevingsbehandlingHendelsestype
import tilbakekreving.infrastructure.repo.ForhåndsvarsletTilbakekrevingsbehandlingHendelsestype
import tilbakekreving.infrastructure.repo.IverksattTilbakekrevingsbehandlingHendelsestype
import tilbakekreving.infrastructure.repo.NotatTilbakekrevingsbehandlingHendelsestype
import tilbakekreving.infrastructure.repo.OppdatertKravgrunnlagPåTilbakekrevingHendelse
import tilbakekreving.infrastructure.repo.OppdatertVedtaksbrevTilbakekrevingsbehandlingHendelsestype
import tilbakekreving.infrastructure.repo.OpprettetTilbakekrevingsbehandlingHendelsestype
import tilbakekreving.infrastructure.repo.TilbakekrevingsbehandlingTilAttesteringHendelsestype
import tilbakekreving.infrastructure.repo.UnderkjentTilbakekrevingsbehandlingHendelsestype
import tilbakekreving.infrastructure.repo.VurdertTilbakekrevingsbehandlingHendelsestype
import tilbakekreving.infrastructure.repo.alleTilbakekrevingsbehandlingHendelser
import tilbakekreving.infrastructure.repo.toBehandlingssamendragStatus
import tilbakekreving.infrastructure.repo.toTilbakekrevingHendelsestype

class BehandlingssammendragTilbakekrevingPostgresRepo(
    private val dbMetrics: DbMetrics,
    private val sessionFactory: SessionFactory,
) : BehandlingssammendragTilbakekrevingRepo {

    private val åpneStatuser = listOf(
        Behandlingssammendrag.Behandlingsstatus.UNDER_BEHANDLING,
        Behandlingssammendrag.Behandlingsstatus.TIL_ATTESTERING,
        Behandlingssammendrag.Behandlingsstatus.UNDERKJENT,
    )

    private val ferdigeStatuser = listOf(
        Behandlingssammendrag.Behandlingsstatus.IVERKSATT,
        Behandlingssammendrag.Behandlingsstatus.AVBRUTT,
    )

    override fun hentÅpne(sessionContext: SessionContext?): List<Behandlingssammendrag> {
        return hentBehandlingssammendrag(sessionContext).filter { it.status in åpneStatuser }
    }

    override fun hentFerdige(sessionContext: SessionContext?): List<Behandlingssammendrag> {
        return hentBehandlingssammendrag(sessionContext).filter { it.status in ferdigeStatuser }
    }

    private fun List<Tuple5<Long, Hendelsestype, Saksnummer, TilbakekrevingsbehandlingId, Tidspunkt>>.toBehandlingssammendrag(): List<Behandlingssammendrag> =
        this.map {
            Behandlingssammendrag(
                saksnummer = it.third,
                periode = null,
                behandlingstype = Behandlingssammendrag.Behandlingstype.TILBAKEKREVING,
                behandlingStartet = it.fifth,
                status = it.second.toBehandlingssamendragStatus(),
            )
        }

    private val alleTilbakekrevingsbehandlingstyper = alleTilbakekrevingsbehandlingHendelser.joinToString { "'${it.value}'" }
    private fun hentBehandlingssammendrag(
        sessionContext: SessionContext?,
    ): List<Behandlingssammendrag> {
        return dbMetrics.timeQuery("hentBehandlingssammendragForTilbakekreving") {
            sessionContext.withOptionalSession(sessionFactory) {
                //language=PostgreSQL
                """
                WITH LatestHendelser AS (
                    SELECT
                        sakId,
                        MAX(versjon) AS max_versjon
                    FROM
                        hendelse
                    WHERE
                        type IN ($alleTilbakekrevingsbehandlingstyper)
                    GROUP BY sakId
                )
                SELECT
                    s.saksnummer,
                    (h.hendelsestidspunkt) AS hendelsestidspunkt,
                    h.type
                FROM
                    hendelse h
                JOIN
                    sak s ON h.sakId = s.id
                JOIN
                    LatestHendelser lh ON h.sakId = lh.sakId AND h.versjon = lh.max_versjon

                """.trimIndent().hentListe(emptyMap(), it) {
                    Behandlingssammendrag(
                        saksnummer = Saksnummer(it.long("saksnummer")),
                        // TODO jah: Vi kan potensielt utvide denne til å hente ut perioden fra kravgrunnlaget. Se BehandlingssammendragKravgrunnlagPostgresRepo
                        periode = null,
                        behandlingstype = Behandlingssammendrag.Behandlingstype.TILBAKEKREVING,
                        behandlingStartet = it.tidspunkt("hendelsestidspunkt"),
                        status = when (it.string("type").toTilbakekrevingHendelsestype()) {
                            OpprettetTilbakekrevingsbehandlingHendelsestype -> Behandlingssammendrag.Behandlingsstatus.UNDER_BEHANDLING
                            ForhåndsvarsletTilbakekrevingsbehandlingHendelsestype -> Behandlingssammendrag.Behandlingsstatus.UNDER_BEHANDLING
                            VurdertTilbakekrevingsbehandlingHendelsestype -> Behandlingssammendrag.Behandlingsstatus.UNDER_BEHANDLING
                            OppdatertVedtaksbrevTilbakekrevingsbehandlingHendelsestype -> Behandlingssammendrag.Behandlingsstatus.UNDER_BEHANDLING
                            TilbakekrevingsbehandlingTilAttesteringHendelsestype -> Behandlingssammendrag.Behandlingsstatus.TIL_ATTESTERING
                            UnderkjentTilbakekrevingsbehandlingHendelsestype -> Behandlingssammendrag.Behandlingsstatus.UNDERKJENT
                            IverksattTilbakekrevingsbehandlingHendelsestype -> Behandlingssammendrag.Behandlingsstatus.IVERKSATT
                            AvbruttTilbakekrevingsbehandlingHendelsestype -> Behandlingssammendrag.Behandlingsstatus.AVBRUTT
                            OppdatertKravgrunnlagPåTilbakekrevingHendelse -> Behandlingssammendrag.Behandlingsstatus.UNDER_BEHANDLING
                            NotatTilbakekrevingsbehandlingHendelsestype -> Behandlingssammendrag.Behandlingsstatus.UNDER_BEHANDLING
                            else -> throw IllegalStateException("Uventet hendelsestype ${it.string("type")}")
                        },
                    )
                }
            }
        }
    }
}
