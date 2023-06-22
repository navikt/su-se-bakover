package no.nav.su.se.bakover.kontrollsamtale.infrastructure.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleService
import no.nav.su.se.bakover.kontrollsamtale.domain.KunneIkkeHenteKontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.KunneIkkeSetteNyDatoForKontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.infrastructure.web.KontrollsamtaleJson.Companion.toJson
import java.time.LocalDate
import java.util.UUID

fun Route.kontrollsamtaleRoutes(
    kontrollsamtaleService: KontrollsamtaleService,
) {
    post("kontrollsamtale/nyDato") {
        authorize(Brukerrolle.Saksbehandler) {
            data class Body(
                val sakId: UUID,
                val nyDato: LocalDate,
            )

            call.withBody<Body> { body ->
                kontrollsamtaleService.nyDato(body.sakId, body.nyDato).fold(
                    {
                        call.svar(
                            when (it) {
                                KunneIkkeSetteNyDatoForKontrollsamtale.FantIkkeGjeldendeStønadsperiode -> Feilresponser.fantIkkeGjeldendeStønadsperiode
                                KunneIkkeSetteNyDatoForKontrollsamtale.FantIkkeSak -> Feilresponser.fantIkkeSak
                                KunneIkkeSetteNyDatoForKontrollsamtale.UgyldigStatusovergang -> Feilresponser.ugyldigStatusovergangKontrollsamtale
                                KunneIkkeSetteNyDatoForKontrollsamtale.DatoIkkeFørsteIMåned -> Feilresponser.datoMåVæreFørsteIMåned
                            },
                        )
                    },
                    {
                        call.svar(Resultat.okJson())
                    },
                )
            }
        }
    }

    get("kontrollsamtale/hent/{sakId}") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                kontrollsamtaleService.hentNestePlanlagteKontrollsamtale(sakId).fold(
                    {
                        call.svar(
                            when (it) {
                                KunneIkkeHenteKontrollsamtale.KunneIkkeHenteKontrollsamtaler -> Feilresponser.kunneIkkeHenteNesteKontrollsamtale
                                KunneIkkeHenteKontrollsamtale.FantIkkePlanlagtKontrollsamtale -> HttpStatusCode.NotFound.errorJson(
                                    "Fant ingen planlagte kontrollsamler",
                                    "fant_ikke_planlagt_kontrollsamtale",
                                )
                            },
                        )
                    },
                    {
                        call.svar(Resultat.json(HttpStatusCode.OK, serialize(it)))
                    },
                )
            }
        }
    }

    get("kontrollsamtaler/{sakId}") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                kontrollsamtaleService.hentKontrollsamtaler(sakId).let {
                    call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson())))
                }
            }
        }
    }
}
