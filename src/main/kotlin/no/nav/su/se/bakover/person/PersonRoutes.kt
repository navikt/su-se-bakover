package no.nav.su.se.bakover.person

import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.*
import no.nav.su.se.bakover.ContextHolder.SecurityContext

internal const val personPath = "/person"

@KtorExperimentalAPI
internal fun Route.personRoutes(oppslag: PersonOppslag) {
    get(personPath) {
        launchWithContext(SecurityContext(call.authHeader())) {
            Fødselsnummer.lesParameter(call).fold(
                    left = { call.respond(BadRequest, it) },
                    right = {
                        call.audit("Gjør oppslag på person: $it")
                        call.svar(oppslag.person(it))
                    }
            )
        }
    }
}
