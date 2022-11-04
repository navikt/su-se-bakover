package no.nav.su.se.bakover.web.routes.vilkår.lovligopphold

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.audit.application.AuditLogEvent
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.periode.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBehandlingId
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.domain.vilkår.KunneIkkeLageLovligOppholdVilkår
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.vilkår.KunneIkkeLeggetilLovligOppholdVilkår
import no.nav.su.se.bakover.service.vilkår.LeggTilLovligOppholdRequest
import no.nav.su.se.bakover.service.vilkår.LovligOppholdVilkårStatus
import no.nav.su.se.bakover.service.vilkår.LovligOppholdVurderinger
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.revurdering.revurderingPath
import no.nav.su.se.bakover.web.routes.revurdering.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.behandlingPath
import no.nav.su.se.bakover.web.routes.søknadsbehandling.toJson
import java.util.UUID

internal fun Route.leggTilLovligOppholdRoute(
    søknadsbehandlingService: SøknadsbehandlingService,
    satsFactory: SatsFactory,
) {
    post("$behandlingPath/{behandlingId}/lovligopphold") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBehandlingId { behandlingId ->
                call.withBody<LovligOppholdBody> { body ->
                    søknadsbehandlingService.leggTilLovligOpphold(body.toLovligOppholdRequest(behandlingId), saksbehandler = call.suUserContext.saksbehandler).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                            call.svar(Resultat.json(HttpStatusCode.Created, serialize(it.toJson(satsFactory))))
                        },
                    )
                }
            }
        }
    }
}

internal fun Route.leggTilLovligOppholdRoute(
    revurderingService: RevurderingService,
    satsFactory: SatsFactory,
) {
    post("$revurderingPath/{revurderingId}/lovligopphold") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withRevurderingId { revurderingId ->
                call.withBody<LovligOppholdBody> { body ->
                    revurderingService.leggTilLovligOppholdVilkår(body.toLovligOppholdRequest(revurderingId)).fold(
                        ifLeft = { call.svar(it.tilResultat()) },
                        ifRight = {
                            call.audit(it.revurdering.fnr, AuditLogEvent.Action.UPDATE, it.revurdering.id)
                            call.svar(Resultat.json(HttpStatusCode.Created, serialize(it.toJson(satsFactory))))
                        },
                    )
                }
            }
        }
    }
}

internal data class LovligOppholdBody(
    val vurderinger: List<LovligOppholdVurderingBody>,
) {
    fun toLovligOppholdRequest(behandlingId: UUID) = LeggTilLovligOppholdRequest(
        behandlingId = behandlingId,
        vurderinger = vurderinger.map { LovligOppholdVurderinger(it.periode.toPeriode(), it.status) },
    )
}

internal data class LovligOppholdVurderingBody(
    val periode: PeriodeJson,
    val status: LovligOppholdVilkårStatus,
)

internal fun KunneIkkeLeggetilLovligOppholdVilkår.tilResultat() = when (this) {
    KunneIkkeLeggetilLovligOppholdVilkår.FantIkkeBehandling -> Feilresponser.fantIkkeBehandling
    is KunneIkkeLeggetilLovligOppholdVilkår.FeilVedSøknadsbehandling -> this.feil.tilResultat()
    is KunneIkkeLeggetilLovligOppholdVilkår.UgyldigLovligOppholdVilkår -> this.feil.tilResultat()
}

internal fun KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilLovligOpphold.tilResultat() = when (this) {
    is KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilLovligOpphold.UgyldigTilstand -> when (this) {
        is KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilLovligOpphold.UgyldigTilstand.Revurdering -> Feilresponser.ugyldigTilstand(
            this.fra,
            this.til,
        )

        is KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilLovligOpphold.UgyldigTilstand.Søknadsbehandling -> Feilresponser.ugyldigTilstand(
            this.fra,
            this.til,
        )
    }

    KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilLovligOpphold.HeleBehandlingsperiodenErIkkeVurdert -> Feilresponser.heleBehandlingsperiodenMåHaVurderinger
}

internal fun KunneIkkeLageLovligOppholdVilkår.tilResultat() = when (this) {
    KunneIkkeLageLovligOppholdVilkår.OverlappendeVurderingsperioder -> Feilresponser.overlappendeVurderingsperioder
    KunneIkkeLageLovligOppholdVilkår.Vurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig -> Feilresponser.periodeForGrunnlagOgVurderingErForskjellig
}
