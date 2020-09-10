package no.nav.su.se.bakover.web.routes

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.post
import io.ktor.routing.routing
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher

private const val AVSTEMMING_PATH = "/avstem"

internal fun Application.avstemmingRoutes(
    repo: ObjectRepo,
    publisher: AvstemmingPublisher
) {
    routing {
        post(AVSTEMMING_PATH) {
            val utbetalingerTilAvstemming = repo.hentUtbetalingerTilAvstemming()
            publisher.publish(utbetalingerTilAvstemming).fold(
                { call.respond(HttpStatusCode.InternalServerError, "Kunne ikke avstemme") },
                { call.respond("Avstemt ok") }
            )
        }
    }
}
