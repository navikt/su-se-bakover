package no.nav.su.se.bakover.soknad

import com.google.gson.JsonObject
import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.request.receive
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.*
import no.nav.su.se.bakover.audit
import no.nav.su.se.bakover.sak.SakService
import no.nav.su.se.bakover.svar
import no.nav.su.se.bakover.tekst

internal const val soknadPath = "/soknad"
internal const val identLabel = "ident"

@KtorExperimentalAPI
internal fun Route.soknadRoutes(sakService: SakService) {
    get(soknadPath) {
        call.parameters[identLabel]?.let { personIdent ->
            call.audit("Henter søknad for person: $personIdent")
            sakService.hentSoknadForPerson(personIdent)?.let {
                call.svar(OK.json(it.toJson()))
            } ?: call.svar(NotFound.tekst("Fant ikke søknad for person: $personIdent"))
        } ?: call.svar(BadRequest.tekst("query param '$identLabel' må oppgis"))
    }

    get("$soknadPath/{soknadId}") {
        call.parameters["soknadId"]?.let { soknadId ->
            call.audit("Henter søknad med id: $soknadId")
            soknadId.toLongOrNull()?.let { søknadIdAsLong ->
                sakService.hentSøknad(søknadIdAsLong)?.let { søknad ->
                    call.svar(OK.json(søknad.toJson()))
                } ?: call.svar(NotFound.tekst("Fant ikke søknad med id: $soknadId"))
            } ?: call.svar(BadRequest.tekst("Søknad Id må være et tall"))
        }
    }

    post(soknadPath) {
        call.receive<JsonObject>().let { json ->
            val fnr = json.getAsJsonObject("personopplysninger")?.get("fnr")?.asString
            call.audit("Lagrer søknad for person: $fnr")
            fnr?.let { sakService.lagreSøknad(fnr = it, søknad = json)?.let { søknadId ->
                call.svar(Created.json("""{"søknadId":$søknadId}"""))
            } ?: call.svar(InternalServerError.tekst("Kunne ikke lagre søknad"))
            } ?: call.svar(BadRequest.tekst("fant ikke fnr i søknaden"))
        }
    }
}
