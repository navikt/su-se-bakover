package tilbakekreving.application.service.avbrutt

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.domain.sak.SakService
import org.slf4j.LoggerFactory
import tilbakekreving.application.service.common.TilbakekrevingsbehandlingTilgangstyringService
import tilbakekreving.domain.avbrutt.AvbruttTilbakekrevingsbehandlingDokumentCommand
import tilbakekreving.domain.avbrutt.ForhåndsvisAvbrytTilbakekrevingsbehandlingCommand
import tilbakekreving.domain.avbrutt.KunneIkkeForhåndsviseAvbruttBrev

class ForhåndsvisAvbruttTilbakekrevingsbehandlingBrevService(
    private val tilgangstyring: TilbakekrevingsbehandlingTilgangstyringService,
    private val sakService: SakService,
    private val brevService: BrevService,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun forhåndsvisBrev(command: ForhåndsvisAvbrytTilbakekrevingsbehandlingCommand): Either<KunneIkkeForhåndsviseAvbruttBrev, PdfA> {
        tilgangstyring.assertHarTilgangTilSak(command.sakId).onLeft {
            return KunneIkkeForhåndsviseAvbruttBrev.IkkeTilgang(it).left()
        }

        val sak = sakService.hentSak(command.sakId).getOrElse {
            throw IllegalStateException("Kunne ikke underkjenne ${command.behandlingsId.value}, fant ikke sak ${command.sakId}")
        }
        if (sak.versjon != command.klientensSisteSaksversjon) {
            log.info("Forhåndsvis Avbrytelse av tilbakekrevingsbrevet - Sakens versjon (${sak.versjon}) er ulik saksbehandlers versjon. Command: $command")
        }

        return brevService.lagDokument(
            AvbruttTilbakekrevingsbehandlingDokumentCommand(
                fødselsnummer = sak.fnr,
                saksnummer = sak.saksnummer,
                fritekst = command.fritekst,
                saksbehandler = command.utførtAv,
                correlationId = command.correlationId,
                sakId = command.sakId,
            ),
        )
            .mapLeft { KunneIkkeForhåndsviseAvbruttBrev.FeilVedDokumentGenerering(it) }
            .map { it.generertDokument }
    }
}
