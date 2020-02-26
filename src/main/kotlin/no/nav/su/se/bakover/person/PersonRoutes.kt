package no.nav.su.se.bakover.person

import io.ktor.application.call
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.request.header
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.Fødselsnummer
import no.nav.su.se.bakover.audit
import no.nav.su.se.bakover.svar

internal const val personPath = "/person"

@KtorExperimentalAPI
internal fun Route.personRoutes(oppslag: PersonOppslag) {
    get(personPath) {
        Fødselsnummer.extract(call).fold(
            onError = { call.respond(BadRequest, it)},
            onValue = {
                call.audit("Gjør oppslag på person: $it")
                call.svar(oppslag.person(it, call.request.header(Authorization)!!))
            }
        )
    }
}
