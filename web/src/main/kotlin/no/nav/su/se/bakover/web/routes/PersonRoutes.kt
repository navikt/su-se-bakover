package no.nav.su.se.bakover.web.routes

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.client.PersonOppslag
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.web.*

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
                call.svar(Resultat.from(oppslag.person(it)))
            }
        }
    }

    get("$personPath/{fnr}/sak") {
        Fnr.lesParameter(call).let {
            call.audit("Henter sak for person: $it")
            when (val sak = sakRepo.hentSak(it)) {
                null -> call.svar(HttpStatusCode.NotFound.message("Fant ingen sak for fnr:$it"))
                else -> call.svar(HttpStatusCode.OK.json(sak.toJson()))
            }
        }
    }
}
