package no.nav.su.se.bakover.sak

import com.google.gson.Gson
import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.Resultat
import no.nav.su.se.bakover.audit
import no.nav.su.se.bakover.svar

internal const val sakPath = "/sak"
internal const val identLabel = "ident"

@KtorExperimentalAPI
internal fun Route.sakRoutes(sakRepository: SakRepository) {
    get(sakPath) {
        call.parameters[identLabel]?.let { fnr ->
            call.audit("Henter sak for person: $fnr")
            sakRepository.hentSak(fnr)?.let {
                call.svar(Resultat.ok(it.toJson()))
            } ?: call.svar(Resultat.resultatMedMelding(NotFound, "Fant ikke sak for person: $fnr"))
        } ?: call.svar(Resultat.resultatMedMelding(BadRequest, "query param '$identLabel' må oppgis"))
    }

    get("$sakPath/{id}") {
        call.parameters["id"]?.let { id ->
            call.audit("Henter sak med id: $id")
            id.toLongOrNull()?.let { idAsLong ->
                sakRepository.hentSak(idAsLong)?.let { sak ->
                    call.svar(Resultat.ok(sak.toJson()))
                } ?: call.svar(Resultat.resultatMedMelding(NotFound, "Fant ikke sak med id: $id"))
            } ?: call.svar(Resultat.resultatMedMelding(BadRequest, "Sak id må være et tall"))
        }
    }

    get("$sakPath/list") {
        call.svar(Resultat.ok(Gson().toJson(sakRepository.hentAlleSaker())))
    }

    post(sakPath) {
        call.parameters[identLabel]?.let { fnr ->
            call.audit("Oppretter sak for person: $fnr")
            sakRepository.opprettSak(fnr)?.let {
                call.svar(Resultat.created("""{"id":$it}"""))
            } ?: call.svar(Resultat.resultatMedMelding(InternalServerError, "kunne ikke opprette sak"))
        } ?: call.svar(Resultat.resultatMedMelding(BadRequest, "query param '$identLabel' må oppgis"))
    }
}
