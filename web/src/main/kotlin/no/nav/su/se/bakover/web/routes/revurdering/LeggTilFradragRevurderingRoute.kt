package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.bruker.Brukerrolle
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.service.grunnlag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilFradragsgrunnlag
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragJson.Companion.toFradrag
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId
import no.nav.su.se.bakover.web.withSakId
import java.time.Clock

internal fun Route.leggTilFradragRevurdering(
    revurderingService: RevurderingService,
    clock: Clock,
) {
    data class BeregningForRevurderingBody(
        val fradrag: List<FradragJson>,
    ) {
        fun toDomain(clock: Clock): Either<Resultat, List<Grunnlag.Fradragsgrunnlag>> =
            fradrag.toFradrag().map {
                it.map { fradrag ->
                    Grunnlag.Fradragsgrunnlag.tryCreate(
                        fradrag = fradrag,
                        opprettet = Tidspunkt.now(clock),
                    ).getOrElse {
                        return BadRequest.errorJson(
                            message = "Kunne ikke lage fradrag",
                            code = "kunne_ikke_lage_fradrag",
                        ).left()
                    }
                }
            }
    }

    authorize(Brukerrolle.Saksbehandler) {
        post("$revurderingPath/{revurderingId}/fradrag") {
            call.withSakId { sakId ->
                call.withRevurderingId { revurderingId ->
                    call.withBody<BeregningForRevurderingBody> { body ->
                        call.svar(
                            body.toDomain(clock).flatMap { fradrag ->
                                revurderingService.leggTilFradragsgrunnlag(
                                    LeggTilFradragsgrunnlagRequest(
                                        revurderingId,
                                        fradrag,
                                    ),
                                ).mapLeft {
                                    when (it) {
                                        KunneIkkeLeggeTilFradragsgrunnlag.FantIkkeBehandling -> Revurderingsfeilresponser.fantIkkeRevurdering
                                        is KunneIkkeLeggeTilFradragsgrunnlag.UgyldigTilstand -> Feilresponser.ugyldigTilstand(
                                            fra = it.fra,
                                            til = it.til,
                                        )
                                        is KunneIkkeLeggeTilFradragsgrunnlag.KunneIkkeEndreFradragsgrunnlag -> Feilresponser.kunneIkkeLeggeTilFradragsgrunnlag
                                    }
                                }.map {
                                    call.sikkerlogg("Lagret fradrag for revudering $revurderingId på $sakId")
                                    Resultat.json(
                                        HttpStatusCode.OK,
                                        serialize(it.toJson()),
                                    )
                                }
                            }.getOrHandle { it },
                        )
                    }
                }
            }
        }
    }
}
