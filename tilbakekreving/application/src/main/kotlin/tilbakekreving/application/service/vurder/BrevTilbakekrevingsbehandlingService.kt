package tilbakekreving.application.service.vurder

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.oppdaterVedtaksbrev
import org.slf4j.LoggerFactory
import tilbakekreving.application.service.tilgang.TilbakekrevingsbehandlingTilgangstyringService
import tilbakekreving.domain.TilbakekrevingsbehandlingRepo
import tilbakekreving.domain.UnderBehandling
import tilbakekreving.domain.vedtaksbrev.KunneIkkeOppdatereVedtaksbrev
import tilbakekreving.domain.vedtaksbrev.OppdaterVedtaksbrevCommand
import java.time.Clock

class BrevTilbakekrevingsbehandlingService(
    private val tilgangstyring: TilbakekrevingsbehandlingTilgangstyringService,
    private val sakService: SakService,
    private val tilbakekrevingsbehandlingRepo: TilbakekrevingsbehandlingRepo,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun lagreBrevtekst(
        command: OppdaterVedtaksbrevCommand,
    ): Either<KunneIkkeOppdatereVedtaksbrev, UnderBehandling.Utfylt> {
        val sakId = command.sakId
        tilgangstyring.assertHarTilgangTilSak(sakId).onLeft {
            return KunneIkkeOppdatereVedtaksbrev.IkkeTilgang(it).left()
        }

        val sak = sakService.hentSak(sakId).getOrElse {
            throw IllegalStateException("Kunne ikke oppdatere vedtaksbrev for tilbakekrevingsbehandling, fant ikke sak. Kommandoen var: $command")
        }
        if (sak.versjon != command.klientensSisteSaksversjon) {
            log.info("Vedtaksbrev for tilbakekreving - Sakens versjon (${sak.versjon}) er ulik saksbehandlers versjon. Command: $command")
        }

        return sak.oppdaterVedtaksbrev(command, clock).let { pair ->
            pair.second.right().onRight {
                tilbakekrevingsbehandlingRepo.lagre(pair.first, command.toDefaultHendelsesMetadata())
            }
        }
    }
}
