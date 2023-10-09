package tilbakekreving.application.service.forhåndsvarsel

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import org.slf4j.LoggerFactory
import tilbakekreving.application.service.common.TilbakekrevingsbehandlingTilgangstyringService
import tilbakekreving.domain.Tilbakekrevingsbehandling
import tilbakekreving.domain.forhåndsvarsel.ForhåndsvarselTilbakekrevingsbehandlingCommand
import tilbakekreving.domain.forhåndsvarsel.KunneIkkeForhåndsvarsle
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo

class ForhåndsvarsleTilbakekrevingsbehandlingService(
    private val tilgangstyring: TilbakekrevingsbehandlingTilgangstyringService,
    private val sakService: SakService,
    private val oppgaveService: OppgaveService,
    private val oppgaveHendelseRepo: OppgaveHendelseRepo,
    private val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun forhåndsvarsle(command: ForhåndsvarselTilbakekrevingsbehandlingCommand): Either<KunneIkkeForhåndsvarsle, Tilbakekrevingsbehandling> {
        tilgangstyring.assertHarTilgangTilSak(command.sakId).onLeft {
            return KunneIkkeForhåndsvarsle.IkkeTilgang(it).left()
        }

        val sak = sakService.hentSak(command.sakId).getOrElse {
            throw IllegalStateException("Kunne ikke sende forhåndsvarsel for tilbakekrevingsbehandling, fant ikke sak ${command.sakId}")
        }

        // TODO - oppgaven som skal bli oppdatert
        val oppgaveId = tilbakekrevingsbehandlingRepo.hentForSak(sakId = command.sakId).hentOppgaveId()
        println("$sak, $oppgaveId")

        TODO()
    }
}
