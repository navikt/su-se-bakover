package no.nav.su.se.bakover.kontrollsamtale.infrastructure.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleService
import no.nav.su.se.bakover.kontrollsamtale.domain.hent.KunneIkkeHenteKontrollsamtale

fun Route.kontrollsamtaleRoutes(
    kontrollsamtaleService: KontrollsamtaleService,
) {
    get("/saker/{sakId}/kontrollsamtaler/hent") {
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

    get("/saker/{sakId}/kontrollsamtaler") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                kontrollsamtaleService.hentKontrollsamtaler(sakId).let {
                    call.svar(Resultat.json(HttpStatusCode.OK, it.toJson()))
                }
            }
        }
    }

    annullerKontrollsamtaleRoute(kontrollsamtaleService)
    opprettKontrollsamtaleRoute(kontrollsamtaleService)
    oppdaterInnkallingsmånedPåKontrollsamtale(kontrollsamtaleService)
    oppdaterStatusPåKontrollsamtale(kontrollsamtaleService)
}
