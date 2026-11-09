package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.merge
import arrow.core.raise.either
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser.Uføre.periodeForGrunnlagOgVurderingErForskjellig
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.domain.revurdering.vilkår.uføre.KunneIkkeLeggeTilUføreVilkår
import no.nav.su.se.bakover.domain.vilkår.uføre.LeggTilUførevurderingerRequest
import no.nav.su.se.bakover.web.inputvalidation.withValidertBody
import no.nav.su.se.bakover.web.routes.grunnlag.LeggTilUførervurderingerBody
import org.slf4j.LoggerFactory
import vilkår.formue.domain.FormuegrenserFactory

internal fun Route.leggTilGrunnlagRevurderingRoutes(
    revurderingService: RevurderingService,
    formuegrenserFactory: FormuegrenserFactory,
) {
    val log = LoggerFactory.getLogger(this::class.java)

    post("$REVURDERING_PATH/{revurderingId}/uføregrunnlag") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withRevurderingId { revurderingId ->
                call.withValidertBody<LeggTilUførervurderingerBody>(log) { body ->
                    either {
                        val command = body.toServiceCommand(RevurderingId(revurderingId)).bind()
                        val oppdatert = revurderingService.leggTilUførevilkår(command)
                            .mapLeft { kunneIkkeLeggeTilUføreVilkår -> kunneIkkeLeggeTilUføreVilkår.tilResultat() }
                            .bind()

                        call.audit(oppdatert.revurdering.fnr, AuditLogEvent.Action.UPDATE, oppdatert.revurdering.id.value)
                        Resultat.json(HttpStatusCode.Created, serialize(oppdatert.toJson(formuegrenserFactory)))
                    }.merge().let { resultat -> call.svar(resultat) }
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

private fun KunneIkkeLeggeTilUføreVilkår.tilResultat(): Resultat {
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
