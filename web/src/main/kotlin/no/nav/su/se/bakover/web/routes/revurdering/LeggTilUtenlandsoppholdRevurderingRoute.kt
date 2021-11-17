package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.Nel
import arrow.core.getOrHandle
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilUtenlandsopphold
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.vilkår.LeggTilFlereUtenlandsoppholdRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUtenlandsoppholdRequest
import no.nav.su.se.bakover.service.vilkår.UtenlandsoppholdStatus
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
    val utenlandsopphold: List<UtenlandsoppholdJson>,
) {
    fun toRequest(revurderingId: UUID): LeggTilFlereUtenlandsoppholdRequest {
        return LeggTilFlereUtenlandsoppholdRequest(
            behandlingId = revurderingId,
            request = Nel.fromListUnsafe(
                utenlandsopphold.map {
                    it.toRequest(revurderingId)
                },
            ),
        )
    }
}

private data class UtenlandsoppholdJson(
    val periode: PeriodeJson,
    val status: String,
    val begrunnelse: String?,
) {
    fun toRequest(revurderingId: UUID): LeggTilUtenlandsoppholdRequest {
        return LeggTilUtenlandsoppholdRequest(
            behandlingId = revurderingId,
            periode = periode.toPeriode().getOrHandle { throw IllegalStateException("Ugyldig periode") },
            status = UtenlandsoppholdStatus.valueOf(status),
            begrunnelse = begrunnelse,
        )
    }
}

internal fun Route.leggTilUtlandsoppholdRoute(
    revurderingService: RevurderingService,
) {
    authorize(Brukerrolle.Saksbehandler) {
        post("$revurderingPath/{revurderingId}/utenlandsopphold") {
            call.withRevurderingId { revurderingId ->
                call.withBody<UtlandsoppholdBody> { body ->
                    val req = body.toRequest(revurderingId)
                    call.svar(
                        revurderingService.leggTilUtenlandsopphold(req).mapLeft {
                            when (it) {
                                KunneIkkeLeggeTilUtenlandsopphold.FantIkkeBehandling -> {
                                    HttpStatusCode.NotFound.errorJson("Fant ikke revurdering", "fant_ikke_revurdering")
                                }
                                KunneIkkeLeggeTilUtenlandsopphold.OverlappendeVurderingsperioder -> {
                                    Feilresponser.overlappendeVurderingsperioder
                                }
                                KunneIkkeLeggeTilUtenlandsopphold.PeriodeForGrunnlagOgVurderingErForskjellig -> {
                                    Feilresponser.periodeForGrunnlagOgVurderingErForskjellig
                                }
                                is KunneIkkeLeggeTilUtenlandsopphold.UgyldigTilstand -> {
                                    Feilresponser.ugyldigTilstand(it.fra, it.til)
                                }
                                KunneIkkeLeggeTilUtenlandsopphold.VurderingsperiodeUtenforBehandlingsperiode -> {
                                    Feilresponser.utenforBehandlingsperioden
                                }
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
