package no.nav.su.se.bakover.web.features

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.util.pipeline.PipelineContext
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.web.ErrorJson

suspend fun PipelineContext<Unit, ApplicationCall>.authorize(
    vararg autoriserteRoller: Brukerrolle,
    build: suspend PipelineContext<Unit, ApplicationCall>.() -> Unit,
) {
    try {
        /**
         * Det er løgn at kallet til [suUserContext] alltid er trygt (null-safe) her. Det er en forutsetning at vi har vært innom
         * [brukerinfoPlugin] og satt den først.
         */
        val grupper = call.suUserContext.grupper.map { Brukerrolle.valueOf(it) }
        val containsRolle = autoriserteRoller.any { grupper.contains(it) }

        if (!containsRolle) {
            call.respond(
                status = HttpStatusCode.Forbidden,
                message = ErrorJson("Bruker mangler en av de tillatte rollene: ${autoriserteRoller.toList()}"),
            )
        } else {
            build()
        }
    } catch (ex: Throwable) {
        log.error("Ukjent feil ved tilgangssjekk", ex)
        call.respond(
            status = HttpStatusCode.InternalServerError,
            message = ErrorJson("Ukjent feil ved tilgangssjekk"),
        )
    }
}
