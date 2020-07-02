package no.nav.su.se.bakover.web.routes.stønadsperiode

import io.ktor.application.call
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.domain.Vilkår
import no.nav.su.se.bakover.domain.Vilkårsvurdering
import no.nav.su.se.bakover.web.audit
import no.nav.su.se.bakover.web.lesParameter
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.behandling.jsonBody
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.svar

internal const val stønadsperiodePath = "$sakPath/{sakId}/stønadsperioder"

@KtorExperimentalAPI
internal fun Route.stønadsperiodeRoutes(
    repo: ObjectRepo
) {

    post("$stønadsperiodePath/{stønadsperiodeId}/behandlinger") {
        Long.lesParameter(call, "stønadsperiodeId").fold(
            left = { call.svar(BadRequest.message(it)) },
            right = { id ->
                call.audit("oppretter behandling på stønadsperiode med id: $id")
                when (val stønadsperiode = repo.hentStønadsperiode(id)) {
                    null -> call.svar(NotFound.message("Fant ikke stønadsperiode med id:$id"))
                    else -> call.svar(Created.jsonBody(stønadsperiode.nyBehandling()))
                }
            }
        )
    }

    get("$stønadsperiodePath/{stønadsperiodeId}") {
        Long.lesParameter(call, "stønadsperiodeId").fold(
            left = { call.svar(BadRequest.message(it)) },
            right = { id ->
                call.audit("Henter stønadsperiode med med id: $id")
                when (val stønadsperiode = repo.hentStønadsperiode(id)) {
                    null -> call.svar(NotFound.message("Fant ikke stønadsperiode med id:$id"))
                    else -> call.svar(OK.jsonBody(stønadsperiode))
                }
            }
        )
    }
}

fun Map<String, VilkårsvurderingData>.toVilkårsvurderinger() = this.map {
    Vilkårsvurdering(
        id = it.value.id,
        vilkår = Vilkår.valueOf(it.key),
        begrunnelse = it.value.begrunnelse,
        status = Vilkårsvurdering.Status.valueOf(it.value.status)
    )
}

data class VilkårsvurderingData(
    val id: Long,
    val begrunnelse: String,
    val status: String
)
