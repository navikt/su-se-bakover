package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.bruker.Brukerrolle
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.vilkår.LeggTilUtenlandsoppholdRequest
import no.nav.su.se.bakover.service.vilkår.UtenlandsoppholdStatus
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.PeriodeJson
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBehandlingId
import no.nav.su.se.bakover.web.withBody
import java.util.UUID

private data class UtenlandsoppholdBody(
    val periode: PeriodeJson,
    val status: UtenlandsoppholdStatus,
    val begrunnelse: String?,
) {
    fun toRequest(behandlingId: UUID): Either<Resultat, LeggTilUtenlandsoppholdRequest> {
        return LeggTilUtenlandsoppholdRequest(
            behandlingId = behandlingId,
            periode = periode.toPeriode().getOrHandle { return it.left() },
            status = status,
            begrunnelse = begrunnelse,
        ).right()
    }
}

internal fun Route.leggTilUtenlandsopphold(
    søknadsbehandlingService: SøknadsbehandlingService,
) {
    authorize(Brukerrolle.Saksbehandler) {
        post("$behandlingPath/{behandlingId}/utenlandsopphold") {
            call.withBehandlingId { behandlingId ->
                call.withBody<UtenlandsoppholdBody> { body ->
                    søknadsbehandlingService.leggTilUtenlandsopphold(body.toRequest(behandlingId).getOrHandle { return@withBehandlingId call.svar(it) })
                        .mapLeft {
                            call.svar(it.tilResultat())
                        }.map {
                            call.svar(Resultat.json(HttpStatusCode.Created, serialize(it.toJson())))
                        }
                }
            }
        }
    }
}

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
