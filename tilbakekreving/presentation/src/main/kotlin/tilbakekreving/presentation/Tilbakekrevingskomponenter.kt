package tilbakekreving.presentation

import dokument.domain.brev.BrevService
import dokument.domain.hendelser.DokumentHendelseRepo
import no.nav.su.se.bakover.common.domain.config.TilbakekrevingConfig
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingUnderRevurderingService
import person.domain.PersonRepo
import person.domain.PersonService
import tilbakekreving.application.service.TilbakekrevingServices
import tilbakekreving.infrastructure.client.TilbakekrevingClients
import tilbakekreving.infrastructure.repo.TilbakekrevingRepos
import tilbakekreving.infrastructure.repo.kravgrunnlag.MapRåttKravgrunnlagTilHendelse
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
            personRepo: PersonRepo,
            personService: PersonService,
            hendelsekonsumenterRepo: HendelsekonsumenterRepo,
            tilbakekrevingUnderRevurderingService: TilbakekrevingUnderRevurderingService,
            sakService: SakService,
            oppgaveService: OppgaveService,
            oppgaveHendelseRepo: OppgaveHendelseRepo,
            mapRåttKravgrunnlag: MapRåttKravgrunnlagTilHendelse,
            hendelseRepo: HendelseRepo,
            dokumentHendelseRepo: DokumentHendelseRepo,
            brevService: BrevService,
            tilbakekrevingConfig: TilbakekrevingConfig,
        ): Tilbakekrevingskomponenter {
            val repos = TilbakekrevingRepos.create(
                clock = clock,
                sessionFactory = sessionFactory,
                hendelseRepo = hendelseRepo,
                hendelsekonsumenterRepo = hendelsekonsumenterRepo,
                oppgaveHendelseRepo = oppgaveHendelseRepo,
                dokumentHendelseRepo = dokumentHendelseRepo,
            )
            val clients = TilbakekrevingClients.create(
                tilbakekrevingConfig = tilbakekrevingConfig,
                clock = clock,
            )
            return Tilbakekrevingskomponenter(
                repos = repos,
                services = TilbakekrevingServices.create(
                    clock = clock,
                    sessionFactory = sessionFactory,
                    personRepo = personRepo,
                    personService = personService,
                    kravgrunnlagRepo = repos.kravgrunnlagRepo,
                    hendelsekonsumenterRepo = hendelsekonsumenterRepo,
                    tilbakekrevingService = tilbakekrevingUnderRevurderingService,
                    sakService = sakService,
                    oppgaveService = oppgaveService,
                    tilbakekrevingsbehandlingRepo = repos.tilbakekrevingsbehandlingRepo,
                    oppgaveHendelseRepo = oppgaveHendelseRepo,
                    mapRåttKravgrunnlag = mapRåttKravgrunnlag,
                    dokumentHendelseRepo = dokumentHendelseRepo,
                    brevService = brevService,
                    tilbakekrevingsklient = clients.tilbakekrevingsklient,
                ),
                clients = clients,
            )
        }
    }
}
