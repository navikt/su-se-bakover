package no.nav.su.se.bakover

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
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
            Fnr.lesParameter(call).let {
                call.audit("Gjør oppslag på person: $it")
                call.svar(oppslag.person(it))
            }
        }
    }

    get("$personPath/{fnr}/sak") {
        Fnr.lesParameter(call).let {
            call.audit("Henter sak for person: $it")
            when (val sak = sakRepo.hentSak(it)) {
                null -> call.svar(HttpStatusCode.NotFound.tekst("Fant ingen sak for fnr:$it"))
                else -> call.svar(HttpStatusCode.OK.json(sak.toJson()))
            }
        }
    }
}
