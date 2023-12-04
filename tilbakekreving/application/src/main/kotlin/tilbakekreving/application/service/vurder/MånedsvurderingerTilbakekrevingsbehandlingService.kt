package tilbakekreving.application.service.vurder

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.vurderTilbakekrevingsbehandling
import org.slf4j.LoggerFactory
import tilbakekreving.application.service.common.TilbakekrevingsbehandlingTilgangstyringService
import tilbakekreving.domain.UnderBehandling
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import tilbakekreving.domain.vurdert.KunneIkkeVurdereTilbakekrevingsbehandling
import tilbakekreving.domain.vurdert.VurderCommand
import java.time.Clock

class MånedsvurderingerTilbakekrevingsbehandlingService(
    private val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
    private val sakService: SakService,
    private val tilgangstyring: TilbakekrevingsbehandlingTilgangstyringService,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun vurder(
        command: VurderCommand,
    ): Either<KunneIkkeVurdereTilbakekrevingsbehandling, UnderBehandling> {
        val sakId = command.sakId

        tilgangstyring.assertHarTilgangTilSak(sakId).onLeft {
            log.info("Kunne ikke vurdere tilbakekrevingsbehandling, mangler tilgang til sak. Kommandoen var: $command. Feil: $it")
            return KunneIkkeVurdereTilbakekrevingsbehandling.IkkeTilgang(it).left()
        }
        val sak = sakService.hentSak(sakId).getOrElse {
            throw IllegalStateException("Kunne ikke vurdere tilbakekrevingsbehandling, fant ikke sak. Kommandoen var: $command")
        }
        if (sak.versjon != command.klientensSisteSaksversjon) {
            log.info("Vurdering av tilbakekreving - Sakens versjon (${sak.versjon}) er ulik saksbehandlers versjon. Command: $command")
        }
        return sak.vurderTilbakekrevingsbehandling(command, clock).map { pair ->
            tilbakekrevingsbehandlingRepo.lagre(pair.first)
            pair.second
        }
    }
}
