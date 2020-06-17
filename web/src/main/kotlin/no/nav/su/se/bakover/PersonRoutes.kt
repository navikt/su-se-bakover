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
        sakRepo: ObjectRepo
) {
    get("$personPath/{fnr}") {
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
                    when (val sak = sakRepo.hentSak(fnr)) {
                        null -> call.svar(HttpStatusCode.NotFound.tekst("Fant ingen sak for fnr:$fnr"))
                        else -> call.svar(HttpStatusCode.OK.json(sak.toJson()))
                    }
                }
        )
    }
}
