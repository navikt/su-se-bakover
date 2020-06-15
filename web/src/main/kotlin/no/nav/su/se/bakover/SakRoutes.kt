package no.nav.su.se.bakover

import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.util.KtorExperimentalAPI

internal const val sakPath = "/sak"

@KtorExperimentalAPI
internal fun Route.sakRoutes(
        sakFactory: SakFactory
) {
    get("$sakPath/{id}") {
        Long.lesParameter(call, "id").fold(
                left = { call.svar(BadRequest.tekst(it)) },
                right = { id ->
                    call.audit("Henter sak med id: $id")
                    sakFactory.hent(id).fold(
                            left = { call.svar(NotFound.tekst("Fant ikke sak med id: $id")) },
                            right = { call.svar(OK.json(it.toJson())) })
                }
        )
    }
}
