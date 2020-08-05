package no.nav.su.se.bakover.web.routes.vilkårsvurdering

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.patch

import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.lesUUID
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.behandling.behandlingPath
import no.nav.su.se.bakover.web.routes.behandling.jsonBody
import no.nav.su.se.bakover.web.svar

internal const val vilkårsvurderingPath = "$behandlingPath/{behandlingId}/vilkarsvurderinger"

internal fun Route.vilkårsvurderingRoutes(repo: ObjectRepo) {

    patch(vilkårsvurderingPath) {
        call.lesUUID("behandlingId").fold(
            ifLeft = { call.svar(HttpStatusCode.BadRequest.message(it)) },
            ifRight = { id ->
                call.audit("Oppdaterer vilkårsvurdering for behandling med id: $id")
                when (val behandling = repo.hentBehandling(id)) {
                    null -> call.svar(HttpStatusCode.NotFound.message("Fant ikke behandling med id:$id"))
                    else -> {
                        val vilkårsvurderingJson =
                            deserialize<VilkårsvurderingJson>(call)
                        behandling.oppdaterVilkårsvurderinger(vilkårsvurderingJson.vilkårsvurderinger.toVilkårsvurderinger())
                        call.svar(HttpStatusCode.OK.jsonBody(behandling))
                    }
                }
            }
        )
    }
}
