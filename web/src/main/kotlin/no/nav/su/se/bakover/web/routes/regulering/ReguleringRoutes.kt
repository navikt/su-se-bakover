package no.nav.su.se.bakover.web.routes.regulering

import io.ktor.server.routing.Route
import no.nav.su.se.bakover.service.regulering.ReguleringService

internal const val reguleringPath = "/reguleringer"

internal fun Route.reguleringRoutes(
    reguleringService: ReguleringService,
) {
    reguler(reguleringService)
    reguleringOversiktRoutes(reguleringService)
}
