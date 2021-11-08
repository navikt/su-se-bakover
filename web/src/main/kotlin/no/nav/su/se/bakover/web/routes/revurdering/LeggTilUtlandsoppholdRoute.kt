package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.application.call
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.vilkår.LeggTilOppholdIUtlandetRequest
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId
import java.util.UUID

private data class UtlandsoppholdBody(
    val status: String,
    val begrunnelse: String?,
) {
    fun toRequest(revurderingId: UUID): LeggTilOppholdIUtlandetRequest {
        return LeggTilOppholdIUtlandetRequest(
            behandlingId = revurderingId,
            status = Behandlingsinformasjon.OppholdIUtlandet.Status.valueOf(status),
            begrunnelse = begrunnelse,
        )
    }
}

internal fun Route.LeggTilUtlandsoppholdRoute(
    revurderingService: RevurderingService,
) {
    authorize(Brukerrolle.Saksbehandler) {
        post("$revurderingPath/{revurderingId}/utlandsopphold") {
            call.withRevurderingId { revurderingId ->
                call.withBody<UtlandsoppholdBody> { body ->
                    val req = body.toRequest(revurderingId)
                    revurderingService.leggTilUtlandsopphold(req)
                }
            }
        }
    }
}
