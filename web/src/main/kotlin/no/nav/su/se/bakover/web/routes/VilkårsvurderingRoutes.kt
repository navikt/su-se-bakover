package no.nav.su.se.bakover.web.routes

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.patch
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.json
import no.nav.su.se.bakover.web.launchWithContext
import no.nav.su.se.bakover.web.lesParameter
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.objectMapper
import no.nav.su.se.bakover.web.readMap
import no.nav.su.se.bakover.web.svar

internal const val vilkårsvurderingPath = "$behandlingPath/{behandlingId}/vilkarsvurderinger"

@KtorExperimentalAPI
internal fun Route.vilkårsvurderingRoutes(repo: ObjectRepo) {

    patch(vilkårsvurderingPath) {
        launchWithContext(call) {
            Long.lesParameter(call, "behandlingId").fold(
                ifLeft = { call.svar(HttpStatusCode.BadRequest.message(it)) },
                ifRight = { id ->
                    call.audit("Oppdaterer vilkårsvurdering for behandling med id: $id")
                    when (val behandling = repo.hentBehandling(id)) {
                        null -> call.svar(HttpStatusCode.NotFound.message("Fant ikke behandling med id:$id"))
                        else -> {
                            val vilkårsvurderinger = objectMapper.readMap<String, VilkårsvurderingData>(call.receiveTextUTF8())
                            behandling.oppdaterVilkårsvurderinger(vilkårsvurderinger.toVilkårsvurderinger())
                            call.svar(HttpStatusCode.OK.json(objectMapper.writeValueAsString(behandling.toDto().toJson())))
                        }
                    }
                }
            )
        }
    }
}
