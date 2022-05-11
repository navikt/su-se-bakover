package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.periode.PeriodeJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.vilkår.LeggTilUtenlandsoppholdRequest
import no.nav.su.se.bakover.service.vilkår.UtenlandsoppholdStatus
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.periode.toPeriodeOrResultat
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
            periode = periode.toPeriodeOrResultat().getOrHandle { return it.left() },
            status = status,
            begrunnelse = begrunnelse,
        ).right()
    }
}

internal fun Route.leggTilUtenlandsopphold(
    søknadsbehandlingService: SøknadsbehandlingService,
    satsFactory: SatsFactory,
) {
    post("$behandlingPath/{behandlingId}/utenlandsopphold") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBehandlingId { behandlingId ->
                call.withBody<UtenlandsoppholdBody> { body ->
                    søknadsbehandlingService.leggTilUtenlandsopphold(
                        body.toRequest(behandlingId)
                            .getOrHandle { return@withBehandlingId call.svar(it) },
                    )
                        .mapLeft {
                            call.svar(it.tilResultat())
                        }.map {
                            call.svar(
                                Resultat.json(
                                    HttpStatusCode.Created,
                                    serialize(it.toJson(satsFactory))
                                )
                            )
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
