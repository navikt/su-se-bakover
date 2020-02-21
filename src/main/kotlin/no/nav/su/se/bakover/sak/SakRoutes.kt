package no.nav.su.se.bakover.sak

import com.google.gson.Gson
import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.*
import no.nav.su.se.bakover.audit
import no.nav.su.se.bakover.json
import no.nav.su.se.bakover.svar

internal const val sakPath = "/sak"
internal const val identLabel = "ident"

@KtorExperimentalAPI
internal fun Route.sakRoutes(
    sakService: SakService
) {
    get(sakPath) {
        call.parameters[identLabel]?.let { fnr ->
            call.audit("Henter sak for person: $fnr")
            sakService.hentSak(fnr)?.let {
                call.svar(OK.json(it.toJson()))
            } ?: call.svar(NotFound.tekst("Fant ikke sak for person: $fnr"))
        } ?: call.svar(BadRequest.tekst("query param '$identLabel' må oppgis"))
    }

    get("$sakPath/{id}") {
        call.parameters["id"]?.let { id ->
            call.audit("Henter sak med id: $id")
            id.toLongOrNull()?.let { idAsLong ->
                sakService.hentSak(idAsLong)?.let { sak ->
                    call.svar(OK.json(sak.toJson()))
                } ?: call.svar(NotFound.tekst("Fant ikke sak med id: $id"))
            } ?: call.svar(BadRequest.tekst("Sak id må være et tall"))
        }
    }

    get("$sakPath/{id}/soknad") {
        call.parameters["id"]?.let { sakId ->
            call.audit("Henter søknad for sakId: $sakId")
            sakId.toLongOrNull()?.let { sakIdAsLong ->
                sakService.hentSøknaderForSak(sakIdAsLong).let { søknader ->
                    call.svar(OK.json("""${søknader.map { it.toJson() }}"""))
                }
            } ?: call.svar(BadRequest.tekst("sakId må være et tall"))
        }
    }

    get("$sakPath/list") {
        call.svar(OK.json(Gson().toJson(sakService.hentAlleSaker())))
    }

    post(sakPath) {
        call.parameters[identLabel]?.let { fnr ->
            call.audit("Oppretter sak for person: $fnr")
            sakService.opprettSak(fnr).let {
                call.svar(Created.json("""{"id":$it}"""))
            }
        } ?: call.svar(BadRequest.tekst("query param '$identLabel' må oppgis"))
    }
}
