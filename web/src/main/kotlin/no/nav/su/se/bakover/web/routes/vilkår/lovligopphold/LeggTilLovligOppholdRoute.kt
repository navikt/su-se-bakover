package no.nav.su.se.bakover.web.routes.vilkår.lovligopphold

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBehandlingId
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.domain.revurdering.vilkår.opphold.KunneIkkeOppdatereLovligOppholdOgMarkereSomVurdert
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.vilkår.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.domain.vilkår.KunneIkkeLageLovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.lovligopphold.KunneIkkeLeggetilLovligOppholdVilkårForRevurdering
import no.nav.su.se.bakover.domain.vilkår.lovligopphold.KunneIkkeLeggetilLovligOppholdVilkårForSøknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.lovligopphold.LeggTilLovligOppholdRequest
import no.nav.su.se.bakover.domain.vilkår.lovligopphold.LovligOppholdVilkårStatus
import no.nav.su.se.bakover.domain.vilkår.lovligopphold.LovligOppholdVurderinger
import no.nav.su.se.bakover.web.routes.revurdering.REVURDERING_PATH
import no.nav.su.se.bakover.web.routes.revurdering.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SØKNADSBEHANDLING_PATH
import no.nav.su.se.bakover.web.routes.søknadsbehandling.toJson
import java.util.UUID

internal fun Route.leggTilLovligOppholdRoute(
    søknadsbehandlingService: SøknadsbehandlingService,
    satsFactory: SatsFactory,
) {
    post("$SØKNADSBEHANDLING_PATH/{behandlingId}/lovligopphold") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBehandlingId { behandlingId ->
                call.withBody<LovligOppholdBody> { body ->
                    søknadsbehandlingService.leggTilLovligOpphold(
                        body.toLovligOppholdRequest(behandlingId),
                        saksbehandler = call.suUserContext.saksbehandler,
                    ).fold(
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
    post("$REVURDERING_PATH/{revurderingId}/lovligopphold") {
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

internal fun KunneIkkeLeggetilLovligOppholdVilkårForSøknadsbehandling.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeLeggetilLovligOppholdVilkårForSøknadsbehandling.FantIkkeBehandling -> Feilresponser.fantIkkeBehandling
        is KunneIkkeLeggetilLovligOppholdVilkårForSøknadsbehandling.FeilVedSøknadsbehandling -> this.feil.tilResultat()
        is KunneIkkeLeggetilLovligOppholdVilkårForSøknadsbehandling.UgyldigLovligOppholdVilkår -> this.feil.tilResultat()
    }
}

internal fun KunneIkkeLeggetilLovligOppholdVilkårForRevurdering.tilResultat(): Resultat {
    return when (this) {
        is KunneIkkeLeggetilLovligOppholdVilkårForRevurdering.Domenefeil -> this.underliggende.tilResultat()
        KunneIkkeLeggetilLovligOppholdVilkårForRevurdering.FantIkkeBehandling -> Feilresponser.fantIkkeBehandling
        is KunneIkkeLeggetilLovligOppholdVilkårForRevurdering.UgyldigLovligOppholdVilkår -> this.underliggende.tilResultat()
    }
}

internal fun KunneIkkeOppdatereLovligOppholdOgMarkereSomVurdert.tilResultat(): Resultat {
    return when (this) {
        is KunneIkkeOppdatereLovligOppholdOgMarkereSomVurdert.HeleBehandlingsperiodenErIkkeVurdert -> Feilresponser.måVurdereHelePerioden
        is KunneIkkeOppdatereLovligOppholdOgMarkereSomVurdert.UgyldigTilstand -> Feilresponser.ugyldigTilstand(
            this.fra,
            this.til,
        )
    }
}

internal fun KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilLovligOpphold.tilResultat(): Resultat {
    return when (this) {
        is KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilLovligOpphold.Vilkårsfeil -> Feilresponser.heleBehandlingsperiodenMåHaVurderinger
    }
}

internal fun KunneIkkeLageLovligOppholdVilkår.tilResultat() = when (this) {
    KunneIkkeLageLovligOppholdVilkår.OverlappendeVurderingsperioder -> Feilresponser.overlappendeVurderingsperioder
    KunneIkkeLageLovligOppholdVilkår.Vurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig -> Feilresponser.periodeForGrunnlagOgVurderingErForskjellig
}
