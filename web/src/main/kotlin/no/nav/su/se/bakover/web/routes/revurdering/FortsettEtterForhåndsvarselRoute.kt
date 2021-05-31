package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.Either
import arrow.core.flatMap
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.revurdering.BeslutningEtterForhåndsvarsling
import no.nav.su.se.bakover.service.revurdering.FortsettEtterForhåndsvarslingRequest
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId

internal fun Route.fortsettEtterForhåndsvarselRoute(
    revurderingService: RevurderingService,
) {
    authorize(Brukerrolle.Saksbehandler) {
        data class Body(
            val begrunnelse: String,
            /**
             * @see BeslutningEtterForhåndsvarsling
             */
            val valg: String,
            val fritekstTilBrev: String?,
        )

        post("$revurderingPath/{revurderingId}/fortsettEtterForhåndsvarsel") {
            call.withRevurderingId { revurderingId ->
                call.withBody<Body> { body ->
                    when (body.valg) {
                        BeslutningEtterForhåndsvarsling.FortsettSammeOpplysninger.beslutning ->
                            Either.Right(
                                FortsettEtterForhåndsvarslingRequest.FortsettMedSammeOpplysninger(
                                    revurderingId = revurderingId,
                                    begrunnelse = body.begrunnelse,
                                    fritekstTilBrev = body.fritekstTilBrev ?: "",
                                    saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                                ),
                            )
                        BeslutningEtterForhåndsvarsling.FortsettMedAndreOpplysninger.beslutning ->
                            Either.Right(
                                FortsettEtterForhåndsvarslingRequest.FortsettMedAndreOpplysninger(
                                    revurderingId = revurderingId,
                                    begrunnelse = body.begrunnelse,
                                    saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                                ),
                            )
                        BeslutningEtterForhåndsvarsling.AvsluttUtenEndringer.beslutning ->
                            Either.Right(
                                FortsettEtterForhåndsvarslingRequest.AvsluttUtenEndringer(
                                    revurderingId = revurderingId,
                                    begrunnelse = body.begrunnelse,
                                    fritekstTilBrev = body.fritekstTilBrev ?: "",
                                    saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                                ),
                            )
                        else -> Either.Left(HttpStatusCode.BadRequest.message("Ugyldig valg"))
                    }
                        .flatMap {
                            revurderingService.fortsettEtterForhåndsvarsling(it)
                                .mapLeft {
                                    HttpStatusCode.BadRequest.message("TODO: korrekt feilmelding")
                                }
                        }
                        .fold(
                            { call.svar(it) },
                            { call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson()))) },
                        )
                }
            }
        }
    }
}
