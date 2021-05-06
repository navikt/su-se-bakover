package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.service.revurdering.KunneIkkeBeregneOgSimulereRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeBeregneOgSimulereRevurdering.FantIkkeRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeBeregneOgSimulereRevurdering.KanIkkeVelgeSisteMånedVedNedgangIStønaden
import no.nav.su.se.bakover.service.revurdering.KunneIkkeBeregneOgSimulereRevurdering.SimuleringFeilet
import no.nav.su.se.bakover.service.revurdering.KunneIkkeBeregneOgSimulereRevurdering.UgyldigTilstand
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.AuditLogEvent
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeRevurdering
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.ugyldigTilstand
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragJson.Companion.toFradrag
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId
import no.nav.su.se.bakover.web.withSakId

@KtorExperimentalAPI
internal fun Route.beregnOgSimulerRevurdering(
    revurderingService: RevurderingService,
) {
    data class BeregningForRevurderingBody(
        val periode: PeriodeJson,
        val fradrag: List<FradragJson>,
    ) {
        fun toDomain(): Either<Resultat, List<Fradrag>> =
            periode.toPeriode()
                .flatMap { fradrag.toFradrag(it) }
    }
    authorize(Brukerrolle.Saksbehandler) {
        post("$revurderingPath/{revurderingId}/beregnOgSimuler") {
            call.withSakId { sakId ->
                call.withRevurderingId { revurderingId ->
                    call.withBody<BeregningForRevurderingBody> { body ->
                        val resultat = body.toDomain()
                            .flatMap { fradrag ->
                                revurderingService.beregnOgSimuler(
                                    revurderingId = revurderingId,
                                    saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                                    fradrag = fradrag,
                                ).mapLeft {
                                    it.tilResultat()
                                }.map { revurdering ->
                                    call.sikkerlogg("Beregnet og simulert revurdering ${revurdering.id} på sak med id $sakId")
                                    call.audit(revurdering.fnr, AuditLogEvent.Action.UPDATE, revurdering.id)
                                    Resultat.json(
                                        HttpStatusCode.Created,
                                        serialize(revurdering.toJson()),
                                    )
                                }
                            }.getOrHandle { it }
                        call.svar(resultat)
                    }
                }
            }
        }
    }
}

private fun KunneIkkeBeregneOgSimulereRevurdering.tilResultat(): Resultat {
    return when (this) {
        is FantIkkeRevurdering -> fantIkkeRevurdering
        is UgyldigTilstand -> ugyldigTilstand(this.fra, this.til)
        is KanIkkeVelgeSisteMånedVedNedgangIStønaden -> BadRequest.errorJson(
            "Kan ikke velge siste måned av stønadsperioden ved nedgang i stønaden",
            "siste_måned_ved_nedgang_i_stønaden",
        )
        is SimuleringFeilet -> InternalServerError.errorJson(
            "Simulering feilet",
            "simulering_feilet",
        )
        is KunneIkkeBeregneOgSimulereRevurdering.UfullstendigBehandlingsinformasjon -> InternalServerError.errorJson(
            "Ufullstendig behandlingsinformasjon",
            "ufullstendig_behandlingsinformasjon",
        )
        KunneIkkeBeregneOgSimulereRevurdering.MåSendeGrunnbeløpReguleringSomÅrsakSammenMedForventetInntekt -> BadRequest.errorJson(
            "Forventet inntekt kan kun sendes sammen med regulering av grunnbeløp",
            "grunnbelop_forventetinntekt",
        )
        is KunneIkkeBeregneOgSimulereRevurdering.UgyldigBeregningsgrunnlag -> BadRequest.errorJson(
            "Ugyldig beregningsgrunnlag. Underliggende årsak: ${this.reason}",
            "ugyldig_beregningsgrunnlag",
        )
    }
}
