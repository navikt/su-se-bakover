package no.nav.su.se.bakover.soknad

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
import no.nav.su.se.bakover.*
import no.nav.su.se.bakover.SakFactory
import no.nav.su.se.bakover.SøknadFactory
import org.json.JSONObject

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
                            right = { call.svar(OK.json(it.toJson())) }
                    )
                }
        )
    }

    post(søknadPath) {
        launchWithContext(call) {
            call.receiveTextUTF8().let { json ->
                SøknadInnhold.fromJson(JSONObject(json)).let { søknadInnhold ->
                    Fødselsnummer.fraString(søknadInnhold.personopplysninger.fnr).fold(
                            left = { call.svar(BadRequest.tekst(it)) },
                            right = {
                                call.audit("Lagrer søknad for person: $it")
                                call.svar(Created.json(sakFactory.forFnr(it).nySøknad(søknadInnhold).toJson()))
                            }
                    )
                }
            }
        }
    }
}

suspend inline fun ApplicationCall.receiveTextUTF8(): String = String(receiveStream().readBytes())
