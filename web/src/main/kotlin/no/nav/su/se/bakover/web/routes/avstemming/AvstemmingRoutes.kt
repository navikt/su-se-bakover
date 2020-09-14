package no.nav.su.se.bakover.web.routes.avstemming

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.post
import io.ktor.routing.routing
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher

private const val AVSTEMMING_PATH = "/avstem"

// TODO automatic job or something probably
internal fun Application.avstemmingRoutes(
    repo: ObjectRepo,
    publisher: AvstemmingPublisher
) {
    routing {
        post(AVSTEMMING_PATH) {
            publisher.publish(AvstemmingBuilder(repo).build()).fold(
                { call.respond(HttpStatusCode.InternalServerError, "Kunne ikke avstemme") },
                {
                    repo.opprettAvstemming(it).also { it.updateUtbetalinger() }
                    call.respond("Avstemt ok")
                }
            )
        }
    }
}
