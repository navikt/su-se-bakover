package no.nav.su.se.bakover.web.routes

import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.web.*
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.json
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.svar

internal const val behandlingPath = "$stønadsperiodePath/{stønadsperiodeId}/behandlinger"

@KtorExperimentalAPI
internal fun Route.behandlingRoutes(
    repo: ObjectRepo
) {

    get("$behandlingPath/{behandlingId}") {
        Long.lesParameter(call, "behandlingId").fold(
                left = { call.svar(BadRequest.message(it)) },
                right = { id ->
                    call.audit("Henter behandling med id: $id")
                    when (val behandling = repo.hentBehandling(id)) {
                        null -> call.svar(NotFound.message("Fant ikke behandling med id:$id"))
                        else -> call.svar(OK.json(behandling.toJson()))
                    }
                }
        )
    }
}
