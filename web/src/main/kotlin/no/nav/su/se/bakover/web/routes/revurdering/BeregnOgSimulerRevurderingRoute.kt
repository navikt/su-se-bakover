package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.revurdering.RevurderingsutfallSomIkkeStøttes
import no.nav.su.se.bakover.service.revurdering.BeregnOgSimulerResponse
import no.nav.su.se.bakover.service.revurdering.KunneIkkeBeregneOgSimulereRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeBeregneOgSimulereRevurdering.FantIkkeRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeBeregneOgSimulereRevurdering.KanIkkeVelgeSisteMånedVedNedgangIStønaden
import no.nav.su.se.bakover.service.revurdering.KunneIkkeBeregneOgSimulereRevurdering.SimuleringFeilet
import no.nav.su.se.bakover.service.revurdering.KunneIkkeBeregneOgSimulereRevurdering.UgyldigTilstand
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.AuditLogEvent
import no.nav.su.se.bakover.web.ErrorJson
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.routes.Feilresponser.kanIkkeHaEpsFradragUtenEps
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeRevurdering
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.ugyldigTilstand
import no.nav.su.se.bakover.web.sikkerlogg
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withRevurderingId
import no.nav.su.se.bakover.web.withSakId

internal fun Route.beregnOgSimulerRevurdering(
    revurderingService: RevurderingService,
) {
    authorize(Brukerrolle.Saksbehandler) {
        post("$revurderingPath/{revurderingId}/beregnOgSimuler") {
            call.withSakId { sakId ->
                call.withRevurderingId { revurderingId ->
                    revurderingService.beregnOgSimuler(
                        revurderingId = revurderingId,
                        saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                    ).mapLeft {
                        call.svar(it.tilResultat())
                    }.map { response ->
                        call.sikkerlogg("Beregnet og simulert revurdering ${response.revurdering.id} på sak med id $sakId")
                        call.audit(response.revurdering.fnr, AuditLogEvent.Action.UPDATE, response.revurdering.id)
                        call.svar(
                            Resultat.json(
                                HttpStatusCode.Created,
                                serialize(response.toJson()),
                            ),
                        )
                    }
                }
            }
        }
    }
}

data class BeregnOgSimulerResponseJson(
    val revurdering: RevurderingJson,
    val feilmeldinger: List<ErrorJson>,
)

internal fun BeregnOgSimulerResponse.toJson() = BeregnOgSimulerResponseJson(
    revurdering = revurdering.toJson(),
    feilmeldinger = feilmeldinger.map { it.toJson() },
)

internal fun RevurderingsutfallSomIkkeStøttes.toJson(): ErrorJson = when (this) {
    RevurderingsutfallSomIkkeStøttes.DelvisOpphør -> ErrorJson(
        message = "Delvis opphør støttes ikke. Revurderingen må gjennomføres i flere steg.",
        code = "delvis_opphør",
    )
    RevurderingsutfallSomIkkeStøttes.OpphørAvFlereVilkår -> ErrorJson(
        message = "Opphør av flere vilkår i kombinasjon støttes ikke",
        code = "opphør_av_flere_vilkår",
    )
    RevurderingsutfallSomIkkeStøttes.OpphørErIkkeFraFørsteMåned -> ErrorJson(
        message = "Opphørsdato er ikke lik fra-dato for revurderingsperioden. Revurdering må gjennomføres i flere steg.",
        code = "opphør_ikke_tidligste_dato",
    )
    RevurderingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon -> ErrorJson(
        message = "Opphør i kombinasjon med andre endringer støttes ikke. Revurdering må gjennomføres i flere steg.",
        code = "opphør_og_andre_endringer_i_kombinasjon",
    )
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
        KunneIkkeBeregneOgSimulereRevurdering.MåSendeGrunnbeløpReguleringSomÅrsakSammenMedForventetInntekt -> BadRequest.errorJson(
            "Forventet inntekt kan kun sendes sammen med regulering av grunnbeløp",
            "grunnbelop_forventetinntekt",
        )
        is KunneIkkeBeregneOgSimulereRevurdering.UgyldigBeregningsgrunnlag -> BadRequest.errorJson(
            "Ugyldig beregningsgrunnlag. Underliggende årsak: ${this.reason}",
            "ugyldig_beregningsgrunnlag",
        )
        KunneIkkeBeregneOgSimulereRevurdering.UfullstendigVilkårsvurdering -> InternalServerError.errorJson(
            "Vurdering av vilkår er ufullstendig",
            "ufullstendig_vilkårsvurdering",
        )
        KunneIkkeBeregneOgSimulereRevurdering.KanIkkeHaFradragSomTilhørerEpsHvisBrukerIkkeHarEps -> kanIkkeHaEpsFradragUtenEps
    }
}
