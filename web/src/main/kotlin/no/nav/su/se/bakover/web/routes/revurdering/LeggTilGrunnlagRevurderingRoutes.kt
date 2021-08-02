package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.Either
import arrow.core.Nel
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.post
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
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId
import java.util.UUID

private data class Body(val vurderinger: List<Vurdering>) {
    fun toServiceCommand(revurderingId: UUID): Either<Resultat, LeggTilUførevurderingerRequest> {
        if (vurderinger.isEmpty()) return HttpStatusCode.BadRequest.errorJson(
            "Ingen perioder er vurdert",
            "vurderingsperioder_mangler",
        ).left()

        return LeggTilUførevurderingerRequest(
            behandlingId = revurderingId,
            vurderinger = Nel.fromListUnsafe(vurderinger).map { vurdering ->
                vurdering.toServiceCommand(revurderingId).getOrHandle {
                    return it.left()
                }
            },
        ).right()
    }

    data class Vurdering(
        val periode: PeriodeJson,
        val uføregrad: Int?,
        val forventetInntekt: Int?,
        // TODO jah: Bruk en egen type for dette
        val resultat: Behandlingsinformasjon.Uførhet.Status,
        val begrunnelse: String?,
    ) {

        fun toServiceCommand(revurderingId: UUID): Either<Resultat, LeggTilUførevurderingRequest> {

            val periode = periode.toPeriode().getOrHandle {
                return it.left()
            }
            val validUføregrad = uføregrad?.let {
                Uføregrad.tryParse(it).getOrElse {
                    return HttpStatusCode.BadRequest.errorJson(
                        message = "Uføregrad må være mellom en og hundre",
                        code = "uføregrad_må_være_mellom_en_og_hundre",
                    ).left()
                }
            }
            return LeggTilUførevurderingRequest(
                behandlingId = revurderingId,
                periode = periode,
                uføregrad = validUføregrad,
                forventetInntekt = forventetInntekt,
                oppfylt = resultat,
                begrunnelse = begrunnelse,
            ).right()
        }
    }
}

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
                                            KunneIkkeLeggeTilGrunnlag.FantIkkeBehandling -> Feilresponser.fantIkkeBehandling
                                            KunneIkkeLeggeTilGrunnlag.UgyldigStatus -> Feilresponser.ugyldigBehandlingsstatus
                                            KunneIkkeLeggeTilGrunnlag.UføregradOgForventetInntektMangler -> Feilresponser.uføregradOgForventetInntektMangler
                                            KunneIkkeLeggeTilGrunnlag.PeriodeForGrunnlagOgVurderingErForskjellig -> Feilresponser.periodeForGrunnlagOgVurderingErForskjellig
                                            KunneIkkeLeggeTilGrunnlag.OverlappendeVurderingsperioder -> Feilresponser.overlappendeVurderingsperioder
                                            KunneIkkeLeggeTilGrunnlag.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden -> Feilresponser.utenforBehandlingsperioden
                                            KunneIkkeLeggeTilGrunnlag.AlleVurderingeneMåHaSammeResultat -> Feilresponser.alleVurderingeneMåHaSammeResultat
                                            KunneIkkeLeggeTilGrunnlag.HeleBehandlingsperiodenMåHaVurderinger -> HttpStatusCode.BadRequest.errorJson(
                                                "Hele behandlingsperioden må ha vurderinger",
                                                "hele_behandlingsperioden_må_ha_vurderinger",
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
