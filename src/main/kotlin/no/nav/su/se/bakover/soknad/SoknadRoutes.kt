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
import no.nav.su.se.bakover.svar
import no.nav.su.se.bakover.tekst

internal const val soknadPath = "/soknad"
internal const val identLabel = "ident"

@KtorExperimentalAPI
internal fun Route.soknadRoutes(sakFactory: SakFactory, søknadFactory: SøknadFactory) {

    get(soknadPath) {
        call.parameters[identLabel]?.let { personIdent ->
            call.audit("Henter søknad for person: $personIdent")
            sakFactory.forFnr(personIdent).gjeldendeSøknad().fold(
                onError = { call.svar(NotFound.tekst(it)) },
                onValue = { call.svar(OK.json(it.toJson())) }
            )
        } ?: call.svar(BadRequest.tekst("query param '$identLabel' må oppgis"))
    }

    get("$soknadPath/{soknadId}") {
        call.parameters["soknadId"]?.let { soknadId ->
            call.audit("Henter søknad med id: $soknadId")
            soknadId.toLongOrNull()?.let { søknadIdAsLong ->
                søknadFactory.forId(søknadIdAsLong).fold(
                    onError = { call.svar(NotFound.tekst(it)) },
                    onValue = { call.svar(OK.json(it.toJson()))}
                )
            } ?: call.svar(BadRequest.tekst("Søknad Id må være et tall"))
        }
    }

    post(soknadPath) {
        call.receive<JsonObject>().let { json ->
            val fnr = json.getAsJsonObject("personopplysninger")?.get("fnr")?.asString
            call.audit("Lagrer søknad for person: $fnr")
            fnr?.let {
                call.svar(Created.json(sakFactory.forFnr(it).nySøknad(json.toString()).toJson()))
            } ?: call.svar(BadRequest.tekst("fant ikke fnr i søknaden"))
        }
    }
}
