package tilbakekreving.application.service.vurder

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.behandling.vurderTilbakekrevingsbehandling
import no.nav.su.se.bakover.domain.sak.SakService
import org.slf4j.LoggerFactory
import tilbakekreving.application.service.common.TilbakekrevingsbehandlingTilgangstyringService
import tilbakekreving.domain.UnderBehandling
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import tilbakekreving.domain.vurdert.KunneIkkeLagreBrevtekst
import tilbakekreving.domain.vurdert.OppdaterBrevtekstCommand
import java.time.Clock

class BrevTilbakekrevingsbehandlingService(
    private val tilgangstyring: TilbakekrevingsbehandlingTilgangstyringService,
    private val sakService: SakService,
    private val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun lagreBrevtekst(
        command: OppdaterBrevtekstCommand,
    ): Either<KunneIkkeLagreBrevtekst, UnderBehandling.Utfylt> {
        tilgangstyring.assertHarTilgangTilSak(command.sakId).onLeft {
            return KunneIkkeLagreBrevtekst.IkkeTilgang(it).left()
        }

        val sak = sakService.hentSak(command.sakId).getOrElse {
            throw IllegalStateException("Kunne ikke vurdere tilbakekrevingsbehandling, fant ikke sak. Kommandoen var: $command")
        }

        return sak.vurderTilbakekrevingsbehandling(command, clock).let { pair ->
            pair.second.right().onRight {
                tilbakekrevingsbehandlingRepo.lagre(pair.first)
            }
        }
    }
}
