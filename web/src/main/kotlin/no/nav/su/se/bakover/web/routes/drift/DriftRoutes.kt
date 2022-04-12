package no.nav.su.se.bakover.web.routes.drift

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.drift.FixSøknaderResponseJson.Companion.toJson

internal const val DRIFT_PATH = "/drift"

internal fun Route.driftRoutes(
    søknadService: SøknadService,
) {
    authorize(Brukerrolle.Drift) {
        patch("$DRIFT_PATH/søknader/fix") {
            søknadService.opprettManglendeJournalpostOgOppgave().let {
                call.respond(
                    HttpStatusCode.OK,
                    serialize(it.toJson())
                )
            }
        }
    }

    authorize(Brukerrolle.Drift) {
        get("$DRIFT_PATH/isalive") {
            call.respond(HttpStatusCode.OK, """{ "Status" : "OK"}""")
        }
    }
}
