package no.nav.su.se.bakover.sak

import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.*
import no.nav.su.se.bakover.audit
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.json
import no.nav.su.se.bakover.svar

internal const val sakPath = "/sak"
internal const val identLabel = "ident"

@KtorExperimentalAPI
internal fun Route.sakRoutes(
    sakFactory: SakFactory
) {
    get(sakPath) {
        call.parameters[identLabel]?.let { fnr ->
            call.audit("Henter sak for person: $fnr")
            call.svar(OK.json(sakFactory.forFnr(fnr).toJson()))
        } ?: call.svar(OK.json("""[${sakFactory.alle().joinToString(",") { it.toJson()}}]"""))
    }

    get("$sakPath/{id}") {
        call.parameters["id"]?.let { id ->
            call.audit("Henter sak med id: $id")
            id.toLongOrNull()?.let { idAsLong ->
                sakFactory.forId(idAsLong)
                    .fold(onError = { call.svar(NotFound.tekst("Fant ikke sak med id: $id")) },
                          onValue = { call.svar(OK.json(it.toJson())) })
            } ?: call.svar(BadRequest.tekst("Sak id må være et tall"))
        }
    }
}
