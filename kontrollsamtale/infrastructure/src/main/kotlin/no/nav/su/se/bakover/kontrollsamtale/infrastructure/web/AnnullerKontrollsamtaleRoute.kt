package no.nav.su.se.bakover.kontrollsamtale.infrastructure.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withKontrollsamtaleId
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleService
import no.nav.su.se.bakover.kontrollsamtale.domain.annuller.KunneIkkeAnnullereKontrollsamtale

fun Route.annullerKontrollsamtaleRoute(
    kontrollsamtaleService: KontrollsamtaleService,
) {
    delete("/saker/{sakId}/kontrollsamtaler/{kontrollsamtaleId}") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withSakId { sakId ->
                call.withKontrollsamtaleId { kontrollsamtaleId ->
                    kontrollsamtaleService.annullerKontrollsamtale(sakId, kontrollsamtaleId).fold(
                        {
                            call.svar(
                                when (it) {
                                    KunneIkkeAnnullereKontrollsamtale.UgyldigStatusovergang -> Feilresponser.ugyldigTilstand
                                },
                            )
                        },
                        { call.svar(Resultat.json(HttpStatusCode.OK, it.toJson())) },
                    )
                }
            }
        }
    }
}
