package no.nav.su.se.bakover.person

import io.ktor.application.call
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.request.header
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.audit
import no.nav.su.se.bakover.svar

internal const val personPath = "/person"
internal const val identLabel = "ident"

@KtorExperimentalAPI
internal fun Route.personRoutes(oppslag: PersonOppslag) {
    get(personPath) {
        call.parameters[identLabel]?.let { personIdent ->
            call.audit("Gjør oppslag på person: $personIdent")
            call.svar(oppslag.person(personIdent, call.request.header(Authorization)!!))
        } ?: call.respond(BadRequest, "query param '$identLabel' må oppgis")
    }
}
