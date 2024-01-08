package tilbakekreving.presentation.api.vedtaksbrev

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.correlationId
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.infrastructure.web.withTilbakekrevingId
import tilbakekreving.application.service.vurder.ForhåndsvisVedtaksbrevTilbakekrevingsbehandlingService
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.vedtaksbrev.ForhåndsvisVedtaksbrevCommand
import tilbakekreving.domain.vedtaksbrev.KunneIkkeForhåndsviseVedtaksbrev
import tilbakekreving.presentation.api.TILBAKEKREVING_PATH
import tilbakekreving.presentation.api.common.ikkeTilgangTilSak

internal fun Route.vedtaksbrevTilbakekrevingsbehandlingRoute(
    forhåndsvisVedtaksbrevService: ForhåndsvisVedtaksbrevTilbakekrevingsbehandlingService,
) {
    get("$TILBAKEKREVING_PATH/{tilbakekrevingsId}/vedtaksbrev/forhandsvis") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withSakId { sakId ->
                call.withTilbakekrevingId { tilbakekrevingId ->
                    forhåndsvisVedtaksbrevService.forhåndsvisVedtaksbrev(
                        ForhåndsvisVedtaksbrevCommand(
                            sakId = sakId,
                            behandlingId = TilbakekrevingsbehandlingId(tilbakekrevingId),
                            correlationId = call.correlationId,
                            utførtAv = call.suUserContext.saksbehandler,
                            brukerroller = call.suUserContext.roller,
                        ),
                    )
                        .fold(
                            { call.svar(it.tilResultat()) },
                            { call.respondBytes(it.getContent(), ContentType.Application.Pdf) },
                        )
                }
            }
        }
    }
}

internal fun KunneIkkeForhåndsviseVedtaksbrev.tilResultat(): Resultat = when (this) {
    is KunneIkkeForhåndsviseVedtaksbrev.IkkeTilgang -> ikkeTilgangTilSak
    KunneIkkeForhåndsviseVedtaksbrev.BrevetMåVæreVedtaksbrevMedFritekst -> HttpStatusCode.InternalServerError.errorJson(
        "Brevet må være av typen [Vedtaksbrev.MedFritekst]",
        "brevet_må_være_av_typen_vedtaksbrev_med_fritekst",
    )

    KunneIkkeForhåndsviseVedtaksbrev.FantIkkeBehandling -> Feilresponser.fantIkkeBehandling
    KunneIkkeForhåndsviseVedtaksbrev.IkkeTattStillingTilBrevvalg -> HttpStatusCode.BadRequest.errorJson(
        "Det er ikke tatt stilling til brevvalg for å vise vedtaksbrev",
        "ikke_tatt_stilling_til_brevvalg",
    )

    KunneIkkeForhåndsviseVedtaksbrev.SkalIkkeSendeBrevForÅViseVedtaksbrev -> HttpStatusCode.BadRequest.errorJson(
        "Det er valg at brev ikke skal sendes. Brevet kan dermed ikke vises",
        "valg_å_ikke_sende_brev",
    )

    KunneIkkeForhåndsviseVedtaksbrev.FeilVedGenereringAvDokument -> Feilresponser.feilVedGenereringAvDokument
    KunneIkkeForhåndsviseVedtaksbrev.UgyldigTilstand -> Feilresponser.ugyldigTilstand
}
