package no.nav.su.se.bakover.web.routes

import arrow.core.Either
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import no.finn.unleash.strategy.Strategy
import no.nav.su.se.bakover.service.toggles.ToggleService
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.parameter
import no.nav.su.se.bakover.web.svar

private const val TOGGLES_PATH = "/toggles"

internal fun Route.toggleRoutes(toggleService: ToggleService) {

    get("$TOGGLES_PATH/{toggleName}") {
        call.parameter("toggleName").fold(
            ifLeft = { call.svar(HttpStatusCode.BadRequest.message(it)) },
            ifRight = { toggleName ->
                val toggle = mapOf(Pair(toggleName, toggleService.isEnabled(toggleName)))
                call.respond(toggle)
            }
        )
    }

    post(TOGGLES_PATH) {
        Either.catch { deserialize<List<String>>(call) }.fold(
            ifLeft = {
                call.application.environment.log.info(it.message, it)
                call.svar(HttpStatusCode.BadRequest.message("Ugyldig body"))
            },
            ifRight = { toggleNames ->
                val toggles = toggleNames.map { Pair(it, toggleService.isEnabled(it)) }.toMap()
                call.respond(toggles)
            }
        )
    }
}

class IsNotProdStrategy(private val isProd: Boolean) : Strategy {
    override fun getName() = "isNotProd"

    override fun isEnabled(parameters: Map<String, String>) = !isProd
}
