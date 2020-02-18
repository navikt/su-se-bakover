package no.nav.su.se.bakover.soknad

import com.google.gson.JsonObject
import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.request.receive
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.Resultat
import no.nav.su.se.bakover.audit
import no.nav.su.se.bakover.sak.SakService
import no.nav.su.se.bakover.svar

internal const val soknadPath = "/soknad"
internal const val identLabel = "ident"

@KtorExperimentalAPI
internal fun Route.soknadRoutes(sakService: SakService) {
    get(soknadPath) {
        call.parameters[identLabel]?.let { personIdent ->
            call.audit("Henter søknad for person: $personIdent")
            sakService.hentSoknadForPerson(personIdent)?.let {
                call.svar(Resultat.ok(it.toJson()))
            } ?: call.svar(Resultat.resultatMedMelding(NotFound, "Fant ikke søknad for person: $personIdent"))
        } ?: call.svar(Resultat.resultatMedMelding(BadRequest, "query param '$identLabel' må oppgis"))
    }

    get("$soknadPath/{soknadId}") {
        call.parameters["soknadId"]?.let { soknadId ->
            call.audit("Henter søknad med id: $soknadId")
            soknadId.toLongOrNull()?.let { søknadIdAsLong ->
                sakService.hentSøknad(søknadIdAsLong)?.let { søknad ->
                    call.svar(Resultat.ok(søknad.toJson()))
                } ?: call.svar(Resultat.resultatMedMelding(NotFound, "Fant ikke søknad med id: $soknadId"))
            } ?: call.svar(Resultat.resultatMedMelding(BadRequest, "Søknad Id må være et tall"))
        }
    }

    post(soknadPath) {
        call.receive<JsonObject>().let { json ->
            val fnr = json.getAsJsonObject("personopplysninger")?.get("fnr")?.asString
            call.audit("Lagrer søknad for person: $fnr")
            fnr?.let { sakService.lagreSøknad(fnr = it, søknad = json)?.let { søknadId ->
                call.svar(Resultat.created("""{"søknadId":$søknadId}"""))
            } ?: call.svar(Resultat.resultatMedMelding(InternalServerError, "Kunne ikke lagre søknad"))
            } ?: call.svar(Resultat.resultatMedMelding(BadRequest, melding = "fant ikke fnr i søknaden"))
        }
    }
}
