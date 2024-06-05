package tilbakekreving.application.service.vurder

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.hentTilbakekrevingsbehandling
import org.slf4j.LoggerFactory
import tilbakekreving.domain.TilbakekrevingsbehandlingTilAttestering
import tilbakekreving.domain.vedtaksbrev.ForhåndsvisVedtaksbrevCommand
import tilbakekreving.domain.vedtaksbrev.KunneIkkeForhåndsviseVedtaksbrev
import tilbakekreving.domain.vedtaksbrev.VedtaksbrevTilbakekrevingsbehandlingDokumentCommand
import tilgangstyring.application.TilgangstyringService

class ForhåndsvisVedtaksbrevTilbakekrevingsbehandlingService(
    private val tilgangstyring: TilgangstyringService,
    private val sakService: SakService,
    private val brevService: BrevService,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun forhåndsvisVedtaksbrev(
        command: ForhåndsvisVedtaksbrevCommand,
    ): Either<KunneIkkeForhåndsviseVedtaksbrev, PdfA> {
        tilgangstyring.assertHarTilgangTilSak(command.sakId).onLeft {
            return KunneIkkeForhåndsviseVedtaksbrev.IkkeTilgang(it).left()
        }

        val sak = sakService.hentSak(command.sakId).getOrElse {
            throw IllegalStateException("Kunne ikke oppdatere vedtaksbrev for tilbakekrevingsbehandling, fant ikke sak. Kommandoen var: $command")
        }

        val behandling = sak.hentTilbakekrevingsbehandling(command.behandlingId)
            ?: return KunneIkkeForhåndsviseVedtaksbrev.FantIkkeBehandling.left()

        return brevService.lagDokument(
            VedtaksbrevTilbakekrevingsbehandlingDokumentCommand(
                fødselsnummer = sak.fnr,
                saksnummer = sak.saksnummer,
                correlationId = command.correlationId,
                sakId = sak.id,
                saksbehandler = when (behandling) {
                    is TilbakekrevingsbehandlingTilAttestering -> behandling.sendtTilAttesteringAv
                    else -> command.utførtAv
                },
                attestant = when (behandling) {
                    is TilbakekrevingsbehandlingTilAttestering -> command.utførtAv
                    else -> null
                },
                fritekst = behandling.vedtaksbrevvalg?.fritekst,
                vurderingerMedKrav = behandling.vurderingerMedKrav ?: return KunneIkkeForhåndsviseVedtaksbrev.VurderingerFinnesIkkePåBehandlingen.left(),
            ),
        )
            .map { it.generertDokument }
            .mapLeft { KunneIkkeForhåndsviseVedtaksbrev.FeilVedGenereringAvDokument }
    }
}
