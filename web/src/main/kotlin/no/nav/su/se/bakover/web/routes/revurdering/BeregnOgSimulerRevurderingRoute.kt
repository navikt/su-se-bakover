package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.service.revurdering.KunneIkkeBeregneOgSimulereRevurdering
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.routes.behandling.beregning.FradragJson
import no.nav.su.se.bakover.web.routes.behandling.beregning.FradragJson.Companion.toFradrag
import no.nav.su.se.bakover.web.routes.behandling.beregning.PeriodeJson
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.fantIkkeRevurdering
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.ugyldigPeriode
import no.nav.su.se.bakover.web.routes.revurdering.GenerelleRevurderingsfeilresponser.ugyldigTilstand
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId
import no.nav.su.se.bakover.web.withSakId

@KtorExperimentalAPI
internal fun Route.beregnOgSimulerRevurdering(
    revurderingService: RevurderingService
) {
    data class BeregningForRevurderingBody(
        val periode: PeriodeJson,
        val fradrag: List<FradragJson>,
    ) {
        fun toDomain(): Either<Resultat, List<Fradrag>> {
            val periode = periode.toPeriode().getOrHandle { return it.left() }
            val fradrag = fradrag.toFradrag(periode).getOrHandle { return it.left() }

            return fradrag.right()
        }
    }
    authorize(Brukerrolle.Saksbehandler) {
        post("$revurderingPath/{revurderingId}/beregnOgSimuler") {
            call.withSakId { sakId ->
                call.withRevurderingId { revurderingId ->
                    call.withBody<BeregningForRevurderingBody> { body ->
                        body.toDomain()
                            .fold(
                                ifLeft = { call.svar(it) },
                                ifRight = {
                                    revurderingService.beregnOgSimuler(
                                        revurderingId = revurderingId,
                                        saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                                        fradrag = it
                                    ).fold(
                                        ifLeft = { revurderingFeilet -> call.svar(revurderingFeilet.tilResultat()) },
                                        ifRight = { revurdering ->
                                            call.audit("Opprettet en ny revurdering beregning og simulering på sak med id $sakId")
                                            call.svar(
                                                Resultat.json(
                                                    HttpStatusCode.Created,
                                                    serialize(revurdering.toJson())
                                                )
                                            )
                                        },
                                    )
                                }
                            )
                    }
                }
            }
        }
    }
}

private fun KunneIkkeBeregneOgSimulereRevurdering.tilResultat(): Resultat {
    return when (this) {
        is KunneIkkeBeregneOgSimulereRevurdering.UgyldigPeriode -> ugyldigPeriode(this.subError)
        is KunneIkkeBeregneOgSimulereRevurdering.FantIkkeRevurdering -> fantIkkeRevurdering
        is KunneIkkeBeregneOgSimulereRevurdering.UgyldigTilstand -> ugyldigTilstand(this.fra, this.til)
        is KunneIkkeBeregneOgSimulereRevurdering.KanIkkeVelgeSisteMånedVedNedgangIStønaden -> HttpStatusCode.BadRequest.errorJson(
            "Kan ikke velge siste måned av stønadsperioden ved nedgang i stønaden",
            "siste_måned_ved_nedgang_i_stønaden",
        )
        is KunneIkkeBeregneOgSimulereRevurdering.SimuleringFeilet -> HttpStatusCode.InternalServerError.errorJson(
            "Simulering feilet",
            "simulering_feilet",
        )
    }
}
