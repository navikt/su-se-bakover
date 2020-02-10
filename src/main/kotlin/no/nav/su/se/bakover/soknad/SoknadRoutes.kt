package no.nav.su.se.bakover.soknad

import com.google.gson.JsonObject
import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.request.receive
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.Resultat
import no.nav.su.se.bakover.db.PostgresRepository
import no.nav.su.se.bakover.svar
import org.slf4j.LoggerFactory

internal const val soknadPath = "/soknad"
internal const val identLabel = "ident"

private val sikkerLogg = LoggerFactory.getLogger("sikkerLogg")

@KtorExperimentalAPI
internal fun Route.soknadRoutes(postgresRepository: PostgresRepository) {
    get(soknadPath) {
        call.parameters[identLabel]?.let { personIdent ->
            val principal = (call.authentication.principal as JWTPrincipal).payload
            sikkerLogg.info("${principal.subject} henter søknad for person: $personIdent")
            postgresRepository.hentSoknadForPerson(personIdent)?.let {
                call.svar(Resultat.ok(it.søknadJson))
            } ?: call.svar(Resultat.feilMedMelding(NotFound, "Fant ikke søknad for person: $personIdent"))
        } ?: call.svar(Resultat.feilMedMelding(BadRequest, "query param '$identLabel' må oppgis"))
    }

    get("$soknadPath/{soknadId}") {
        call.parameters["soknadId"]?.let { soknadId ->
            val principal = (call.authentication.principal as JWTPrincipal).payload
            sikkerLogg.info("${principal.subject} henter søknad med id: $soknadId")
            soknadId.toLongOrNull()?.let { søknadIdAsLong ->
                postgresRepository.hentSøknad(søknadIdAsLong)?.let { søknad ->
                    call.svar(Resultat.ok(søknad.søknadJson))
                } ?: call.svar(Resultat.feilMedMelding(NotFound, "Fant ikke søknad med id: $soknadId"))
            } ?: call.svar(Resultat.feilMedMelding(BadRequest, "Søknad Id må være et tall"))
        }
    }

    post(soknadPath) {
        call.receive<JsonObject>().let { json ->
            val principal = (call.authentication.principal as JWTPrincipal).payload
            postgresRepository.lagreSøknad(json.toString())?.let { søknadId ->
                call.svar(Resultat.created("""{"søknadId":$søknadId}"""))
            } ?: call.svar(Resultat.feilMedMelding(InternalServerError, "Kunne ikke lagre søknad"))
        }
    }
}
