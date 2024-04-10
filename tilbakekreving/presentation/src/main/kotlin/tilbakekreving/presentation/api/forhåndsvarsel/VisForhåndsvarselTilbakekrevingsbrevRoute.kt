package tilbakekreving.presentation.api.forhåndsvarsel

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.KunneIkkeLageDokument
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.ident.NavIdentBruker
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
import tilbakekreving.application.service.forhåndsvarsel.ForhåndsvisForhåndsvarselTilbakekrevingsbehandlingService
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.forhåndsvarsel.ForhåndsvisForhåndsvarselTilbakekrevingsbehandlingCommand
import tilbakekreving.domain.forhåndsvarsel.KunneIkkeForhåndsviseForhåndsvarsel
import tilbakekreving.presentation.api.TILBAKEKREVING_PATH
import tilbakekreving.presentation.api.common.ikkeTilgangTilSak
import tilbakekreving.presentation.api.common.manglerBrukkerroller
import java.util.UUID

private data class ForhåndsvisBody(
    val versjon: Long,
    val fritekst: String?,
) {
    fun toCommand(
        sakId: UUID,
        behandlingsId: UUID,
        utførtAv: NavIdentBruker.Saksbehandler,
        correlationId: CorrelationId,
        brukerroller: List<Brukerrolle>,
    ): Either<Resultat, ForhåndsvisForhåndsvarselTilbakekrevingsbehandlingCommand> {
        val validatedBrukerroller = Either.catch { brukerroller.toNonEmptyList() }.getOrElse {
            return manglerBrukkerroller.left()
        }

        return ForhåndsvisForhåndsvarselTilbakekrevingsbehandlingCommand(
            sakId = sakId,
            behandlingId = TilbakekrevingsbehandlingId(value = behandlingsId),
            utførtAv = utførtAv,
            correlationId = correlationId,
            brukerroller = validatedBrukerroller,
            klientensSisteSaksversjon = Hendelsesversjon(value = versjon),
            fritekst = fritekst,
        ).right()
    }
}

internal fun Route.visForhåndsvarselTilbakekrevingsbrev(
    service: ForhåndsvisForhåndsvarselTilbakekrevingsbehandlingService,
) {
    post("$TILBAKEKREVING_PATH/{tilbakekrevingsId}/forhandsvarsel/forhandsvis") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withSakId { sakId ->
                call.withTilbakekrevingId { tilbakekrevingId ->
                    call.withBody<ForhåndsvisBody> { body ->
                        body.toCommand(
                            sakId = sakId,
                            behandlingsId = tilbakekrevingId,
                            utførtAv = call.suUserContext.saksbehandler,
                            correlationId = call.correlationId,
                            brukerroller = call.suUserContext.roller.toNonEmptyList(),
                        ).fold(
                            ifLeft = { call.svar(it) },
                            ifRight = {
                                service.forhåndsvisBrev(it).fold(
                                    { call.svar(it.tilResultat()) },
                                    { call.respondBytes(it.getContent(), ContentType.Application.Pdf) },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

internal fun KunneIkkeForhåndsviseForhåndsvarsel.tilResultat(): Resultat = when (this) {
    is KunneIkkeForhåndsviseForhåndsvarsel.IkkeTilgang -> ikkeTilgangTilSak

    // dobbel-impl av [forhådsvarsleTilbakekrevingRoute]
    is KunneIkkeForhåndsviseForhåndsvarsel.FeilVedDokumentGenerering -> when (this.kunneIkkeLageDokument) {
        is KunneIkkeLageDokument.FeilVedHentingAvInformasjon -> HttpStatusCode.InternalServerError.errorJson(
            "Feil ved henting av personinformasjon",
            "feil_ved_henting_av_personInformasjon",
        )

        is KunneIkkeLageDokument.FeilVedGenereringAvPdf -> Feilresponser.feilVedGenereringAvDokument
    }

    KunneIkkeForhåndsviseForhåndsvarsel.UlikVersjon -> Feilresponser.utdatertVersjon
    KunneIkkeForhåndsviseForhåndsvarsel.FantIkkeBehandling -> Feilresponser.fantIkkeBehandling
}
