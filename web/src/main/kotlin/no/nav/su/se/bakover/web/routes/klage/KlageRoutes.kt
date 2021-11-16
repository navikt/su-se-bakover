package no.nav.su.se.bakover.web.routes.klage

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.klage.KlageService
import no.nav.su.se.bakover.service.klage.NyKlageRequest
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.features.suUserContext
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withSakId

internal const val klagePath = "$sakPath/{sakId}/klager"

internal fun Route.klageRoutes(
    klageService: KlageService,
) {
    authorize(Brukerrolle.Saksbehandler) {
        post(klagePath) {
            data class Body(val journalpostId: String)
            call.withSakId { sakId ->
                call.withBody<Body> { body ->
                    klageService.opprettKlage(
                        NyKlageRequest(
                            sakId = sakId,
                            navIdent = body.journalpostId,
                            journalpostId = call.suUserContext.navIdent,
                        ),
                    )
                    call.svar(Resultat.json(HttpStatusCode.OK, "{}"))
                }
            }
        }
    }
}
