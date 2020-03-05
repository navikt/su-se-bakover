package no.nav.su.se.bakover.soknad

import com.google.gson.JsonObject
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

internal const val søknadPath = "/soknad"

@KtorExperimentalAPI
internal fun Route.soknadRoutes(sakFactory: SakFactory, søknadFactory: SøknadFactory) {

    get(søknadPath) {
        Fødselsnummer.lesParameter(call).fold(
            left = { call.svar(BadRequest.tekst(it)) },
            right = {
                sakFactory.forFnr(it).gjeldendeSøknad().fold(
                    left = { call.svar(NotFound.tekst(it)) },
                    right = { call.svar(OK.json(it.toJson())) }
                )
            }
        )
    }

    get("$søknadPath/{soknadId}") {
        Long.lesParameter(call, "soknadId").fold(
            left = { call.svar(BadRequest.tekst(it)) },
            right = { id ->
                call.audit("Henter søknad med id: $id")
                søknadFactory.forId(id).fold(
                    left = { call.svar(NotFound.tekst(it)) },
                    right = { call.svar(OK.json(it.toJson()))}
                )
            }
        )
    }

    post(søknadPath) {
        call.receive<JsonObject>().let { json ->
            Fødselsnummer.fraString(json.getAsJsonObject("personopplysninger")?.get("fnr")?.asString).fold(
                left = { call.svar(BadRequest.tekst(it)) },
                right = {
                    call.audit("Lagrer søknad for person: $it")
                    call.svar(Created.json(sakFactory.forFnr(it).nySøknad(json.toString()).toJson()))
                }
            )
        }
    }
}
