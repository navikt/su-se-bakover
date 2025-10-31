package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.web.ErrorJson
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.ugyldigTilstand
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.beregning.KunneIkkeBeregneOgSimulereRevurdering
import no.nav.su.se.bakover.domain.revurdering.beregning.KunneIkkeBeregneOgSimulereRevurdering.FantIkkeRevurdering
import no.nav.su.se.bakover.domain.revurdering.beregning.KunneIkkeBeregneOgSimulereRevurdering.KanIkkeVelgeSisteMånedVedNedgangIStønaden
import no.nav.su.se.bakover.domain.revurdering.beregning.KunneIkkeBeregneOgSimulereRevurdering.UgyldigTilstand
import no.nav.su.se.bakover.domain.revurdering.opphør.RevurderingsutfallSomIkkeStøttes
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingOgFeilmeldingerResponse
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.domain.revurdering.varsel.Varselmelding
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeRevurdering
import no.nav.su.se.bakover.web.routes.tilResultat
import vilkår.formue.domain.FormuegrenserFactory

internal fun Route.beregnOgSimulerRevurdering(
    revurderingService: RevurderingService,
    formuegrenserFactory: FormuegrenserFactory,
) {
    post("$REVURDERING_PATH/{revurderingId}/beregnOgSimuler") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withRevurderingId { revurderingId ->
                    revurderingService.beregnOgSimuler(
                        revurderingId = RevurderingId(revurderingId),
                        saksbehandler = NavIdentBruker.Saksbehandler(call.suUserContext.navIdent),
                    ).mapLeft {
                        call.svar(it.tilResultat())
                    }.map { response ->
                        call.sikkerlogg("Beregnet og simulert revurdering ${response.revurdering.id} på sak med id $sakId")
                        call.audit(response.revurdering.fnr, AuditLogEvent.Action.UPDATE, response.revurdering.id.value)
                        call.svar(
                            Resultat.json(
                                HttpStatusCode.Created,
                                serialize(response.toJson(formuegrenserFactory)),
                            ),
                        )
                    }
                }
            }
        }
    }
}

internal data class RevurderingOgFeilmeldingerResponseJson(
    val revurdering: RevurderingJson,
    val feilmeldinger: List<ErrorJson>,
    val varselmeldinger: List<ErrorJson>,
)

internal fun RevurderingOgFeilmeldingerResponse.json(formuegrenserFactory: FormuegrenserFactory): String {
    return serialize(toJson(formuegrenserFactory))
}

internal fun RevurderingOgFeilmeldingerResponse.toJson(formuegrenserFactory: FormuegrenserFactory) =
    RevurderingOgFeilmeldingerResponseJson(
        revurdering = revurdering.toJson(formuegrenserFactory),
        feilmeldinger = feilmeldinger.map { it.toJson() },
        varselmeldinger = varselmeldinger.map { it.toJson() },
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

internal fun Varselmelding.toJson(): ErrorJson {
    return when (this) {
        Varselmelding.BeløpsendringUnder10Prosent -> {
            ErrorJson(
                message = "Beløpsendring er mindre enn 10 prosent av gjeldende utbetaling.",
                code = "beløpsendring_mindre_enn_ti_prosent",
            )
        }

        Varselmelding.FradragOgFormueForEPSErFjernet -> {
            ErrorJson(
                message = "Fradrag og formue for EPS er fjernet som følge av endring i bosituasjon.",
                code = "varsel_fjernet_fradrag_og_formue_eps",
            )
        }
    }
}

private fun KunneIkkeBeregneOgSimulereRevurdering.tilResultat(): Resultat {
    return when (this) {
        is FantIkkeRevurdering -> {
            fantIkkeRevurdering
        }

        is UgyldigTilstand -> {
            ugyldigTilstand(this.fra, this.til)
        }

        is KanIkkeVelgeSisteMånedVedNedgangIStønaden -> {
            BadRequest.errorJson(
                "Kan ikke velge siste måned av stønadsperioden ved nedgang i stønaden",
                "siste_måned_ved_nedgang_i_stønaden",
            )
        }

        is KunneIkkeBeregneOgSimulereRevurdering.UgyldigBeregningsgrunnlag -> {
            BadRequest.errorJson(
                "Ugyldig beregningsgrunnlag. Underliggende årsak: ${this.reason}",
                "ugyldig_beregningsgrunnlag",
            )
        }

        is KunneIkkeBeregneOgSimulereRevurdering.KanIkkeHaFradragSomTilhørerEpsHvisBrukerIkkeHarEps -> {
            BadRequest.errorJson(
                "Kan ikke ha fradrag knyttet til EPS når bruker ikke har EPS.",
                "kan_ikke_ha_eps_fradrag_uten_eps",
            )
        }

        is KunneIkkeBeregneOgSimulereRevurdering.KunneIkkeSimulere -> {
            this.simuleringFeilet.tilResultat()
        }
    }
}
