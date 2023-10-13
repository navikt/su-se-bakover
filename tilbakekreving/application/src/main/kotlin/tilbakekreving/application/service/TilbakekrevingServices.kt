package tilbakekreving.application.service

import arrow.core.Either
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import person.domain.PersonRepo
import person.domain.PersonService
import tilbakekreving.application.service.common.TilbakekrevingsbehandlingTilgangstyringService
import tilbakekreving.application.service.consumer.KnyttKravgrunnlagTilSakOgUtbetalingKonsument
import tilbakekreving.application.service.consumer.OpprettOppgaveForTilbakekrevingshendelserKonsument
import tilbakekreving.application.service.forhåndsvarsel.ForhåndsvarsleTilbakekrevingsbehandlingService
import tilbakekreving.application.service.opprett.OpprettTilbakekrevingsbehandlingService
import tilbakekreving.application.service.tilAttestering.TilbakekrevingsbehandlingTilAttesteringService
import tilbakekreving.application.service.vurder.BrevTilbakekrevingsbehandlingService
import tilbakekreving.application.service.vurder.MånedsvurderingerTilbakekrevingsbehandlingService
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagRepo
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlag
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import java.time.Clock

/**
 * Et forsøk på modularisering av [no.nav.su.se.bakover.web.services.Services] der de forskjellige modulene er ansvarlige for å wire opp sine komponenter.
 *
 * Det kan hende vi må splitte denne i en data class + builder.
 */
class TilbakekrevingServices(
    private val clock: Clock,
    private val sessionFactory: SessionFactory,
    private val personRepo: PersonRepo,
    private val personService: PersonService,
    private val kravgrunnlagRepo: KravgrunnlagRepo,
    private val hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    private val tilbakekrevingService: TilbakekrevingService,
    private val sakService: SakService,
    private val oppgaveService: OppgaveService,
    private val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
    private val oppgaveHendelseRepo: OppgaveHendelseRepo,
    private val mapRåttKravgrunnlag: (RåttKravgrunnlag) -> Either<Throwable, Kravgrunnlag>,
    private val hendelseRepo: HendelseRepo,
    private val tilgangstyringService: TilbakekrevingsbehandlingTilgangstyringService = TilbakekrevingsbehandlingTilgangstyringService(
        personRepo = personRepo,
        personService = personService,
    ),
    val brevTilbakekrevingsbehandlingService: BrevTilbakekrevingsbehandlingService = BrevTilbakekrevingsbehandlingService(
        tilgangstyring = tilgangstyringService,
        sakService = sakService,
        tilbakekrevingsbehandlingRepo = tilbakekrevingsbehandlingRepo,
        clock = clock,
    ),
    val knyttKravgrunnlagTilSakOgUtbetalingKonsument: KnyttKravgrunnlagTilSakOgUtbetalingKonsument = KnyttKravgrunnlagTilSakOgUtbetalingKonsument(
        kravgrunnlagRepo = kravgrunnlagRepo,
        tilbakekrevingService = tilbakekrevingService,
        sakService = sakService,
        hendelsekonsumenterRepo = hendelsekonsumenterRepo,
        mapRåttKravgrunnlag = mapRåttKravgrunnlag,
        clock = clock,
        sessionFactory = sessionFactory,
    ),
    val månedsvurderingerTilbakekrevingsbehandlingService: MånedsvurderingerTilbakekrevingsbehandlingService = MånedsvurderingerTilbakekrevingsbehandlingService(
        tilbakekrevingsbehandlingRepo = tilbakekrevingsbehandlingRepo,
        sakService = sakService,
        tilgangstyring = tilgangstyringService,
        clock = clock,
    ),
    val opprettTilbakekrevingsbehandlingService: OpprettTilbakekrevingsbehandlingService = OpprettTilbakekrevingsbehandlingService(
        tilbakekrevingsbehandlingRepo = tilbakekrevingsbehandlingRepo,
        tilgangstyring = tilgangstyringService,
        clock = clock,
        sakService = sakService,
        personService = personService,
        sessionFactory = sessionFactory,
    ),
    val råttKravgrunnlagService: RåttKravgrunnlagService = RåttKravgrunnlagService(
        kravgrunnlagRepo = kravgrunnlagRepo,
        clock = clock,
    ),
    val forhåndsvarsleTilbakekrevingsbehandlingService: ForhåndsvarsleTilbakekrevingsbehandlingService = ForhåndsvarsleTilbakekrevingsbehandlingService(
        tilgangstyring = tilgangstyringService,
        sakService = sakService,
        tilbakekrevingsbehandlingRepo = tilbakekrevingsbehandlingRepo,
        oppgaveService = oppgaveService,
        oppgaveHendelseRepo = oppgaveHendelseRepo,
    ),
    val opprettOppgaveForTilbakekrevingshendelserKonsument: OpprettOppgaveForTilbakekrevingshendelserKonsument = OpprettOppgaveForTilbakekrevingshendelserKonsument(
        sakService = sakService,
        personService = personService,
        oppgaveService = oppgaveService,
        tilbakekrevingsbehandlingHendelseRepo = tilbakekrevingsbehandlingRepo,
        oppgaveHendelseRepo = oppgaveHendelseRepo,
        hendelseRepo = hendelseRepo,
        hendelsekonsumenterRepo = hendelsekonsumenterRepo,
        sessionFactory = sessionFactory,
        clock = clock,
    ),
    val tilbakekrevingsbehandlingTilAttesteringService: TilbakekrevingsbehandlingTilAttesteringService = TilbakekrevingsbehandlingTilAttesteringService(
        tilgangstyring = tilgangstyringService,
        sakService = sakService,
    ),
)
