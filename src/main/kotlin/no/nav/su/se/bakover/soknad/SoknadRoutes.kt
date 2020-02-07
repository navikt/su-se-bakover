package no.nav.su.se.bakover.soknad

import com.google.gson.JsonObject
import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
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
import no.nav.su.se.bakover.Feil
import no.nav.su.se.bakover.Suksess
import no.nav.su.se.bakover.db.PostgresRepository
import no.nav.su.se.bakover.svar
import org.slf4j.LoggerFactory

internal const val soknadPath = "/soknad"
internal const val identLabel = "ident"

private val sikkerLogg = LoggerFactory.getLogger("sikkerLogg")

@KtorExperimentalAPI
internal fun Route.soknadRoutes(postgresRepository: PostgresRepository) {
    get(soknadPath) {
        call.parameters["ident"]?.let { personIdent ->
            val principal = (call.authentication.principal as JWTPrincipal).payload
            sikkerLogg.info("${principal.subject} henter søknad for person: $personIdent")
            postgresRepository.hentSoknadForPerson(personIdent)?.let {
                call.svar(Suksess(OK, it.søknadJson))
            } ?: call.svar(Feil(NotFound, "Fant ikke søknad for person: $personIdent"))
        } ?: call.svar(Feil(BadRequest, "query param '$identLabel' må oppgis"))
    }

    get("$soknadPath/{soknadId}") {
        call.parameters["soknadId"]?.let { soknadId ->
            val principal = (call.authentication.principal as JWTPrincipal).payload
            sikkerLogg.info("${principal.subject} henter søknad med id: $soknadId")
            soknadId.toLongOrNull()?.let { søknadIdAsLong ->
                postgresRepository.hentSøknad(søknadIdAsLong)?.let { søknad ->
                    call.svar(Suksess(OK, søknad.søknadJson))
                } ?: call.svar(Feil(NotFound, "Fant ikke søknad med id: $soknadId"))
            } ?: call.svar(Feil(BadRequest, "Søknad Id må være et tall"))
        }
    }

    post(soknadPath) {
        call.receive<JsonObject>().let { json ->
            val principal = (call.authentication.principal as JWTPrincipal).payload
            postgresRepository.lagreSøknad(json.toString())?.let { søknadId ->
                call.svar(Suksess(Created, """{"søknadId":$søknadId}"""))
            } ?: call.svar(Feil(InternalServerError, "Kunne ikke lagre søknad"))
        }
    }
}
