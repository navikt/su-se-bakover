package no.nav.su.se.bakover.web.routes

import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.web.*
import no.nav.su.se.bakover.web.json
import no.nav.su.se.bakover.web.lesParameter
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.message

internal const val sakPath = "/sak"

@KtorExperimentalAPI
internal fun Route.sakRoutes(
        sakRepo: ObjectRepo
) {
    get("$sakPath/{id}") {
        Long.lesParameter(call, "id").fold(
                left = { call.svar(BadRequest.message(it)) },
                right = { id ->
                    call.audit("Henter sak med id: $id")
                    when (val sak = sakRepo.hentSak(id)) {
                        null -> call.svar(NotFound.message("Fant ikke sak med id: $id"))
                        else -> call.svar(OK.json(sak.toJson()))
                    }
                }
        )
    }
}
