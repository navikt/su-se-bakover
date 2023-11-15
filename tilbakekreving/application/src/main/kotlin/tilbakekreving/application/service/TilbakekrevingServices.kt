package tilbakekreving.application.service

import dokument.domain.brev.BrevService
import dokument.domain.hendelser.DokumentHendelseRepo
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import person.domain.PersonRepo
import person.domain.PersonService
import tilbakekreving.application.service.avbrutt.AvbrytTilbakekrevingsbehandlingService
import tilbakekreving.application.service.common.TilbakekrevingsbehandlingTilgangstyringService
import tilbakekreving.application.service.consumer.GenererDokumentForForhåndsvarselTilbakekrevingKonsument
import tilbakekreving.application.service.consumer.KnyttKravgrunnlagTilSakOgUtbetalingKonsument
import tilbakekreving.application.service.consumer.LukkOppgaveForTilbakekrevingshendelserKonsument
import tilbakekreving.application.service.consumer.OppdaterOppgaveForTilbakekrevingshendelserKonsument
import tilbakekreving.application.service.consumer.OpprettOppgaveForTilbakekrevingshendelserKonsument
import tilbakekreving.application.service.forhåndsvarsel.ForhåndsvarsleTilbakekrevingsbehandlingService
import tilbakekreving.application.service.forhåndsvarsel.ForhåndsvisForhåndsvarselTilbakekrevingsbehandlingService
import tilbakekreving.application.service.forhåndsvarsel.VisUtsendtForhåndsvarselbrevForTilbakekrevingService
import tilbakekreving.application.service.iverksett.IverksettTilbakekrevingService
import tilbakekreving.application.service.kravgrunnlag.OppdaterKravgrunnlagService
import tilbakekreving.application.service.opprett.OpprettTilbakekrevingsbehandlingService
import tilbakekreving.application.service.tilAttestering.TilbakekrevingsbehandlingTilAttesteringService
import tilbakekreving.application.service.underkjenn.UnderkjennTilbakekrevingsbehandlingService
import tilbakekreving.application.service.vurder.BrevTilbakekrevingsbehandlingService
import tilbakekreving.application.service.vurder.ForhåndsvisVedtaksbrevTilbakekrevingsbehandlingService
import tilbakekreving.application.service.vurder.MånedsvurderingerTilbakekrevingsbehandlingService
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagRepo
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import tilbakekreving.infrastructure.repo.kravgrunnlag.MapRåttKravgrunnlagTilHendelse
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
    private val mapRåttKravgrunnlag: MapRåttKravgrunnlagTilHendelse,
    private val hendelseRepo: HendelseRepo,
    private val dokumentHendelseRepo: DokumentHendelseRepo,
    private val brevService: BrevService,
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
    val forhåndsvisVedtaksbrevTilbakekrevingsbehandlingService: ForhåndsvisVedtaksbrevTilbakekrevingsbehandlingService = ForhåndsvisVedtaksbrevTilbakekrevingsbehandlingService(
        tilgangstyring = tilgangstyringService,
        sakService = sakService,
        brevService = brevService,
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
        brevService = brevService,
        sessionFactory = sessionFactory,
        clock = clock,
    ),
    val forhåndsvisForhåndsvarselTilbakekrevingsbehandlingService: ForhåndsvisForhåndsvarselTilbakekrevingsbehandlingService = ForhåndsvisForhåndsvarselTilbakekrevingsbehandlingService(
        tilgangstyring = tilgangstyringService,
        sakService = sakService,
        brevService = brevService,
    ),
    val genererDokumentForForhåndsvarselTilbakekrevingKonsument: GenererDokumentForForhåndsvarselTilbakekrevingKonsument = GenererDokumentForForhåndsvarselTilbakekrevingKonsument(
        sakService = sakService,
        brevService = brevService,
        tilbakekrevingsbehandlingRepo = tilbakekrevingsbehandlingRepo,
        dokumentHendelseRepo = dokumentHendelseRepo,
        hendelsekonsumenterRepo = hendelsekonsumenterRepo,
        sessionFactory = sessionFactory,
        clock = clock,
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
        clock = clock,
        tilbakekrevingsbehandlingRepo = tilbakekrevingsbehandlingRepo,
    ),
    val visUtsendtForhåndsvarselbrevForTilbakekrevingService: VisUtsendtForhåndsvarselbrevForTilbakekrevingService = VisUtsendtForhåndsvarselbrevForTilbakekrevingService(
        dokumentHendelseRepo = dokumentHendelseRepo,
    ),
    val underkjennTilbakekrevingsbehandlingService: UnderkjennTilbakekrevingsbehandlingService = UnderkjennTilbakekrevingsbehandlingService(
        tilgangstyring = tilgangstyringService,
        sakService = sakService,
        clock = clock,
        tilbakekrevingsbehandlingRepo = tilbakekrevingsbehandlingRepo,
    ),
    val iverksettTilbakekrevingService: IverksettTilbakekrevingService = IverksettTilbakekrevingService(
        tilgangstyring = tilgangstyringService,
        sakService = sakService,
        clock = clock,
        tilbakekrevingsbehandlingRepo = tilbakekrevingsbehandlingRepo,
    ),
    val avbrytTilbakekrevingsbehandlingService: AvbrytTilbakekrevingsbehandlingService = AvbrytTilbakekrevingsbehandlingService(
        tilgangstyring = tilgangstyringService,
        sakService = sakService,
        clock = clock,
        tilbakekrevingsbehandlingRepo = tilbakekrevingsbehandlingRepo,
    ),
    val lukkOppgaveForTilbakekrevingshendelserKonsument: LukkOppgaveForTilbakekrevingshendelserKonsument = LukkOppgaveForTilbakekrevingshendelserKonsument(
        sakService = sakService,
        oppgaveService = oppgaveService,
        tilbakekrevingsbehandlingHendelseRepo = tilbakekrevingsbehandlingRepo,
        oppgaveHendelseRepo = oppgaveHendelseRepo,
        hendelsekonsumenterRepo = hendelsekonsumenterRepo,
        sessionFactory = sessionFactory,
        clock = clock,
    ),
    val oppdaterOppgaveForTilbakekrevingshendelserKonsument: OppdaterOppgaveForTilbakekrevingshendelserKonsument = OppdaterOppgaveForTilbakekrevingshendelserKonsument(
        sakService = sakService,
        oppgaveService = oppgaveService,
        tilbakekrevingsbehandlingHendelseRepo = tilbakekrevingsbehandlingRepo,
        oppgaveHendelseRepo = oppgaveHendelseRepo,
        hendelseRepo = hendelseRepo,
        hendelsekonsumenterRepo = hendelsekonsumenterRepo,
        sessionFactory = sessionFactory,
        clock = clock,
    ),
    val oppdaterKravgrunnlagService: OppdaterKravgrunnlagService = OppdaterKravgrunnlagService(
        tilgangstyring = tilgangstyringService,
        sakService = sakService,
        tilbakekrevingsbehandlingRepo = tilbakekrevingsbehandlingRepo,
        clock = clock,
    ),
)
