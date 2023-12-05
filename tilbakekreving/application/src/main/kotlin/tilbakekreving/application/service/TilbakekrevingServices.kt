package tilbakekreving.application.service

import dokument.domain.brev.BrevService
import dokument.domain.hendelser.DokumentHendelseRepo
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingUnderRevurderingService
import person.domain.PersonRepo
import person.domain.PersonService
import tilbakekreving.application.service.avbrutt.AvbrytTilbakekrevingsbehandlingService
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
import tilbakekreving.application.service.kravgrunnlag.RåttKravgrunnlagService
import tilbakekreving.application.service.notat.NotatTilbakekrevingsbehandlingService
import tilbakekreving.application.service.opprett.OpprettTilbakekrevingsbehandlingService
import tilbakekreving.application.service.tilAttestering.TilbakekrevingsbehandlingTilAttesteringService
import tilbakekreving.application.service.tilgang.TilbakekrevingsbehandlingTilgangstyringService
import tilbakekreving.application.service.underkjenn.UnderkjennTilbakekrevingsbehandlingService
import tilbakekreving.application.service.vurder.BrevTilbakekrevingsbehandlingService
import tilbakekreving.application.service.vurder.ForhåndsvisVedtaksbrevTilbakekrevingsbehandlingService
import tilbakekreving.application.service.vurder.MånedsvurderingerTilbakekrevingsbehandlingService
import tilbakekreving.domain.iverksett.Tilbakekrevingsklient
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
    val brevTilbakekrevingsbehandlingService: BrevTilbakekrevingsbehandlingService,
    val forhåndsvisVedtaksbrevTilbakekrevingsbehandlingService: ForhåndsvisVedtaksbrevTilbakekrevingsbehandlingService,
    val knyttKravgrunnlagTilSakOgUtbetalingKonsument: KnyttKravgrunnlagTilSakOgUtbetalingKonsument,
    val månedsvurderingerTilbakekrevingsbehandlingService: MånedsvurderingerTilbakekrevingsbehandlingService,
    val opprettTilbakekrevingsbehandlingService: OpprettTilbakekrevingsbehandlingService,
    val råttKravgrunnlagService: RåttKravgrunnlagService,
    val forhåndsvarsleTilbakekrevingsbehandlingService: ForhåndsvarsleTilbakekrevingsbehandlingService,
    val forhåndsvisForhåndsvarselTilbakekrevingsbehandlingService: ForhåndsvisForhåndsvarselTilbakekrevingsbehandlingService,
    val genererDokumentForForhåndsvarselTilbakekrevingKonsument: GenererDokumentForForhåndsvarselTilbakekrevingKonsument,
    val opprettOppgaveForTilbakekrevingshendelserKonsument: OpprettOppgaveForTilbakekrevingshendelserKonsument,
    val tilbakekrevingsbehandlingTilAttesteringService: TilbakekrevingsbehandlingTilAttesteringService,
    val visUtsendtForhåndsvarselbrevForTilbakekrevingService: VisUtsendtForhåndsvarselbrevForTilbakekrevingService,
    val underkjennTilbakekrevingsbehandlingService: UnderkjennTilbakekrevingsbehandlingService,
    val iverksettTilbakekrevingService: IverksettTilbakekrevingService,
    val avbrytTilbakekrevingsbehandlingService: AvbrytTilbakekrevingsbehandlingService,
    val lukkOppgaveForTilbakekrevingshendelserKonsument: LukkOppgaveForTilbakekrevingshendelserKonsument,
    val oppdaterOppgaveForTilbakekrevingshendelserKonsument: OppdaterOppgaveForTilbakekrevingshendelserKonsument,
    val oppdaterKravgrunnlagService: OppdaterKravgrunnlagService,
    val notatTilbakekrevingsbehandlingService: NotatTilbakekrevingsbehandlingService,
) {
    companion object {
        fun create(
            clock: Clock,
            sessionFactory: SessionFactory,
            personRepo: PersonRepo,
            personService: PersonService,
            kravgrunnlagRepo: KravgrunnlagRepo,
            hendelsekonsumenterRepo: HendelsekonsumenterRepo,
            tilbakekrevingService: TilbakekrevingUnderRevurderingService,
            sakService: SakService,
            oppgaveService: OppgaveService,
            tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
            oppgaveHendelseRepo: OppgaveHendelseRepo,
            mapRåttKravgrunnlag: MapRåttKravgrunnlagTilHendelse,
            hendelseRepo: HendelseRepo,
            dokumentHendelseRepo: DokumentHendelseRepo,
            brevService: BrevService,
            tilbakekrevingsklient: Tilbakekrevingsklient,
        ): TilbakekrevingServices {
            val tilgangstyringService = TilbakekrevingsbehandlingTilgangstyringService(
                personRepo = personRepo,
                personService = personService,
            )
            return TilbakekrevingServices(
                brevTilbakekrevingsbehandlingService = BrevTilbakekrevingsbehandlingService(
                    tilgangstyring = tilgangstyringService,
                    sakService = sakService,
                    tilbakekrevingsbehandlingRepo = tilbakekrevingsbehandlingRepo,
                    clock = clock,
                ),
                forhåndsvisVedtaksbrevTilbakekrevingsbehandlingService = ForhåndsvisVedtaksbrevTilbakekrevingsbehandlingService(
                    tilgangstyring = tilgangstyringService,
                    sakService = sakService,
                    brevService = brevService,
                ),
                knyttKravgrunnlagTilSakOgUtbetalingKonsument = KnyttKravgrunnlagTilSakOgUtbetalingKonsument(
                    kravgrunnlagRepo = kravgrunnlagRepo,
                    tilbakekrevingService = tilbakekrevingService,
                    sakService = sakService,
                    hendelsekonsumenterRepo = hendelsekonsumenterRepo,
                    mapRåttKravgrunnlag = mapRåttKravgrunnlag,
                    clock = clock,
                    sessionFactory = sessionFactory,
                ),
                månedsvurderingerTilbakekrevingsbehandlingService = MånedsvurderingerTilbakekrevingsbehandlingService(
                    tilbakekrevingsbehandlingRepo = tilbakekrevingsbehandlingRepo,
                    sakService = sakService,
                    tilgangstyring = tilgangstyringService,
                    clock = clock,
                ),
                opprettTilbakekrevingsbehandlingService = OpprettTilbakekrevingsbehandlingService(
                    tilbakekrevingsbehandlingRepo = tilbakekrevingsbehandlingRepo,
                    tilgangstyring = tilgangstyringService,
                    clock = clock,
                    sakService = sakService,
                    personService = personService,
                    sessionFactory = sessionFactory,
                ),
                råttKravgrunnlagService = RåttKravgrunnlagService(
                    kravgrunnlagRepo = kravgrunnlagRepo,
                    clock = clock,
                ),
                forhåndsvarsleTilbakekrevingsbehandlingService = ForhåndsvarsleTilbakekrevingsbehandlingService(
                    tilgangstyring = tilgangstyringService,
                    sakService = sakService,
                    tilbakekrevingsbehandlingRepo = tilbakekrevingsbehandlingRepo,
                    oppgaveService = oppgaveService,
                    oppgaveHendelseRepo = oppgaveHendelseRepo,
                    brevService = brevService,
                    sessionFactory = sessionFactory,
                    clock = clock,
                ),
                forhåndsvisForhåndsvarselTilbakekrevingsbehandlingService = ForhåndsvisForhåndsvarselTilbakekrevingsbehandlingService(
                    tilgangstyring = tilgangstyringService,
                    sakService = sakService,
                    brevService = brevService,
                ),
                genererDokumentForForhåndsvarselTilbakekrevingKonsument = GenererDokumentForForhåndsvarselTilbakekrevingKonsument(
                    sakService = sakService,
                    brevService = brevService,
                    tilbakekrevingsbehandlingRepo = tilbakekrevingsbehandlingRepo,
                    dokumentHendelseRepo = dokumentHendelseRepo,
                    hendelsekonsumenterRepo = hendelsekonsumenterRepo,
                    sessionFactory = sessionFactory,
                    clock = clock,
                ),
                opprettOppgaveForTilbakekrevingshendelserKonsument = OpprettOppgaveForTilbakekrevingshendelserKonsument(
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
                tilbakekrevingsbehandlingTilAttesteringService = TilbakekrevingsbehandlingTilAttesteringService(
                    tilgangstyring = tilgangstyringService,
                    sakService = sakService,
                    clock = clock,
                    tilbakekrevingsbehandlingRepo = tilbakekrevingsbehandlingRepo,
                ),
                visUtsendtForhåndsvarselbrevForTilbakekrevingService = VisUtsendtForhåndsvarselbrevForTilbakekrevingService(
                    dokumentHendelseRepo = dokumentHendelseRepo,
                ),
                underkjennTilbakekrevingsbehandlingService = UnderkjennTilbakekrevingsbehandlingService(
                    tilgangstyring = tilgangstyringService,
                    sakService = sakService,
                    clock = clock,
                    tilbakekrevingsbehandlingRepo = tilbakekrevingsbehandlingRepo,
                ),

                iverksettTilbakekrevingService = IverksettTilbakekrevingService(
                    tilgangstyring = tilgangstyringService,
                    sakService = sakService,
                    clock = clock,
                    tilbakekrevingsbehandlingRepo = tilbakekrevingsbehandlingRepo,
                    tilbakekrevingsklient = tilbakekrevingsklient,
                ),
                avbrytTilbakekrevingsbehandlingService = AvbrytTilbakekrevingsbehandlingService(
                    tilgangstyring = tilgangstyringService,
                    sakService = sakService,
                    clock = clock,
                    tilbakekrevingsbehandlingRepo = tilbakekrevingsbehandlingRepo,
                ),
                lukkOppgaveForTilbakekrevingshendelserKonsument = LukkOppgaveForTilbakekrevingshendelserKonsument(
                    sakService = sakService,
                    oppgaveService = oppgaveService,
                    tilbakekrevingsbehandlingHendelseRepo = tilbakekrevingsbehandlingRepo,
                    oppgaveHendelseRepo = oppgaveHendelseRepo,
                    hendelsekonsumenterRepo = hendelsekonsumenterRepo,
                    sessionFactory = sessionFactory,
                    clock = clock,
                ),
                oppdaterOppgaveForTilbakekrevingshendelserKonsument = OppdaterOppgaveForTilbakekrevingshendelserKonsument(
                    sakService = sakService,
                    oppgaveService = oppgaveService,
                    tilbakekrevingsbehandlingHendelseRepo = tilbakekrevingsbehandlingRepo,
                    oppgaveHendelseRepo = oppgaveHendelseRepo,
                    hendelseRepo = hendelseRepo,
                    hendelsekonsumenterRepo = hendelsekonsumenterRepo,
                    sessionFactory = sessionFactory,
                    clock = clock,
                ),
                oppdaterKravgrunnlagService = OppdaterKravgrunnlagService(
                    tilgangstyring = tilgangstyringService,
                    sakService = sakService,
                    tilbakekrevingsbehandlingRepo = tilbakekrevingsbehandlingRepo,
                    clock = clock,
                ),
                notatTilbakekrevingsbehandlingService = NotatTilbakekrevingsbehandlingService(
                    tilgangstyring = tilgangstyringService,
                    sakService = sakService,
                    tilbakekrevingsbehandlingRepo = tilbakekrevingsbehandlingRepo,
                    clock = clock,
                ),
            )
        }
    }
}
