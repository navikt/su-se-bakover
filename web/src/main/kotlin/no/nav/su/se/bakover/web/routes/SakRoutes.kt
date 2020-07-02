package no.nav.su.se.bakover.web.routes

import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.domain.SakDto
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.jsonObject
import no.nav.su.se.bakover.web.lesParameter
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.svar

internal const val sakPath = "/sak"

@KtorExperimentalAPI
internal fun Route.sakRoutes(
    sakRepo: ObjectRepo
) {
    get("$sakPath/{id}") {
        Long.lesParameter(call, "id").fold(
                left = { call.svar(BadRequest.message(it)) },
                right = { id ->
                    call.audit("Henter sak med id: $id")
                    when (val sak = sakRepo.hentSak(id)) {
                        null -> call.svar(NotFound.message("Fant ikke sak med id: $id"))
                        else -> call.svar(OK.jsonObject(sak.toDto().toJson()))
                    }
                }
        )
    }
}

fun SakDto.toJson() = SakJson(
    id = id,
    fnr = fnr.toString(),
    stønadsperioder = stønadsperioder.map { it.toJson() }
)

data class SakJson(
    val id: Long,
    val fnr: String,
    val stønadsperioder: List<StønadsperiodeJson>
)
