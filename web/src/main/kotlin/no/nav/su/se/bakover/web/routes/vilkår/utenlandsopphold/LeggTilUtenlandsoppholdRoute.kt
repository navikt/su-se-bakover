package no.nav.su.se.bakover.web.routes.vilkår.utenlandsopphold

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.periode.PeriodeJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilUtenlandsopphold
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.vilkår.LeggTilFlereUtenlandsoppholdRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUtenlandsoppholdRequest
import no.nav.su.se.bakover.service.vilkår.UtenlandsoppholdStatus
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.periode.toPeriodeOrResultat
import no.nav.su.se.bakover.web.routes.revurdering.revurderingPath
import no.nav.su.se.bakover.web.routes.revurdering.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.behandlingPath
import no.nav.su.se.bakover.web.routes.søknadsbehandling.toJson
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBehandlingId
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId
import java.util.UUID

internal fun Route.leggTilUtenlandsopphold(
    søknadsbehandlingService: SøknadsbehandlingService,
    satsFactory: SatsFactory,
) {
    post("$behandlingPath/{behandlingId}/utenlandsopphold") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBehandlingId { behandlingId ->
                call.withBody<UtenlandsoppholdBody> { body ->
                    body.tilLeggTilUtenlandsoppholdrequest(behandlingId).mapLeft {
                        call.svar(it)
                    }.map { request ->
                        søknadsbehandlingService.leggTilUtenlandsopphold(request).fold(
                            ifLeft = {
                                call.svar(it.tilResultat())
                            },
                            ifRight = {
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
                    body.tilLeggTilFlereUtenlandsoppholdRequest(revurderingId).mapLeft {
                        call.svar(it)
                    }.map {
                        revurderingService.leggTilUtenlandsopphold(it)
                            .fold(
                                ifLeft = {
                                    call.svar(it.tilResultat())
                                },
                                ifRight = {
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
    fun tilLeggTilUtenlandsoppholdrequest(behandlingId: UUID): Either<Resultat, LeggTilUtenlandsoppholdRequest> =
        if (vurderinger.size != 1) BadRequest.errorJson(
            "Flere vurderingsperioder ble sendt inn. Forventet kun 1",
            "flere_perioder_ble_Sendt_inn",
        ).left() else LeggTilUtenlandsoppholdRequest(
            behandlingId = behandlingId,
            periode = vurderinger.first().periode.toPeriodeOrResultat().getOrHandle { return it.left() },
            status = vurderinger.first().status,
        ).right()

    fun tilLeggTilFlereUtenlandsoppholdRequest(revurderingId: UUID): Either<Resultat, LeggTilFlereUtenlandsoppholdRequest> =
        LeggTilFlereUtenlandsoppholdRequest(
            behandlingId = revurderingId,
            request = Nel.fromListUnsafe(
                vurderinger.map {
                    LeggTilUtenlandsoppholdRequest(
                        behandlingId = revurderingId,
                        periode = it.periode.toPeriodeOrResultat().getOrHandle { return it.left() },
                        status = it.status,
                    )
                },
            ),
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
