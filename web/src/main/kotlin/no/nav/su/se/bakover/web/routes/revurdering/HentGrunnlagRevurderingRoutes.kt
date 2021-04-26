package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.getOrHandle
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.revurdering.KunneIkkeHenteGrunnlag
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.grunnlag.revurdering.toJson
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withRevurderingId

/**
 * Mulighet for å hente grunnlagene som stod til grunn for revurderingen (før), nåværende grunnlag (endring) og grunnlagsresultatet (etter).
 * I.e. "før + endring = resultat"
 */
@KtorExperimentalAPI
internal fun Route.hentGrunnlagRevurderingRoutes(
    revurderingService: RevurderingService,
) {
    authorize(Brukerrolle.Saksbehandler) {
        get("$revurderingPath/{revurderingId}/uføregrunnlag") {
            call.withRevurderingId { revurderingId ->

                call.svar(
                    revurderingService.hentUføregrunnlag(revurderingId).mapLeft {
                        when (it) {
                            KunneIkkeHenteGrunnlag.FantIkkeBehandling -> HttpStatusCode.NotFound.errorJson(
                                "fant ikke behandling",
                                "fant_ikke_behandling",
                            )
                        }
                    }.map {
                        Resultat.json(HttpStatusCode.Created, serialize(it.toJson()))
                    }.getOrHandle {
                        it
                    },
                )
            }
        }
    }
}
