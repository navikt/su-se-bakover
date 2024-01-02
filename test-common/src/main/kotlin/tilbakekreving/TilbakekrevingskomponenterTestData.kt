package no.nav.su.se.bakover.test.tilbakekreving

import dokument.domain.brev.BrevService
import dokument.domain.hendelser.DokumentHendelseRepo
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingUnderRevurderingService
import person.domain.PersonService
import tilbakekreving.application.service.TilbakekrevingServices
import tilbakekreving.infrastructure.client.TilbakekrevingClients
import tilbakekreving.infrastructure.repo.TilbakekrevingRepos
import tilbakekreving.infrastructure.repo.kravgrunnlag.MapRåttKravgrunnlagTilHendelse
import tilbakekreving.presentation.Tilbakekrevingskomponenter
import java.time.Clock

/**
 * Database er ikke stubbet.
 */
fun tilbakekrevingskomponenterMedClientStubs(
    clock: Clock,
    sessionFactory: SessionFactory,
    personService: PersonService,
    hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    tilbakekrevingUnderRevurderingService: TilbakekrevingUnderRevurderingService,
    sakService: SakService,
    oppgaveService: OppgaveService,
    oppgaveHendelseRepo: OppgaveHendelseRepo,
    mapRåttKravgrunnlagPåSakHendelse: MapRåttKravgrunnlagTilHendelse,
    hendelseRepo: HendelseRepo,
    dokumentHendelseRepo: DokumentHendelseRepo,
    brevService: BrevService,
): Tilbakekrevingskomponenter {
    val repos = TilbakekrevingRepos.create(
        clock = clock,
        sessionFactory = sessionFactory,
        hendelseRepo = hendelseRepo,
        hendelsekonsumenterRepo = hendelsekonsumenterRepo,
        oppgaveHendelseRepo = oppgaveHendelseRepo,
        dokumentHendelseRepo = dokumentHendelseRepo,
    )
    val clients = TilbakekrevingClients(
        tilbakekrevingsklient = TilbakekrevingsklientStub(
            clock = clock,
        ),
    )
    return Tilbakekrevingskomponenter(
        repos = repos,
        services = TilbakekrevingServices.create(
            clock = clock,
            sessionFactory = sessionFactory,
            personService = personService,
            kravgrunnlagRepo = repos.kravgrunnlagRepo,
            hendelsekonsumenterRepo = hendelsekonsumenterRepo,
            tilbakekrevingService = tilbakekrevingUnderRevurderingService,
            sakService = sakService,
            oppgaveService = oppgaveService,
            tilbakekrevingsbehandlingRepo = repos.tilbakekrevingsbehandlingRepo,
            oppgaveHendelseRepo = oppgaveHendelseRepo,
            mapRåttKravgrunnlag = mapRåttKravgrunnlagPåSakHendelse,
            dokumentHendelseRepo = dokumentHendelseRepo,
            brevService = brevService,
            tilbakekrevingsklient = clients.tilbakekrevingsklient,
        ),
        clients = clients,
    )
}
