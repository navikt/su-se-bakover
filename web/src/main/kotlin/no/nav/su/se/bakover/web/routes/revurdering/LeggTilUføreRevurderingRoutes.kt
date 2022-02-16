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
import no.nav.su.se.bakover.domain.bruker.Brukerrolle
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilGrunnlag
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevurderingerRequest
import no.nav.su.se.bakover.service.vilkår.UførevilkårStatus
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.Feilresponser.Uføre.periodeForGrunnlagOgVurderingErForskjellig
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
        val resultat: UførevilkårStatus,
        val begrunnelse: String?,
    ) {

        fun toServiceCommand(revurderingId: UUID): Either<Resultat, LeggTilUførevilkårRequest> {

            val periode = periode.toPeriode().getOrHandle {
                return it.left()
            }
            val validUføregrad = uføregrad?.let {
                Uføregrad.tryParse(it).getOrElse {
                    return Feilresponser.Uføre.uføregradMåVæreMellomEnOgHundre.left()
                }
            }
            return LeggTilUførevilkårRequest(
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
                                            is KunneIkkeLeggeTilGrunnlag.UgyldigTilstand -> Feilresponser.ugyldigTilstand(
                                                it.fra,
                                                it.til,
                                            )
                                            KunneIkkeLeggeTilGrunnlag.UføregradOgForventetInntektMangler -> Feilresponser.Uføre.uføregradOgForventetInntektMangler
                                            KunneIkkeLeggeTilGrunnlag.PeriodeForGrunnlagOgVurderingErForskjellig -> periodeForGrunnlagOgVurderingErForskjellig
                                            KunneIkkeLeggeTilGrunnlag.OverlappendeVurderingsperioder -> Feilresponser.overlappendeVurderingsperioder
                                            KunneIkkeLeggeTilGrunnlag.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden -> Feilresponser.utenforBehandlingsperioden
                                            KunneIkkeLeggeTilGrunnlag.AlleVurderingeneMåHaSammeResultat -> HttpStatusCode.BadRequest.errorJson(
                                                "Alle vurderingsperiodene må ha samme vurdering (ja/nei)",
                                                "vurderingene_må_ha_samme_resultat",
                                            )
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
