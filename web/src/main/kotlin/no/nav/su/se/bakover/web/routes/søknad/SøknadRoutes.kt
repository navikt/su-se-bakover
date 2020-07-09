package no.nav.su.se.bakover.web.routes.søknad

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.request.receiveStream
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.meldinger.kafka.soknad.SøknadInnhold
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.lesUUID
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.sak.jsonBody
import no.nav.su.se.bakover.web.svar
import org.json.JSONObject

internal const val søknadPath = "/soknad"

@KtorExperimentalAPI
internal fun Route.søknadRoutes(
    mediator: SøknadRouteMediator
) {

    get("$søknadPath/{soknadId}") {
        call.lesUUID("soknadId").fold(
            ifLeft = { call.svar(BadRequest.message(it)) },
            ifRight = { id ->
                call.audit("Henter søknad med id: $id")
                when (val søknad = mediator.hentSøknad(id)) {
                    null -> call.svar(NotFound.message("Fant ikke søknad med id:$id"))
                    else -> call.svar(OK.jsonBody(søknad))
                }
            }
        )
    }

    post(søknadPath) {
        call.receiveTextUTF8().let { json ->
            SøknadInnhold.fromJson(JSONObject(json)).let { søknadInnhold ->
                Fnr(søknadInnhold.personopplysninger.fnr).let {
                    call.audit("Lagrer søknad for person: $it")
                    call.svar(Created.jsonBody(mediator.nySøknad(søknadInnhold)))
                }
            }
        }
    }
}

suspend inline fun ApplicationCall.receiveTextUTF8(): String = String(receiveStream().readBytes())
