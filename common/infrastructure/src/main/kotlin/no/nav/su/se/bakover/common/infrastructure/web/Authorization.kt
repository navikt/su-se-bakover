package no.nav.su.se.bakover.common.infrastructure.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.RoutingHandler
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle

suspend fun RoutingContext.authorize(
    vararg autoriserteRoller: Brukerrolle,
    body: RoutingHandler,
) {
    /**
     * Det er løgn at kallet til [suUserContext] alltid er trygt (null-safe) her. Det er en forutsetning at vi har vært innom
     * [brukerinfoPlugin] og satt den først.
     */
    val autorisert = autoriserteRoller.any { call.suUserContext.roller.contains(it) }

    if (!autorisert) {
        call.svar(
            HttpStatusCode.Forbidden.errorJson(
                message = "Bruker mangler en av de tillatte rollene: ${autoriserteRoller.toList()}",
                code = "mangler_rolle",
            ),
        )
    } else {
        body()
    }
}
