package no.nav.su.se.bakover.soknad

import com.google.gson.JsonObject
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.request.receive
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.*
import no.nav.su.se.bakover.audit
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.SøknadFactory
import no.nav.su.se.bakover.json
import no.nav.su.se.bakover.svar
import no.nav.su.se.bakover.tekst

internal const val soknadPath = "/soknad"

@KtorExperimentalAPI
internal fun Route.soknadRoutes(sakFactory: SakFactory, søknadFactory: SøknadFactory) {

    get(soknadPath) {
        Fødselsnummer.extract(call).fold(
            onError = { call.svar(BadRequest.tekst(it)) },
            onValue = {
                sakFactory.forFnr(it).gjeldendeSøknad().fold(
                    onError = { call.svar(NotFound.tekst(it)) },
                    onValue = { call.svar(OK.json(it.toJson())) }
                )
            }
        )
    }

    get("$soknadPath/{soknadId}") {
        call.extractLong("soknadId").fold(
            onError = { call.svar(BadRequest.tekst(it)) },
            onValue = { id ->
                call.audit("Henter søknad med id: $id")
                søknadFactory.forId(id).fold(
                    onError = { call.svar(NotFound.tekst(it)) },
                    onValue = { call.svar(OK.json(it.toJson()))}
                )
            }
        )
    }

    post(soknadPath) {
        call.receive<JsonObject>().let { json ->
            Fødselsnummer.fraString(json.getAsJsonObject("personopplysninger")?.get("fnr")?.asString).fold(
                onError = { call.svar(BadRequest.tekst(it)) },
                onValue = {
                    call.audit("Lagrer søknad for person: $it")
                    call.svar(Created.json(sakFactory.forFnr(it).nySøknad(json.toString()).toJson()))
                }
            )
        }
    }
}
