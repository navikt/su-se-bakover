package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilGrunnlag
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevurderingRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevurderingerRequest
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.grunnlag.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId
import java.util.UUID

private data class Body(val vurderinger: List<Vurdering>) {
    fun toServiceCommand(revurderingId: UUID): Either<Resultat, LeggTilUførevurderingerRequest> {
        return LeggTilUførevurderingerRequest(
            behandlingId = revurderingId,
            vurderinger = vurderinger.map { vurdering ->
                vurdering.toServiceCommand(revurderingId).getOrHandle {
                    return it.left()
                }
            },
        ).right()
    }

    private data class Vurdering(
        val periode: PeriodeJson,
        val uføregrad: Int,
        val forventetInntekt: Int,
        // TODO jah: Bruk en egen type for dette
        val resultat: Behandlingsinformasjon.Uførhet.Status,
        val begrunnelse: String?,
    ) {

        fun toServiceCommand(revurderingId: UUID): Either<Resultat, LeggTilUførevurderingRequest> {

            val periode = periode.toPeriode().getOrHandle {
                return it.left()
            }
            val validUføregrad = Uføregrad.tryParse(uføregrad).getOrElse {
                return HttpStatusCode.BadRequest.errorJson(
                    message = "Uføregrad må være mellom en og hundre",
                    code = "uføregrad_må_være_mellom_en_og_hundre",
                ).left()
            }
            return LeggTilUførevurderingRequest(
                behandlingId = revurderingId,
                periode = periode,
                uføregrad = validUføregrad,
                forventetInntekt = forventetInntekt,
                oppfylt = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
                begrunnelse = begrunnelse,
            ).right()
        }
    }
}

@KtorExperimentalAPI
internal fun Route.leggTilGrunnlagRevurderingRoutes(
    revurderingService: RevurderingService,
) {
    authorize(Brukerrolle.Saksbehandler) {
        post("$revurderingPath/{revurderingId}/uføregrunnlag") {
            call.withRevurderingId { revurderingId ->
                call.withBody<Body> { body ->
                    call.svar(
                        body.toServiceCommand(revurderingId)
                            .flatMap { command ->
                                revurderingService.leggTilUføregrunnlag(command)
                                    .mapLeft {
                                        when (it) {
                                            KunneIkkeLeggeTilGrunnlag.FantIkkeBehandling -> HttpStatusCode.NotFound.errorJson(
                                                "fant ikke behandling",
                                                "fant_ikke_behandling",
                                            )
                                            KunneIkkeLeggeTilGrunnlag.UgyldigStatus -> InternalServerError.errorJson(
                                                "ugyldig status for å legge til",
                                                "ugyldig_status_for_å_legge_til",
                                            )
                                            KunneIkkeLeggeTilGrunnlag.UføregradOgForventetInntektMangler -> HttpStatusCode.BadRequest.errorJson(
                                                "Hvis man innvilger uførevilkåret må man sende med uføregrad og forventet inntekt",
                                                "uføregrad_og_forventet_inntekt_mangler",
                                            )
                                            KunneIkkeLeggeTilGrunnlag.PeriodeForGrunnlagOgVurderingErForskjellig -> HttpStatusCode.BadRequest.errorJson(
                                                "Det er ikke samsvar mellom perioden for vurdering og perioden for grunnlaget",
                                                "periode_for_grunnlag_og_vurdering_er_forskjellig",
                                            )
                                            KunneIkkeLeggeTilGrunnlag.OverlappendeVurderingsperioder -> BadRequest.errorJson(
                                                "Vurderingperioder kan ikke overlappe",
                                                "overlappende_vurderingsperioder",
                                            )
                                            KunneIkkeLeggeTilGrunnlag.VurderingsperiodeMangler -> BadRequest.errorJson(
                                                "Ingen perioder er vurdert",
                                                "vurderingsperioder_mangler",
                                            )
                                        }
                                    }.map {
                                        Resultat.json(HttpStatusCode.Created, serialize(it.toJson()))
                                    }
                            }.getOrHandle { it },
                    )
                }
            }
        }
    }
}
