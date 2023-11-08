package tilbakekreving.application.service.forhåndsvarsel

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import org.slf4j.LoggerFactory
import tilbakekreving.application.service.common.TilbakekrevingsbehandlingTilgangstyringService
import tilbakekreving.domain.KanForhåndsvarsle
import tilbakekreving.domain.Tilbakekrevingsbehandling
import tilbakekreving.domain.forhåndsvarsel.ForhåndsvarselTilbakekrevingsbehandlingCommand
import tilbakekreving.domain.forhåndsvarsel.KunneIkkeForhåndsvarsle
import tilbakekreving.domain.leggTilForhåndsvarsel
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import java.time.Clock

class ForhåndsvarsleTilbakekrevingsbehandlingService(
    private val tilgangstyring: TilbakekrevingsbehandlingTilgangstyringService,
    private val sakService: SakService,
    private val brevService: BrevService,
    private val oppgaveService: OppgaveService,
    private val oppgaveHendelseRepo: OppgaveHendelseRepo,
    private val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun forhåndsvarsle(
        command: ForhåndsvarselTilbakekrevingsbehandlingCommand,
    ): Either<KunneIkkeForhåndsvarsle, Tilbakekrevingsbehandling> {
        val sakId = command.sakId
        val id = command.behandlingId
        tilgangstyring.assertHarTilgangTilSak(sakId).onLeft {
            return KunneIkkeForhåndsvarsle.IkkeTilgang(it).left()
        }

        val sak = sakService.hentSak(sakId).getOrElse {
            throw IllegalStateException("Kunne ikke sende forhåndsvarsel for tilbakekrevingsbehandling, fant ikke sak. Command: $command")
        }
        if (sak.versjon != command.klientensSisteSaksversjon) {
            log.info("Forhåndsvis forhåndsvarsel - Sakens versjon (${sak.versjon}) er ulik saksbehandlers versjon. Command: $command")
        }

        val behandling = (
            sak.behandlinger.tilbakekrevinger.hent(id)
                ?: throw IllegalStateException("Kunne ikke sende forhåndsvarsel for tilbakekrevingsbehandling. Fant ikke Tilbakekrevingsbehandling $id. Command: $command")
            )
            .let {
                it as? KanForhåndsvarsle
                    ?: throw IllegalStateException("Kunne ikke forhåndsvarsle tilbakekrevingsbehandling $id, behandlingen er ikke i tilstanden til attestering. Command: $command")
            }

        behandling.leggTilForhåndsvarsel(
            command = command,
            tidligereHendelsesId = behandling.hendelseId,
            nesteVersjon = sak.versjon.inc(),
            clock = clock,
        ).let {
            tilbakekrevingsbehandlingRepo.lagre(it.first)
            return it.second.right()
        }
    }
}
