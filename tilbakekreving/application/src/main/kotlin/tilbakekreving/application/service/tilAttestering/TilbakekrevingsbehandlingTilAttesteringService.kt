package tilbakekreving.application.service.tilAttestering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.domain.sak.SakService
import tilbakekreving.application.service.common.TilbakekrevingsbehandlingTilgangstyringService
import tilbakekreving.domain.TilbakekrevingsbehandlingTilAttestering
import tilbakekreving.domain.tilAttestering.KunneIkkeSendeTilAttestering
import tilbakekreving.domain.tilAttestering.TilbakekrevingsbehandlingTilAttesteringCommand

class TilbakekrevingsbehandlingTilAttesteringService(
    private val tilgangstyring: TilbakekrevingsbehandlingTilgangstyringService,
    private val sakService: SakService,
) {
    fun tilAttestering(
        command: TilbakekrevingsbehandlingTilAttesteringCommand,
    ): Either<KunneIkkeSendeTilAttestering, TilbakekrevingsbehandlingTilAttestering> {
        tilgangstyring.assertHarTilgangTilSak(command.sakId).onLeft {
            return KunneIkkeSendeTilAttestering.IkkeTilgang(it).left()
        }
        val id = command.tilbakekrevingsbehandlingId

        val sak = sakService.hentSak(command.sakId).getOrElse {
            throw IllegalStateException("Kunne ikke sende forhåndsvarsel for tilbakekrevingsbehandling $id, fant ikke sak ${command.sakId}")
        }

        @Suppress("UNUSED_VARIABLE")
        val behandling = (
            sak.behandlinger.tilbakekrevinger.hent(id)
                ?: throw IllegalStateException("Kunne ikke sende forhåndsvarsel for tilbakekrevingsbehandling $id, fant ikke tilbakekrevingsbehandling på sak ${command.sakId}")
            ).let {
            // TODO jah: Legg til sjekk på at behandlingen er i riktig tilstand
        }

        TODO(" jah: Send til attestering. Husk lukk/opprett oppgave.")
    }
}
