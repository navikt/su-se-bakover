package no.nav.su.se.bakover.web.routes.revurdering

import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.fantIkkeRevurdering
import no.nav.su.se.bakover.web.routes.revurdering.Revurderingsfeilresponser.tilResultat

internal fun Route.brevutkastForRevurdering(
    revurderingService: RevurderingService,
) {
    get("$REVURDERING_PATH/{revurderingId}/brevutkast") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withRevurderingId { revurderingId ->
                // TODO jah: La `lagBrevutkastForRevurdering` også returnere fnr slik at vi kan slette denne linja.
                val revurdering = revurderingService.hentRevurdering(revurderingId)
                    ?: return@authorize call.svar(fantIkkeRevurdering)

                revurderingService.lagBrevutkastForRevurdering(revurderingId).fold(
                    ifLeft = { call.svar(it.tilResultat()) },
                    ifRight = {
                        call.sikkerlogg("Laget brevutkast for revurdering med id $revurderingId")
                        call.audit(revurdering.fnr, AuditLogEvent.Action.ACCESS, revurderingId)
                        call.respondBytes(it.getContent(), ContentType.Application.Pdf)
                    },
                )
            }
        }
    }
}
