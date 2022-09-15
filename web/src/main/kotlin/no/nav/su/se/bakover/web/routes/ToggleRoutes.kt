package no.nav.su.se.bakover.web.routes

import arrow.core.Either
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.service.toggles.ToggleService
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.parameter
import no.nav.su.se.bakover.web.svar

private const val TOGGLES_PATH = "/toggles"

internal val togglePaths = listOf(TOGGLES_PATH)

internal fun Route.toggleRoutes(toggleService: ToggleService) {
    get("$TOGGLES_PATH/{toggleName}") {
        call.parameter("toggleName").fold(
            ifLeft = { call.svar(it) },
            ifRight = { toggleName ->
                val toggle = mapOf(Pair(toggleName, toggleService.isEnabled(toggleName)))
                call.respond(toggle)
            },
        )
    }

    post(TOGGLES_PATH) {
        Either.catch { deserialize<List<String>>(call) }.fold(
            ifLeft = {
                log.info(it.message, it)
                call.svar(Feilresponser.ugyldigBody)
            },
            ifRight = { toggleNames ->
                val toggles = toggleNames.associateWith { toggleService.isEnabled(it) }
                call.respond(toggles)
            },
        )
    }
}
