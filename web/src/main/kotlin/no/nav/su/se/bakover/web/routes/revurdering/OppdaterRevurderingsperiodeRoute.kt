package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.put
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.service.revurdering.KunneIkkeOppdatereRevurdering
import no.nav.su.se.bakover.service.revurdering.OppdaterRevurderingRequest
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.AuditLogEvent
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeRevurdering
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeSak
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.måVelgeInformasjonSomRevurderes
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.ugyldigTilstand
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId
import java.time.LocalDate

internal fun Route.oppdaterRevurderingRoute(
    revurderingService: RevurderingService,
) {
    data class Body(
        val fraOgMed: LocalDate,
        val årsak: String,
        val begrunnelse: String,
        val informasjonSomRevurderes: List<Revurderingsteg>,
    )

    authorize(Brukerrolle.Saksbehandler) {
        put("$revurderingPath/{revurderingId}") {
            call.withRevurderingId { revurderingId ->
                call.withBody<Body> { body ->
                    val navIdent = call.suUserContext.navIdent

                    revurderingService.oppdaterRevurdering(
                        OppdaterRevurderingRequest(
                            revurderingId = revurderingId,
                            fraOgMed = body.fraOgMed,
                            årsak = body.årsak,
                            begrunnelse = body.begrunnelse,
                            saksbehandler = NavIdentBruker.Saksbehandler(navIdent),
                            informasjonSomRevurderes = body.informasjonSomRevurderes,
                        ),
                    ).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.sikkerlogg("Oppdaterte perioden på revurdering med id: $revurderingId")
                            call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                            call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson())))
                        },
                    )
                }
            }
        }
    }
}

private fun KunneIkkeOppdatereRevurdering.tilResultat(): Resultat {
    return when (this) {
        is KunneIkkeOppdatereRevurdering.UgyldigPeriode -> Revurderingsfeilresponser.ugyldigPeriode(
            this.subError,
        )
        is KunneIkkeOppdatereRevurdering.FantIkkeRevurdering -> fantIkkeRevurdering
        is KunneIkkeOppdatereRevurdering.UgyldigTilstand -> ugyldigTilstand(this.fra, this.til)
        KunneIkkeOppdatereRevurdering.UgyldigBegrunnelse -> HttpStatusCode.BadRequest.errorJson(
            "Begrunnelse kan ikke være tom",
            "begrunnelse_kan_ikke_være_tom",
        )
        KunneIkkeOppdatereRevurdering.UgyldigÅrsak -> HttpStatusCode.BadRequest.errorJson(
            "Ugyldig årsak, må være en av: ${Revurderingsårsak.Årsak.values()}",
            "ugyldig_årsak",
        )
        KunneIkkeOppdatereRevurdering.KanIkkeOppdatereRevurderingSomErForhåndsvarslet -> HttpStatusCode.BadRequest.errorJson(
            "Kan ikke oppdatere revurdering som er forhåndsvarslet",
            "kan_ikke_oppdatere_revurdering_som_er_forhåndsvarslet",
        )
        KunneIkkeOppdatereRevurdering.MåVelgeInformasjonSomSkalRevurderes -> måVelgeInformasjonSomRevurderes
        KunneIkkeOppdatereRevurdering.FantIkkeSak -> fantIkkeSak
    }
}
