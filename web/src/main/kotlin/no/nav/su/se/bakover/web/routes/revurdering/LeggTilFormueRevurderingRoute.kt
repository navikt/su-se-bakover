package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilFormuegrunnlag
import no.nav.su.se.bakover.service.revurdering.LeggTilFormuegrunnlagRequest
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.grunnlag.FormuegrunnlagJson
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId
import no.nav.su.se.bakover.web.withSakId
import java.util.UUID

internal fun Route.leggTilFormueRevurderingRoute(
    revurderingService: RevurderingService,
) {

    data class Body(
        val epsFormue: FormuegrunnlagJson.VerdierJson?,
        val søkersFormue: FormuegrunnlagJson.VerdierJson,
        val begrunnelse: String?,
    ) {
        fun toServiceRequest(revurderingId: UUID): LeggTilFormuegrunnlagRequest {
            return LeggTilFormuegrunnlagRequest(
                revurderingId = revurderingId,
                epsFormue = epsFormue?.toDomain(),
                søkersFormue = søkersFormue.toDomain(),
                begrunnelse = begrunnelse,
            )
        }
    }
    authorize(Brukerrolle.Saksbehandler) {
        post("$revurderingPath/{revurderingId}/formuegrunnlag") {
            call.withSakId { sakId ->
                call.withRevurderingId { revurderingId ->
                    call.withBody<Body> { body ->
                        revurderingService.leggTilFormuegrunnlag(
                            body.toServiceRequest(revurderingId),
                        ).map {
                            call.sikkerlogg("Lagret formue for revudering $revurderingId på $sakId")
                            call.svar(
                                Resultat.json(
                                    HttpStatusCode.OK,
                                    serialize(it.toJson()),
                                ),
                            )
                        }.mapLeft {
                            call.svar(it.tilResultat())
                        }
                    }
                }
            }
        }
    }
}

private fun KunneIkkeLeggeTilFormuegrunnlag.tilResultat() = when (this) {
    KunneIkkeLeggeTilFormuegrunnlag.FantIkkeRevurdering -> Revurderingsfeilresponser.fantIkkeRevurdering
}
