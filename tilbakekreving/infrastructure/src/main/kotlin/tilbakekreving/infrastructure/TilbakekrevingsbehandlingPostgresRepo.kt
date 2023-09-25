package tilbakekreving.infrastructure

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withOptionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import tilbakekreving.domain.Tilbakekrevingsbehandling
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.opprett.OpprettetTilbakekrevingsbehandling
import tilbakekreving.domain.opprett.OpprettetTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import java.util.UUID

private val OpprettTilbakekrevingsbehandlingHendelsestype = Hendelsestype("OPPRETT_TILBAKEKREVINGSBEHANDLING")

class TilbakekrevingsbehandlingPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val hendelseRepo: HendelseRepo,
) : TilbakekrevingsbehandlingRepo {
    override fun opprett(
        hendelse: OpprettetTilbakekrevingsbehandlingHendelse,
        sessionContext: SessionContext?,
    ) {
        sessionContext.withOptionalSession(sessionFactory) {
            // TODO jah: Kanskje vi bør flytte denne til interfacet?
            (hendelseRepo as HendelsePostgresRepo).persisterSakshendelse(
                hendelse = hendelse,
                type = OpprettTilbakekrevingsbehandlingHendelsestype,
                data = "{}",
                sessionContext = null,
            )
        }
    }

    override fun hentForSak(sakId: UUID, sessionContext: SessionContext?): List<Tilbakekrevingsbehandling> {
        return (hendelseRepo as HendelsePostgresRepo)
            .hentHendelserForSakIdOgType(
                sakId = sakId,
                type = OpprettTilbakekrevingsbehandlingHendelsestype,
                sessionContext = sessionContext ?: sessionFactory.newSessionContext(),
            )
            .toOpprettetTilbakekrevingsbehandlingHendelse()
            .toBehandling()
    }
}

private fun List<PersistertHendelse>.toOpprettetTilbakekrevingsbehandlingHendelse(): List<OpprettetTilbakekrevingsbehandlingHendelse> =
    this.map {
        OpprettetTilbakekrevingsbehandlingHendelse(
            hendelseId = it.hendelseId,
            sakId = it.sakId!!,
            hendelsestidspunkt = it.hendelsestidspunkt,
            versjon = it.versjon,
            meta = it.hendelseMetadata,

            // TODO - Disse må vi hente fra repoet, disse blir enda ikke lagret. Legger på random data midlertidig
            id = TilbakekrevingsbehandlingId.generer(),
            opprettetAv = NavIdentBruker.Saksbehandler(navIdent = "hent faktiske saksbehandler"),
            kravgrunnlagsId = "",
        )
    }

private fun List<OpprettetTilbakekrevingsbehandlingHendelse>.toBehandling(): List<OpprettetTilbakekrevingsbehandling> =
    this.map {
        it.toDomain(
            kravgrunnlag = Kravgrunnlag(
                saksnummer = Saksnummer(nummer = 9999),
                kravgrunnlagId = "",
                vedtakId = "",
                kontrollfelt = "",
                status = Kravgrunnlag.KravgrunnlagStatus.Annulert,
                behandler = NavIdentBruker.Saksbehandler(navIdent = "hent faktiske saksbehandler"),
                utbetalingId = UUID30.randomUUID(),
                grunnlagsperioder = listOf(),
            ),
        )
    }
