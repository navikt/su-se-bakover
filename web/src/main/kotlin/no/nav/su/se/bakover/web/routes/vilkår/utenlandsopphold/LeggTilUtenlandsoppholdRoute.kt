package no.nav.su.se.bakover.web.routes.vilkår.utenlandsopphold

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.audit.application.AuditLogEvent
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.periode.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBehandlingId
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeLeggeTilUtenlandsopphold
import no.nav.su.se.bakover.domain.revurdering.RevurderingService
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.vilkår.utenlandsopphold.LeggTilFlereUtenlandsoppholdRequest
import no.nav.su.se.bakover.domain.vilkår.utenlandsopphold.LeggTilUtenlandsoppholdRequest
import no.nav.su.se.bakover.domain.vilkår.utenlandsopphold.UtenlandsoppholdStatus
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.periode.toPeriodeOrResultat
import no.nav.su.se.bakover.web.routes.revurdering.revurderingPath
import no.nav.su.se.bakover.web.routes.revurdering.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.behandlingPath
import no.nav.su.se.bakover.web.routes.søknadsbehandling.toJson
import java.util.UUID

internal fun Route.leggTilUtenlandsopphold(
    søknadsbehandlingService: SøknadsbehandlingService,
    satsFactory: SatsFactory,
) {
    post("$behandlingPath/{behandlingId}/utenlandsopphold") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBehandlingId { behandlingId ->
                call.withBody<UtenlandsoppholdBody> { body ->
                    if (body.vurderinger.size != 1) {
                        call.svar(
                            BadRequest.errorJson(
                                "Flere vurderingsperioder ble sendt inn. Forventet kun 1",
                                "flere_perioder_ble_Sendt_inn",
                            ),
                        )
                        return@withBehandlingId
                    }
                    body.toDomain(behandlingId).mapLeft {
                        call.svar(it)
                    }.map { request ->
                        søknadsbehandlingService.leggTilUtenlandsopphold(request, saksbehandler = call.suUserContext.saksbehandler).fold(
                            ifLeft = { call.svar(it.tilResultat()) },
                            ifRight = {
                                call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                                call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson(satsFactory))))
                            },
                        )
                    }
                }
            }
        }
    }
}

internal fun Route.leggTilUtlandsoppholdRoute(
    revurderingService: RevurderingService,
    satsFactory: SatsFactory,
) {
    post("$revurderingPath/{revurderingId}/utenlandsopphold") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withRevurderingId { revurderingId ->
                call.withBody<UtenlandsoppholdBody> { body ->
                    body.toDomain(revurderingId).mapLeft {
                        call.svar(it)
                    }.map {
                        revurderingService.leggTilUtenlandsopphold(it)
                            .fold(
                                ifLeft = { call.svar(it.tilResultat()) },
                                ifRight = {
                                    call.audit(it.revurdering.fnr, AuditLogEvent.Action.UPDATE, it.revurdering.id)
                                    call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson(satsFactory))))
                                },
                            )
                    }
                }
            }
        }
    }
}

internal data class UtenlandsoppholdBody(
    val vurderinger: List<UtenlandsoppholdVurderingBody>,
) {
    fun toDomain(revurderingId: UUID): Either<Resultat, LeggTilFlereUtenlandsoppholdRequest> =
        LeggTilFlereUtenlandsoppholdRequest(
            behandlingId = revurderingId,
            request = vurderinger.map {
                LeggTilUtenlandsoppholdRequest(
                    behandlingId = revurderingId,
                    periode = it.periode.toPeriodeOrResultat().getOrElse { return it.left() },
                    status = it.status,
                )
            }.toNonEmptyList(),
        ).right()
}

internal data class UtenlandsoppholdVurderingBody(
    val periode: PeriodeJson,
    val status: UtenlandsoppholdStatus,
)

internal fun SøknadsbehandlingService.KunneIkkeLeggeTilUtenlandsopphold.tilResultat(): Resultat {
    return when (this) {
        SøknadsbehandlingService.KunneIkkeLeggeTilUtenlandsopphold.FantIkkeBehandling -> {
            Feilresponser.fantIkkeBehandling
        }

        is SøknadsbehandlingService.KunneIkkeLeggeTilUtenlandsopphold.UgyldigTilstand -> {
            Feilresponser.ugyldigTilstand(fra = this.fra, til = this.til)
        }

        SøknadsbehandlingService.KunneIkkeLeggeTilUtenlandsopphold.VurderingsperiodeUtenforBehandlingsperiode -> {
            Feilresponser.utenforBehandlingsperioden
        }

        SøknadsbehandlingService.KunneIkkeLeggeTilUtenlandsopphold.AlleVurderingsperioderMåHaSammeResultat -> {
            Feilresponser.alleResultaterMåVæreLike
        }

        SøknadsbehandlingService.KunneIkkeLeggeTilUtenlandsopphold.MåInneholdeKunEnVurderingsperiode -> {
            Feilresponser.måInnheholdeKunEnVurderingsperiode
        }

        SøknadsbehandlingService.KunneIkkeLeggeTilUtenlandsopphold.MåVurdereHelePerioden -> {
            Feilresponser.måVurdereHelePerioden
        }
    }
}

internal fun KunneIkkeLeggeTilUtenlandsopphold.tilResultat(): Resultat {
    return when (this) {
        KunneIkkeLeggeTilUtenlandsopphold.FantIkkeBehandling -> Feilresponser.fantIkkeBehandling

        KunneIkkeLeggeTilUtenlandsopphold.OverlappendeVurderingsperioder -> {
            Feilresponser.overlappendeVurderingsperioder
        }

        KunneIkkeLeggeTilUtenlandsopphold.PeriodeForGrunnlagOgVurderingErForskjellig -> {
            Feilresponser.periodeForGrunnlagOgVurderingErForskjellig
        }

        is KunneIkkeLeggeTilUtenlandsopphold.UgyldigTilstand -> {
            Feilresponser.ugyldigTilstand(this.fra, this.til)
        }

        KunneIkkeLeggeTilUtenlandsopphold.VurderingsperiodeUtenforBehandlingsperiode -> {
            Feilresponser.utenforBehandlingsperioden
        }

        KunneIkkeLeggeTilUtenlandsopphold.AlleVurderingsperioderMåHaSammeResultat -> {
            Feilresponser.alleResultaterMåVæreLike
        }

        KunneIkkeLeggeTilUtenlandsopphold.MåVurdereHelePerioden -> {
            Feilresponser.måVurdereHelePerioden
        }
    }
}
