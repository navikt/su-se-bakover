package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.flatMap
import arrow.core.getOrHandle
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.audit.AuditLogEvent
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.Uføre.periodeForGrunnlagOgVurderingErForskjellig
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilUføreVilkår
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevurderingerRequest
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.grunnlag.LeggTilUførervurderingerBody

internal fun Route.leggTilGrunnlagRevurderingRoutes(
    revurderingService: RevurderingService,
    satsFactory: SatsFactory,
) {
    post("$revurderingPath/{revurderingId}/uføregrunnlag") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withRevurderingId { revurderingId ->
                call.withBody<LeggTilUførervurderingerBody> { body ->
                    call.svar(
                        body.toServiceCommand(revurderingId)
                            .flatMap { command ->
                                revurderingService.leggTilUførevilkår(command)
                                    .mapLeft {
                                        it.mapFeil()
                                    }.map {
                                        call.audit(it.revurdering.fnr, AuditLogEvent.Action.UPDATE, it.revurdering.id)
                                        Resultat.json(HttpStatusCode.Created, serialize(it.toJson(satsFactory)))
                                    }
                            }.getOrHandle { it },
                    )
                }
            }
        }
    }
}

internal fun LeggTilUførevurderingerRequest.UgyldigUførevurdering.tilResultat() =
    when (this) {
        LeggTilUførevurderingerRequest.UgyldigUførevurdering.AlleVurderingeneMåHaSammeResultat -> {
            Feilresponser.alleVurderingsperioderMåHaSammeResultat
        }

        LeggTilUførevurderingerRequest.UgyldigUførevurdering.HeleBehandlingsperiodenMåHaVurderinger -> {
            Feilresponser.heleBehandlingsperiodenMåHaVurderinger
        }

        LeggTilUførevurderingerRequest.UgyldigUførevurdering.OverlappendeVurderingsperioder -> {
            Feilresponser.overlappendeVurderingsperioder
        }

        LeggTilUførevurderingerRequest.UgyldigUførevurdering.PeriodeForGrunnlagOgVurderingErForskjellig -> {
            periodeForGrunnlagOgVurderingErForskjellig
        }

        LeggTilUførevurderingerRequest.UgyldigUførevurdering.UføregradOgForventetInntektMangler -> {
            Feilresponser.Uføre.uføregradOgForventetInntektMangler
        }

        LeggTilUførevurderingerRequest.UgyldigUførevurdering.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden -> {
            Feilresponser.utenforBehandlingsperioden
        }
    }

private fun KunneIkkeLeggeTilUføreVilkår.mapFeil(): Resultat {
    return when (this) {
        KunneIkkeLeggeTilUføreVilkår.FantIkkeBehandling -> {
            Feilresponser.fantIkkeBehandling
        }

        is KunneIkkeLeggeTilUføreVilkår.UgyldigInput -> this.originalFeil.tilResultat()
        is KunneIkkeLeggeTilUføreVilkår.UgyldigTilstand -> {
            Feilresponser.ugyldigTilstand(
                fra = fra,
                til = til,
            )
        }

        KunneIkkeLeggeTilUføreVilkår.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden -> {
            Feilresponser.utenforBehandlingsperioden
        }
    }
}
