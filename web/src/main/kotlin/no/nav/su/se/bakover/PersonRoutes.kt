package no.nav.su.se.bakover

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.util.KtorExperimentalAPI

internal const val personPath = "/person"

@KtorExperimentalAPI
internal fun Route.personRoutes(
        oppslag: PersonOppslag,
        sakFactory: SakFactory
) {
    get(personPath) {
        launchWithContext(call) {
            Fødselsnummer.lesParameter(call).fold(
                    left = { call.respond(BadRequest, it) },
                    right = {
                        call.audit("Gjør oppslag på person: $it")
                        call.svar(oppslag.person(it))
                    }
            )
        }
    }

    get("$personPath/{fnr}/sak") {
        Fødselsnummer.lesParameter(call).fold(
                left = { call.svar(BadRequest.tekst(it)) },
                right = { fnr ->
                    call.audit("Henter sak for person: $fnr")
                    sakFactory.hent(fnr).fold(
                            left = { call.svar(HttpStatusCode.NotFound.tekst(it)) },
                            right = { call.svar(HttpStatusCode.OK.json(it.toJson())) }
                    )
                }
        )
    }
}
