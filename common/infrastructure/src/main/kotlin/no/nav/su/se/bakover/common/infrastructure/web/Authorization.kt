package no.nav.su.se.bakover.common.infrastructure.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.util.pipeline.PipelineContext
import no.nav.su.se.bakover.common.Brukerrolle

suspend fun PipelineContext<Unit, ApplicationCall>.authorize(
    vararg autoriserteRoller: Brukerrolle,
    build: suspend PipelineContext<Unit, ApplicationCall>.() -> Unit,
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
        build()
    }
}
