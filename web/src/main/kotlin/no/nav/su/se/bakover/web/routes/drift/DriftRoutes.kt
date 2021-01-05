package no.nav.su.se.bakover.web.routes.drift

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.patch
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.web.features.authorize

private const val DRIFT_PATH = "/drift"

@KtorExperimentalAPI
internal fun Route.driftRoutes(
    @Suppress("UNUSED_PARAMETER") service: SøknadService
) {
    authorize(Brukerrolle.Drift) {
        patch("$DRIFT_PATH/søknader/oppgaver/fix") {
            call.respond(
                HttpStatusCode.OK,
                "Called '$DRIFT_PATH/søknader/oppgaver/fix' successfully."
            )
        }
    }
}
