package tilbakekreving.presentation

import dokument.domain.brev.BrevService
import dokument.domain.hendelser.DokumentHendelseRepo
import no.nav.su.se.bakover.common.domain.auth.SamlTokenProvider
import no.nav.su.se.bakover.common.domain.config.TilbakekrevingConfig
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkRepo
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import tilbakekreving.application.service.TilbakekrevingServices
import tilbakekreving.infrastructure.client.TilbakekrevingClients
import tilbakekreving.infrastructure.repo.TilbakekrevingRepos
import tilbakekreving.infrastructure.repo.kravgrunnlag.MapRåttKravgrunnlagTilHendelse
import tilgangstyring.application.TilgangstyringService
import java.time.Clock

/**
 * Et forsøk på modularisering av [no.nav.su.se.bakover.web.services.Services] og [no.nav.su.se.bakover.domain.DatabaseRepos] der de forskjellige modulene er ansvarlige for å wire opp sine komponenter.
 */
class Tilbakekrevingskomponenter(
    val repos: TilbakekrevingRepos,
    val services: TilbakekrevingServices,
    val clients: TilbakekrevingClients,
) {
    companion object {
        fun create(
            clock: Clock,
            sessionFactory: SessionFactory,
            hendelsekonsumenterRepo: HendelsekonsumenterRepo,
            sakService: SakService,
            oppgaveService: OppgaveService,
            oppgaveHendelseRepo: OppgaveHendelseRepo,
            mapRåttKravgrunnlagPåSakHendelse: MapRåttKravgrunnlagTilHendelse,
            hendelseRepo: HendelseRepo,
            dokumentHendelseRepo: DokumentHendelseRepo,
            brevService: BrevService,
            sakStatistikkRepo: SakStatistikkRepo,
            tilbakekrevingConfig: TilbakekrevingConfig,
            dbMetrics: DbMetrics,
            samlTokenProvider: SamlTokenProvider,
            tilgangstyringService: TilgangstyringService,
        ): Tilbakekrevingskomponenter {
            val repos = TilbakekrevingRepos.create(
                clock = clock,
                sessionFactory = sessionFactory,
                hendelseRepo = hendelseRepo,
                hendelsekonsumenterRepo = hendelsekonsumenterRepo,
                dokumentHendelseRepo = dokumentHendelseRepo,
                dbMetrics = dbMetrics,
            )
            val clients = TilbakekrevingClients.create(
                baseUrl = tilbakekrevingConfig.soap.url,
                samlTokenProvider = samlTokenProvider,
                clock = clock,
            )
            return Tilbakekrevingskomponenter(
                repos = repos,
                services = TilbakekrevingServices.create(
                    clock = clock,
                    sessionFactory = sessionFactory,
                    kravgrunnlagRepo = repos.kravgrunnlagRepo,
                    hendelsekonsumenterRepo = hendelsekonsumenterRepo,
                    sakService = sakService,
                    oppgaveService = oppgaveService,
                    tilbakekrevingsbehandlingRepo = repos.tilbakekrevingsbehandlingRepo,
                    oppgaveHendelseRepo = oppgaveHendelseRepo,
                    mapRåttKravgrunnlag = mapRåttKravgrunnlagPåSakHendelse,
                    dokumentHendelseRepo = dokumentHendelseRepo,
                    brevService = brevService,
                    tilbakekrevingsklient = clients.tilbakekrevingsklient,
                    tilgangstyringService = tilgangstyringService,
                    sakStatistikkRepo = sakStatistikkRepo,
                ),
                clients = clients,
            )
        }
    }
}
