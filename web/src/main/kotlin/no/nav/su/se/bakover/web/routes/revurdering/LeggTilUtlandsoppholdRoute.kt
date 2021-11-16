package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.Nel
import arrow.core.getOrHandle
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilUtlandsopphold
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.vilkår.LeggTilOppholdIUtlandetRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilOppholdIUtlandetRevurderingRequest
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId
import java.util.UUID

private data class UtlandsoppholdBody(
    val oppholdIUtlandet: List<OppholdIUtlandetJson>,
) {
    fun toRequest(revurderingId: UUID): LeggTilOppholdIUtlandetRevurderingRequest {
        return LeggTilOppholdIUtlandetRevurderingRequest(
            behandlingId = revurderingId,
            request = Nel.fromListUnsafe(
                oppholdIUtlandet.map {
                    it.toRequest(revurderingId)
                },
            ),
        )
    }
}

private data class OppholdIUtlandetJson(
    val periode: PeriodeJson,
    val status: String,
    val begrunnelse: String?,
) {
    fun toRequest(revurderingId: UUID): LeggTilOppholdIUtlandetRequest {
        return LeggTilOppholdIUtlandetRequest(
            behandlingId = revurderingId,
            periode = periode.toPeriode().getOrHandle { throw IllegalStateException("dfdsf") },
            status = LeggTilOppholdIUtlandetRequest.Status.valueOf(status),
            begrunnelse = begrunnelse,
        )
    }
}

internal fun Route.leggTilUtlandsoppholdRoute(
    revurderingService: RevurderingService,
) {
    authorize(Brukerrolle.Saksbehandler) {
        post("$revurderingPath/{revurderingId}/oppholdIUtlandet") {
            call.withRevurderingId { revurderingId ->
                call.withBody<UtlandsoppholdBody> { body ->
                    val req = body.toRequest(revurderingId)
                    call.svar(
                        revurderingService.leggTilUtlandsopphold(req).mapLeft {
                            when (it) {
                                KunneIkkeLeggeTilUtlandsopphold.FantIkkeBehandling -> HttpStatusCode.NotFound.errorJson(
                                    "Fant ikke revurdering",
                                    "fant_ikke_revurdering",
                                )
                                KunneIkkeLeggeTilUtlandsopphold.OverlappendeVurderingsperioder -> Feilresponser.overlappendeVurderingsperioder
                                KunneIkkeLeggeTilUtlandsopphold.PeriodeForGrunnlagOgVurderingErForskjellig -> Feilresponser.periodeForGrunnlagOgVurderingErForskjellig
                                is KunneIkkeLeggeTilUtlandsopphold.UgyldigTilstand -> Feilresponser.ugyldigTilstand(
                                    it.fra,
                                    it.til,
                                )
                                KunneIkkeLeggeTilUtlandsopphold.VurderingsperiodeUtenforBehandlingsperiode -> Feilresponser.utenforBehandlingsperioden
                            }
                        }.map {
                            Resultat.json(HttpStatusCode.OK, serialize(it.toJson()))
                        }.getOrHandle { it },
                    )
                }
            }
        }
    }
}
