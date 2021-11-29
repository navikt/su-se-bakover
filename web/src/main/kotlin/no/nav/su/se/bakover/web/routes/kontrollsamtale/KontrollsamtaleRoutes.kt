package no.nav.su.se.bakover.web.routes.kontrollsamtale

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.service.kontrollsamtale.KontrollsamtaleService
import no.nav.su.se.bakover.service.kontrollsamtale.KunneIkkeKalleInnTilKontrollsamtale
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import java.util.UUID

internal fun Route.kontrollsamtaleRoutes(
    kontrollsamtaleService: KontrollsamtaleService
) {
    authorize(Brukerrolle.Saksbehandler) {
        post("kontrollsamtale/kallInn") {
            data class Body(
                val sakId: UUID,
            )

            call.withBody<Body> { body ->
                kontrollsamtaleService.kallInn(body.sakId, NavIdentBruker.Saksbehandler(call.suUserContext.navIdent)).fold(
                    {
                        call.svar(
                            when (it) {
                                KunneIkkeKalleInnTilKontrollsamtale.FantIkkeSak -> Feilresponser.fantIkkeSak
                                KunneIkkeKalleInnTilKontrollsamtale.FantIkkePerson -> Feilresponser.fantIkkePerson
                                KunneIkkeKalleInnTilKontrollsamtale.KunneIkkeGenerereDokument -> Feilresponser.feilVedGenereringAvDokument
                                KunneIkkeKalleInnTilKontrollsamtale.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> Feilresponser.fantIkkeSaksbehandlerEllerAttestant
                                KunneIkkeKalleInnTilKontrollsamtale.KunneIkkeKalleInn -> HttpStatusCode.InternalServerError.errorJson(
                                    "Kunne ikke kalle inn til kontrollsamtale",
                                    "kunne_ikke_kalle_inn_til_kontrollsamtale",
                                )
                            }
                        )
                    },
                    {
                        call.svar(Resultat.okJson(HttpStatusCode.OK))
                    }
                )
            }
        }
    }
}
