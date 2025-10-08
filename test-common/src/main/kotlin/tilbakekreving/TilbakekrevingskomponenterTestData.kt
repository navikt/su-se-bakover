package no.nav.su.se.bakover.test.tilbakekreving

import dokument.domain.brev.BrevService
import dokument.domain.hendelser.DokumentHendelseRepo
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.statistikk.SakStatistikkRepo
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import no.nav.su.se.bakover.test.persistence.dbMetricsStub
import tilbakekreving.application.service.TilbakekrevingServices
import tilbakekreving.infrastructure.client.TilbakekrevingClients
import tilbakekreving.infrastructure.repo.TilbakekrevingRepos
import tilbakekreving.infrastructure.repo.kravgrunnlag.MapRåttKravgrunnlagTilHendelse
import tilbakekreving.presentation.Tilbakekrevingskomponenter
import tilgangstyring.application.TilgangstyringService
import java.time.Clock

/**
 * Database er ikke stubbet.
 */
fun tilbakekrevingskomponenterMedClientStubs(
    clock: Clock,
    sessionFactory: SessionFactory,
    hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    sakService: SakService,
    oppgaveService: OppgaveService,
    oppgaveHendelseRepo: OppgaveHendelseRepo,
    mapRåttKravgrunnlagPåSakHendelse: MapRåttKravgrunnlagTilHendelse,
    hendelseRepo: HendelseRepo,
    sakStatistikkRepo: SakStatistikkRepo,
    dokumentHendelseRepo: DokumentHendelseRepo,
    brevService: BrevService,
    tilgangstyringService: TilgangstyringService,
): Tilbakekrevingskomponenter {
    val repos = TilbakekrevingRepos.create(
        clock = clock,
        sessionFactory = sessionFactory,
        hendelseRepo = hendelseRepo,
        hendelsekonsumenterRepo = hendelsekonsumenterRepo,
        dokumentHendelseRepo = dokumentHendelseRepo,
        dbMetrics = dbMetricsStub,
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
