package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.getOrHandle
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
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
    fun toRequest(behandlingId: UUID): LeggTilUtenlandsoppholdRequest {
        return LeggTilUtenlandsoppholdRequest(
            behandlingId = behandlingId,
            periode = periode.toPeriode().getOrHandle { throw IllegalArgumentException("Ugyldig periodejson") },
            status = status,
            begrunnelse = begrunnelse,
        )
    }
}

internal fun Route.leggTilUtenlandsopphold(
    søknadsbehandlingService: SøknadsbehandlingService,
) {
    authorize(Brukerrolle.Saksbehandler) {
        post("$behandlingPath/{behandlingId}/utenlandsopphold") {
            call.withBehandlingId { behandlingId ->
                call.withBody<UtenlandsoppholdBody> { body ->
                    søknadsbehandlingService.leggTilUtenlandsopphold(body.toRequest(behandlingId))
                        .mapLeft {
                            call.svar(
                                when (it) {
                                    SøknadsbehandlingService.KunneIkkeLeggeTilUtenlandsopphold.FantIkkeBehandling -> {
                                        Feilresponser.fantIkkeBehandling
                                    }
                                    is SøknadsbehandlingService.KunneIkkeLeggeTilUtenlandsopphold.UgyldigTilstand -> {
                                        Feilresponser.ugyldigTilstand(fra = it.fra, til = it.til)
                                    }
                                    SøknadsbehandlingService.KunneIkkeLeggeTilUtenlandsopphold.VurderingsperiodeUtenforBehandlingsperiode -> {
                                        Feilresponser.utenforBehandlingsperioden
                                    }
                                },
                            )
                        }.map {
                            call.svar(Resultat.json(HttpStatusCode.Created, serialize(it.toJson())))
                        }
                }
            }
        }
    }
}
