package tilbakekreving.application.service.forhåndsvarsel

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.domain.sak.SakService
import tilbakekreving.application.service.common.TilbakekrevingsbehandlingTilgangstyringService
import tilbakekreving.domain.forhåndsvarsel.ForhåndsvarsleTilbakekrevingsbehandlingDokumentCommand
import tilbakekreving.domain.forhåndsvarsel.ForhåndsvisForhåndsvarselTilbakekrevingsbehandlingCommand
import tilbakekreving.domain.forhåndsvarsel.KunneIkkeForhåndsviseForhåndsvarsel

class ForhåndsvisForhåndsvarselTilbakekrevingsbehandlingService(
    private val tilgangstyring: TilbakekrevingsbehandlingTilgangstyringService,
    private val sakService: SakService,
    private val brevService: BrevService,
) {
    fun forhåndsvisBrev(command: ForhåndsvisForhåndsvarselTilbakekrevingsbehandlingCommand): Either<KunneIkkeForhåndsviseForhåndsvarsel, PdfA> {
        tilgangstyring.assertHarTilgangTilSak(command.sakId).onLeft {
            return KunneIkkeForhåndsviseForhåndsvarsel.IkkeTilgang(it).left()
        }

        val sak = sakService.hentSak(command.sakId).getOrElse {
            throw IllegalStateException("Kunne ikke sende forhåndsvarsel for tilbakekrevingsbehandling, fant ikke sak ${command.sakId}")
        }

        return brevService.lagDokument(
            ForhåndsvarsleTilbakekrevingsbehandlingDokumentCommand(
                fødselsnummer = sak.fnr,
                saksnummer = sak.saksnummer,
                fritekst = command.fritekst,
                saksbehandler = command.utførtAv,
                correlationId = command.correlationId,
                sakId = command.sakId,
            ),
        )
            .mapLeft { KunneIkkeForhåndsviseForhåndsvarsel.FeilVedDokumentGenerering(it) }
            .map { it.generertDokument }
    }
}
