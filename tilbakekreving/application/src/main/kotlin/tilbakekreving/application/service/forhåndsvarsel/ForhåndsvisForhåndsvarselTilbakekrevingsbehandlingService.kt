package tilbakekreving.application.service.forhåndsvarsel

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.hentTilbakekrevingsbehandling
import org.slf4j.LoggerFactory
import tilbakekreving.domain.forhåndsvarsel.ForhåndsvarsleTilbakekrevingsbehandlingDokumentCommand
import tilbakekreving.domain.forhåndsvarsel.ForhåndsvisForhåndsvarselTilbakekrevingsbehandlingCommand
import tilbakekreving.domain.forhåndsvarsel.KunneIkkeForhåndsviseForhåndsvarsel
import tilgangstyring.application.TilgangstyringService

class ForhåndsvisForhåndsvarselTilbakekrevingsbehandlingService(
    private val tilgangstyring: TilgangstyringService,
    private val sakService: SakService,
    private val brevService: BrevService,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun forhåndsvisBrev(
        command: ForhåndsvisForhåndsvarselTilbakekrevingsbehandlingCommand,
    ): Either<KunneIkkeForhåndsviseForhåndsvarsel, PdfA> {
        tilgangstyring.assertHarTilgangTilSak(command.sakId).onLeft {
            return KunneIkkeForhåndsviseForhåndsvarsel.IkkeTilgang(it).left()
        }

        val sak = sakService.hentSak(command.sakId).getOrElse {
            throw IllegalStateException("Kunne ikke vise varsel om tilbakekreving, fant ikke sak. Command: $command")
        }
        if (sak.versjon != command.klientensSisteSaksversjon) {
            log.info("Forhåndsvis forhåndsvarsel av tilbakekreving - Sakens versjon (${sak.versjon}) er ulik saksbehandlers versjon. Command: $command")
        }

        val behandling = sak.hentTilbakekrevingsbehandling(command.behandlingId)
            ?: return KunneIkkeForhåndsviseForhåndsvarsel.FantIkkeBehandling.left()

        return brevService.lagDokument(
            ForhåndsvarsleTilbakekrevingsbehandlingDokumentCommand(
                saksnummer = sak.saksnummer,
                sakstype = sak.type,
                fritekst = command.fritekst,
                saksbehandler = command.utførtAv,
                correlationId = command.correlationId,
                sakId = command.sakId,
                kravgrunnlag = behandling.kravgrunnlag,
                fødselsnummer = sak.fnr,
            ),
        )
            .mapLeft { KunneIkkeForhåndsviseForhåndsvarsel.FeilVedDokumentGenerering(it) }
            .map { it.generertDokument }
    }
}
