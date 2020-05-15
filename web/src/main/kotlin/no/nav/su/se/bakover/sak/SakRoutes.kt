package no.nav.su.se.bakover.sak

import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.*
import no.nav.su.se.bakover.audit
import no.nav.su.se.bakover.SakFactory
import no.nav.su.se.bakover.json
import no.nav.su.se.bakover.svar

internal const val sakPath = "/sak"

@KtorExperimentalAPI
internal fun Route.sakRoutes(
    sakFactory: SakFactory
) {
    get(sakPath) {
        Fødselsnummer.lesParameter(call).fold(
            left = { call.svar(OK.json("""[${sakFactory.alle().joinToString(",") { it.toJson()}}]""")) },
            right = {
                call.audit("Henter sak for person: $it")
                call.svar(OK.json(sakFactory.forFnr(it).toJson()))
            }
        )
    }

    get("$sakPath/{id}") {
        Long.lesParameter(call, "id").fold(
            left = { call.svar(BadRequest.tekst(it)) },
            right = { id ->
                call.audit("Henter sak med id: $id")
                sakFactory.forId(id).fold(
                    left = { call.svar(NotFound.tekst("Fant ikke sak med id: $id")) },
                    right = { call.svar(OK.json(it.toJson())) })
            }
        )
    }

    // FIXME: Denne burde ha søknad i flertall, siden den returnerer alle søknadene registert på en sak.
    get("$sakPath/{id}/soknad") {
        Long.lesParameter(call, "id").fold(
            left = { call.svar(BadRequest.tekst(it)) },
            right = { id ->
                call.audit("Henter søknad for sakId: $id")
                sakFactory.forId(id).fold(
                    right = { call.svar(OK.json(it.stønadsperioderSomJsonListe())) },
                    left = { call.svar(NotFound.tekst("Fant ikke sak med id: $id")) }
                )
            }
        )
    }

    get("$sakPath/list") {
        call.svar(OK.json("""[${sakFactory.alle().joinToString(",") { it.toJson()}}]"""))
    }
}
