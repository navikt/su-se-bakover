package tilbakekreving.application.service.vurder

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import dokument.domain.brev.BrevService
import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.hentTilbakekrevingsbehandling
import org.slf4j.LoggerFactory
import tilbakekreving.application.service.tilgang.TilbakekrevingsbehandlingTilgangstyringService
import tilbakekreving.domain.ErUtfylt
import tilbakekreving.domain.IverksattTilbakekrevingsbehandling
import tilbakekreving.domain.TilbakekrevingsbehandlingTilAttestering
import tilbakekreving.domain.forhåndsvarsel.VedtaksbrevTilbakekrevingsbehandlingDokumentCommand
import tilbakekreving.domain.vurdert.ForhåndsvisVedtaksbrevCommand
import tilbakekreving.domain.vurdert.KunneIkkeForhåndsviseVedtaksbrev

class ForhåndsvisVedtaksbrevTilbakekrevingsbehandlingService(
    private val tilgangstyring: TilbakekrevingsbehandlingTilgangstyringService,
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

        val vurderingMedKrav = when (behandling) {
            is TilbakekrevingsbehandlingTilAttestering -> behandling.vurderingerMedKrav
            is ErUtfylt -> behandling.vurderingerMedKrav
            else -> return KunneIkkeForhåndsviseVedtaksbrev.UgyldigTilstand.left()
        }

        val fritekst = when (val x = behandling.vedtaksbrevvalg) {
            is Brevvalg.SaksbehandlersValg.SkalIkkeSendeBrev -> return KunneIkkeForhåndsviseVedtaksbrev.SkalIkkeSendeBrevForÅViseVedtaksbrev.left()
            is Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst -> return KunneIkkeForhåndsviseVedtaksbrev.BrevetMåVæreVedtaksbrevMedFritekst.left()
                .also {
                    log.error("Tilbakekrevingsbehandling ${behandling.id} har brevvalg for InformasjonsbrevMedFritekst. Det skal bare være mulig å ikke sende brev, eller VedtaksbrevMedFritekst")
                }

            is Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.MedFritekst -> x.fritekst
            is Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.UtenFritekst -> return KunneIkkeForhåndsviseVedtaksbrev.BrevetMåVæreVedtaksbrevMedFritekst.left()
                .also {
                    log.error("Tilbakekrevingsbehandling ${behandling.id} har brevvalg for VedtaksbrevUtenFritekst. Det skal bare være mulig å ikke sende brev, eller VedtaksbrevMedFritekst")
                }

            null -> return KunneIkkeForhåndsviseVedtaksbrev.IkkeTattStillingTilBrevvalg.left()
        }

        return brevService.lagDokument(
            VedtaksbrevTilbakekrevingsbehandlingDokumentCommand(
                fødselsnummer = sak.fnr,
                saksnummer = sak.saksnummer,
                correlationId = command.correlationId,
                sakId = sak.id,
                saksbehandler = when (behandling) {
                    is TilbakekrevingsbehandlingTilAttestering -> behandling.sendtTilAttesteringAv
                    is IverksattTilbakekrevingsbehandling -> behandling.forrigeSteg.sendtTilAttesteringAv
                    else -> command.utførtAv
                },
                attestant = when (behandling) {
                    is TilbakekrevingsbehandlingTilAttestering -> command.utførtAv
                    is IverksattTilbakekrevingsbehandling -> behandling.attesteringer.hentSisteIverksatteAttesteringOrNull()!!.attestant
                    else -> null
                },
                fritekst = fritekst,
                vurderingerMedKrav = vurderingMedKrav,
            ),
        )
            .map { it.generertDokument }
            .mapLeft { KunneIkkeForhåndsviseVedtaksbrev.FeilVedGenereringAvDokument }
    }
}
