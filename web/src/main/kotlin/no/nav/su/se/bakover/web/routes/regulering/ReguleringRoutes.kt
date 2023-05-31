package no.nav.su.se.bakover.web.routes.regulering

import io.ktor.server.routing.Route
import no.nav.su.se.bakover.domain.regulering.ReguleringService
import java.time.Clock

internal const val reguleringPath = "/reguleringer"

internal fun Route.reguleringRoutes(
    reguleringService: ReguleringService,
    clock: Clock,
) {
    reguler(reguleringService, clock)
    reguleringOversiktRoutes(reguleringService)
}
