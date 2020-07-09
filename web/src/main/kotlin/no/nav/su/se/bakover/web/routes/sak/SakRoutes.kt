package no.nav.su.se.bakover.web.routes.sak

import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.lesFnr
import no.nav.su.se.bakover.web.lesUUID
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.svar

internal const val sakPath = "/saker"

@KtorExperimentalAPI
internal fun Route.sakRoutes(
    sakRepo: ObjectRepo
) {
    get(sakPath) {
        call.lesFnr("fnr").fold(
            ifLeft = { call.svar(BadRequest.message(it)) },
            ifRight = { fnr ->
                call.audit("Henter sak for fnr: $fnr")
                when (val sak = sakRepo.hentSak(fnr)) {
                    null -> call.svar(NotFound.message("Fant ikke noen sak for person: $fnr"))
                    else -> call.svar(OK.jsonBody(sak))
                }
            }
        )
    }
    get("$sakPath/{id}") {
        call.lesUUID("id").fold(
            ifLeft = { call.svar(BadRequest.message(it)) },
            ifRight = { id ->
                call.audit("Henter sak med id: $id")
                when (val sak = sakRepo.hentSak(id)) {
                    null -> call.svar(NotFound.message("Fant ikke sak med id: $id"))
                    else -> call.svar(OK.jsonBody(sak))
                }
            }
        )
    }
}
