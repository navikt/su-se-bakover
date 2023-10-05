package tilbakekreving.application.service

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.behandling.vurderTilbakekrevingsbehandling
import no.nav.su.se.bakover.domain.sak.SakService
import org.slf4j.LoggerFactory
import tilbakekreving.domain.VurdertTilbakekrevingsbehandling
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import tilbakekreving.domain.vurdert.KunneIkkeVurdereTilbakekrevingsbehandling
import tilbakekreving.domain.vurdert.OppdaterMånedsvurderingerCommand
import java.time.Clock

class MånedsvurderingerTilbakekrevingsbehandlingService(
    private val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
    private val sakService: SakService,
    private val tilgangstyring: TilbakekrevingsbehandlingTilgangstyringService,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun vurder(
        command: OppdaterMånedsvurderingerCommand,
    ): Either<KunneIkkeVurdereTilbakekrevingsbehandling, VurdertTilbakekrevingsbehandling> {
        val sakId = command.sakId

        tilgangstyring.assertHarTilgangTilSak(sakId).onLeft {
            log.info("Kunne ikke vurdere tilbakekrevingsbehandling, mangler tilgang til sak. Kommandoen var: $command. Feil: $it")
            return KunneIkkeVurdereTilbakekrevingsbehandling.IkkeTilgang(it).left()
        }
        val sak = sakService.hentSak(sakId).getOrElse {
            throw IllegalStateException("Kunne ikke vurdere tilbakekrevingsbehandling, fant ikke sak. Kommandoen var: $command")
        }
        return sak.vurderTilbakekrevingsbehandling(command, clock).let { pair ->
            pair.second.right().onRight {
                tilbakekrevingsbehandlingRepo.lagre(pair.first)
            }
        }
    }
}
