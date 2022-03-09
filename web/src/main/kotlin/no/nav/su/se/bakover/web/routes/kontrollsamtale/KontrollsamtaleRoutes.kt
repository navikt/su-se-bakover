package no.nav.su.se.bakover.web.routes.kontrollsamtale

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.kontrollsamtale.KontrollsamtaleService
import no.nav.su.se.bakover.service.kontrollsamtale.KunneIkkeHenteKontrollsamtale
import no.nav.su.se.bakover.service.kontrollsamtale.KunneIkkeSetteNyDatoForKontrollsamtale
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withSakId
import java.time.LocalDate
import java.util.UUID

internal fun Route.kontrollsamtaleRoutes(
    kontrollsamtaleService: KontrollsamtaleService
) {
    authorize(Brukerrolle.Saksbehandler) {
        post("kontrollsamtale/nyDato") {
            data class Body(
                val sakId: UUID,
                val nyDato: LocalDate
            )

            call.withBody<Body> { body ->
                kontrollsamtaleService.nyDato(body.sakId, body.nyDato).fold(
                    {
                        call.svar(
                            when (it) {
                                KunneIkkeSetteNyDatoForKontrollsamtale.FantIkkeGjeldendeStønadsperiode -> Feilresponser.fantIkkeGjeldendeStønadsperiode
                                KunneIkkeSetteNyDatoForKontrollsamtale.FantIkkeSak -> Feilresponser.fantIkkeSak
                                KunneIkkeSetteNyDatoForKontrollsamtale.UgyldigStatusovergang -> Feilresponser.ugyldigStatusovergangKontrollsamtale
                            }
                        )
                    },
                    {
                        call.svar(Resultat.okJson(HttpStatusCode.OK))
                    }
                )
            }
        }

        get("kontrollsamtale/hent/{sakId}") {
            call.withSakId { sakId ->
                kontrollsamtaleService.hentNestePlanlagteKontrollsamtale(sakId).fold(
                    {
                        call.svar(
                            when (it) {
                                KunneIkkeHenteKontrollsamtale.KunneIkkeHenteKontrollsamtaler -> Feilresponser.kunneIkkeHenteNesteKontrollsamtale
                                KunneIkkeHenteKontrollsamtale.FantIkkeKontrollsamtale -> Resultat.json(
                                    HttpStatusCode.OK, "null"
                                )
                            }
                        )
                    },
                    {
                        call.svar(Resultat.json(HttpStatusCode.OK, serialize(it)))
                    }
                )
            }
        }
    }
}
