package no.nav.su.se.bakover.web.routes.avstemming

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.service.avstemming.AvstemmingService

private const val AVSTEMMING_PATH = "/avstem"

// TODO automatic job or something probably
internal fun Route.avstemmingRoutes(
    service: AvstemmingService
) {
    post(AVSTEMMING_PATH) {
        service.avstemming().fold(
            { call.respond(HttpStatusCode.InternalServerError, "Kunne ikke avstemme") },
            { call.respond("Avstemt ok") }
        )
    }
}
