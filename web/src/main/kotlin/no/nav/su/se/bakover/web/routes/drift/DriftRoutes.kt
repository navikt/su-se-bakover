package no.nav.su.se.bakover.web.routes.drift

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.drift.FixSøknaderResponseJson.Companion.toJson
import no.nav.su.se.bakover.web.svar

internal const val DRIFT_PATH = "/drift"

internal fun Route.driftRoutes(
    søknadService: SøknadService,
) {
    patch("$DRIFT_PATH/søknader/fix") {
        authorize(Brukerrolle.Drift) {
            søknadService.opprettManglendeJournalpostOgOppgave().let {
                call.svar(Resultat.json(HttpStatusCode.OK, serialize(it.toJson())))
            }
        }
    }

    get("$DRIFT_PATH/isalive") {
        authorize(Brukerrolle.Drift) {
            call.svar(Resultat.json(HttpStatusCode.OK, """{ "Status" : "OK"}"""))
        }
    }
}
