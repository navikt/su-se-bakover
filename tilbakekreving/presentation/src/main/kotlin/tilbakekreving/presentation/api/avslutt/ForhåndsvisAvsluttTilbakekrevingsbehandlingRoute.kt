package tilbakekreving.presentation.api.avslutt

import dokument.domain.KunneIkkeLageDokument
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.correlationId
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.infrastructure.web.withTilbakekrevingId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.application.service.avbrutt.ForhåndsvisAvbruttTilbakekrevingsbehandlingBrevService
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.avbrutt.ForhåndsvisAvbrytTilbakekrevingsbehandlingCommand
import tilbakekreving.domain.avbrutt.KunneIkkeForhåndsviseAvbruttBrev
import tilbakekreving.presentation.api.TILBAKEKREVING_PATH
import tilbakekreving.presentation.api.common.ikkeTilgangTilSak

private data class ForhåndsvisBody(
    val versjon: Long,
    val fritekst: String?,
)

internal fun Route.forhåndsvisAvbrytTilbakekrevingsbehandlingRoute(
    service: ForhåndsvisAvbruttTilbakekrevingsbehandlingBrevService,
) {
    post("$TILBAKEKREVING_PATH/{tilbakekrevingsId}/avbryt/forhandsvis") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withSakId { sakId ->
                call.withTilbakekrevingId { id ->
                    call.withBody<ForhåndsvisBody> { body ->
                        service.forhåndsvisBrev(
                            ForhåndsvisAvbrytTilbakekrevingsbehandlingCommand(
                                sakId = sakId,
                                behandlingsId = TilbakekrevingsbehandlingId(id),
                                klientensSisteSaksversjon = Hendelsesversjon(body.versjon),
                                fritekst = body.fritekst,
                                utførtAv = call.suUserContext.saksbehandler,
                                correlationId = call.correlationId,
                                brukerroller = call.suUserContext.roller.toNonEmptyList(),
                            ),
                        ).fold(
                            { call.svar(it.tilResultat()) },
                            { call.respondBytes(it.getContent(), ContentType.Application.Pdf) },
                        )
                    }
                }
            }
        }
    }
}

internal fun KunneIkkeForhåndsviseAvbruttBrev.tilResultat(): Resultat {
    return when (this) {
        // trippel impl - av visForhåndsvarselTilbakekrevingsbrev
        is KunneIkkeForhåndsviseAvbruttBrev.FeilVedDokumentGenerering -> when (this.kunneIkkeLageDokument) {
            is KunneIkkeLageDokument.FeilVedHentingAvInformasjon -> HttpStatusCode.InternalServerError.errorJson(
                "Feil ved henting av personinformasjon",
                "feil_ved_henting_av_personInformasjon",
            )

            is KunneIkkeLageDokument.FeilVedGenereringAvPdf -> Feilresponser.feilVedGenereringAvDokument
        }
        is KunneIkkeForhåndsviseAvbruttBrev.IkkeTilgang -> ikkeTilgangTilSak
        KunneIkkeForhåndsviseAvbruttBrev.UlikVersjon -> Feilresponser.utdatertVersjon
    }
}
