package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.brev.KunneIkkeLeggeTilBrevvalg
import no.nav.su.se.bakover.domain.revurdering.brev.LeggTilBrevvalgRequest
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.domain.satser.SatsFactory

internal fun Route.leggTilBrevvalgRevurderingRoute(
    revurderingService: RevurderingService,
    satsFactory: SatsFactory,
) {
    data class Body(
        val valg: LeggTilBrevvalgRequest.Valg,
        val fritekst: String?,
        val begrunnelse: String?,
    )

    post("$revurderingPath/{revurderingId}/brevvalg") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withRevurderingId { revurderingId ->
                call.withBody<Body> { body ->
                    call.svar(
                        revurderingService.leggTilBrevvalg(
                            LeggTilBrevvalgRequest(
                                revurderingId = revurderingId,
                                valg = body.valg,
                                fritekst = body.fritekst,
                                begrunnelse = body.begrunnelse,
                                saksbehandler = call.suUserContext.saksbehandler,
                            ),
                        ).fold(
                            ifLeft = { it.tilResultat() },
                            ifRight = {
                                call.sikkerlogg("Oppdaterte brevvalg for revurdering:$revurderingId")
                                call.audit(it.fnr, AuditLogEvent.Action.UPDATE, revurderingId)
                                Resultat.json(HttpStatusCode.Created, serialize(it.toJson(satsFactory)))
                            },
                        ),
                    )
                }
            }
        }
    }
}

internal fun KunneIkkeLeggeTilBrevvalg.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeLeggeTilBrevvalg.FantIkkeRevurdering -> {
            Feilresponser.fantIkkeBehandling
        }
        is KunneIkkeLeggeTilBrevvalg.Feil -> {
            when (val f = this.feil) {
                Revurdering.KunneIkkeLeggeTilBrevvalg.UgyldigBrevvalg -> {
                    BadRequest.errorJson(
                        message = "Ugyldig brevvalg",
                        code = "ugyldig_brevvalg",
                    )
                }
                is Revurdering.KunneIkkeLeggeTilBrevvalg.UgyldigTilstand -> {
                    Feilresponser.ugyldigTilstand(f.tilstand, f.tilstand)
                }
            }
        }
    }
}
