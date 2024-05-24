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
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleService
import no.nav.su.se.bakover.kontrollsamtale.domain.KunneIkkeHenteKontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.KunneIkkeSetteNyDatoForKontrollsamtale
import java.time.LocalDate

const val SAK_PATH = "/saker"

fun Route.kontrollsamtaleRoutes(
    kontrollsamtaleService: KontrollsamtaleService,
) {
    post("$SAK_PATH/{sakId}/kontrollsamtaler/nyDato") {
        authorize(Brukerrolle.Saksbehandler) {
            data class Body(
                val nyDato: LocalDate,
            )
            call.withSakId { sakId ->
                call.withBody<Body> { body ->
                    kontrollsamtaleService.nyDato(sakId, body.nyDato).fold(
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
                        { call.svar(Resultat.okJson()) },
                    )
                }
            }
        }
    }

    get("$SAK_PATH/{sakId}/kontrollsamtaler/hent") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                kontrollsamtaleService.hentNestePlanlagteKontrollsamtale(sakId).fold(
                    {
                        call.svar(
                            when (it) {
                                KunneIkkeHenteKontrollsamtale.KunneIkkeHenteKontrollsamtaler -> Feilresponser.kunneIkkeHenteNesteKontrollsamtale
                                KunneIkkeHenteKontrollsamtale.FantIkkePlanlagtKontrollsamtale -> HttpStatusCode.NotFound.errorJson(
                                    "Fant ikke planlagt kontrollsamle",
                                    "fant_ikke_planlagt_kontrollsamtale",
                                )
                            },
                        )
                    },
                    {
                        call.svar(Resultat.json(HttpStatusCode.OK, it.toJson()))
                    },
                )
            }
        }
    }

    get("$SAK_PATH/{sakId}/kontrollsamtaler") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                kontrollsamtaleService.hentKontrollsamtaler(sakId).let {
                    call.svar(Resultat.json(HttpStatusCode.OK, it.toJson()))
                }
            }
        }
    }
}
