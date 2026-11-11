package no.nav.su.se.bakover.web.routes.tilbakekreving.underkjenn

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.merge
import arrow.core.raise.either
import arrow.core.right
import io.ktor.http.HttpStatusCode
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
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.infrastructure.web.withTilbakekrevingId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.web.inputvalidation.withValidertBody
import no.nav.su.se.bakover.web.routes.tilbakekreving.TILBAKEKREVING_PATH
import no.nav.su.se.bakover.web.routes.tilbakekreving.ikkeTilgangTilSak
import org.slf4j.LoggerFactory
import tilbakekreving.application.service.underkjenn.UnderkjennTilbakekrevingsbehandlingService
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.underkjennelse.KunneIkkeUnderkjenne
import tilbakekreving.domain.underkjennelse.UnderkjennAttesteringsgrunnTilbakekreving
import tilbakekreving.domain.underkjennelse.UnderkjennTilbakekrevingsbehandlingCommand
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson.Companion.toStringifiedJson
import java.util.UUID

data class Body(
    val versjon: Long,
    val kommentar: String,
    val grunn: String,
) {
    fun toCommand(
        sakId: UUID,
        behandlingsId: UUID,
        utførtAv: NavIdentBruker.Attestant,
        correlationId: CorrelationId,
        brukerroller: List<Brukerrolle>,
    ): Either<Resultat, UnderkjennTilbakekrevingsbehandlingCommand> {
        return UnderkjennTilbakekrevingsbehandlingCommand(
            sakId = sakId,
            behandlingsId = TilbakekrevingsbehandlingId(behandlingsId),
            utførtAv = utførtAv,
            correlationId = correlationId,
            brukerroller = brukerroller.toNonEmptyList(),
            klientensSisteSaksversjon = Hendelsesversjon(versjon),
            grunn = Either.catch {
                UnderkjennAttesteringsgrunnTilbakekreving.valueOf(grunn)
            }.getOrElse {
                return HttpStatusCode.BadRequest.errorJson(
                    "Kunne ikke mappe grunn til Attesteringsgrunn - input var $grunn",
                    "kunne_ikke_mappe_attesteringsgrunn",
                ).left()
            },
            kommentar = kommentar,
        ).right()
    }
}

internal fun Route.underkjennTilbakekrevingsbehandlingRoute(
    service: UnderkjennTilbakekrevingsbehandlingService,
) {
    val log = LoggerFactory.getLogger(this::class.java)
    post("$TILBAKEKREVING_PATH/{tilbakekrevingsId}/underkjenn") {
        authorize(Brukerrolle.Attestant) {
            call.withSakId { sakId ->
                call.withTilbakekrevingId { id ->
                    call.withValidertBody<Body>(log) { body ->
                        either {
                            val command = body.toCommand(
                                sakId = sakId,
                                behandlingsId = id,
                                utførtAv = call.suUserContext.attestant,
                                correlationId = call.correlationId,
                                brukerroller = call.suUserContext.roller,
                            ).bind()
                            val behandling = service.underkjenn(command)
                                .mapLeft { feil -> feil.tilResultat() }.bind()
                            Resultat.json(HttpStatusCode.Created, behandling.toStringifiedJson())
                        }.merge().let { resultat -> call.svar(resultat) }
                    }
                }
            }
        }
    }
}

private fun KunneIkkeUnderkjenne.tilResultat(): Resultat = when (this) {
    is KunneIkkeUnderkjenne.IkkeTilgang -> ikkeTilgangTilSak
    KunneIkkeUnderkjenne.UlikVersjon -> Feilresponser.utdatertVersjon
}
