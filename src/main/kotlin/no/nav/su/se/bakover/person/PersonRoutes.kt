package no.nav.su.se.bakover.person

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.fromValue
import io.ktor.request.header
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.Feil
import no.nav.su.se.bakover.Ok
import org.slf4j.LoggerFactory

const val personPath = "/person"
const val identLabel = "ident"

private val sikkerLogg = LoggerFactory.getLogger("sikkerLogg")

@KtorExperimentalAPI
internal fun Route.personRoutes(personClient: SuPersonClient) {
    get(personPath) {
        call.parameters[identLabel]?.let { personIdent ->
            val principal = (call.authentication.principal as JWTPrincipal).payload
            sikkerLogg.info("${principal.subject} gjør oppslag på person $personIdent")
            when (val response = personClient.person(personIdent, call.request.header(Authorization)!!)) {
                is Ok -> call.respond(OK, response.json)
                is Feil -> call.respond(fromValue(response.httpCode), response.toJson())
            }
        } ?: call.respond(BadRequest, "query param '$identLabel' må oppgis")
    }
}
