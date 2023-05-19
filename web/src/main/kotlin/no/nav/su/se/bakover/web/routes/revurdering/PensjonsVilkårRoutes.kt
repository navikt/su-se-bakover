package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.getOrElse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBehandlingId
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.vilkår.pensjon.LeggTilPensjonsVilkårRequest
import no.nav.su.se.bakover.web.routes.vilkår.pensjon.LeggTilVurderingsperiodePensjonsvilkårJson
import no.nav.su.se.bakover.web.routes.vilkår.pensjon.tilResultat
import no.nav.su.se.bakover.web.routes.vilkår.pensjon.toDomain
import java.time.Clock

internal fun Route.pensjonsVilkårRoutes(
    revurderingService: RevurderingService,
    satsFactory: SatsFactory,
    clock: Clock,
) {
    post("$revurderingPath/{revurderingId}/pensjon") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBehandlingId {
                call.withBody<List<LeggTilVurderingsperiodePensjonsvilkårJson>> { body ->
                    call.svar(
                        revurderingService.leggTilPensjonsVilkår(
                            request = LeggTilPensjonsVilkårRequest(
                                behandlingId = it,
                                vilkår = body.toDomain(clock).getOrElse { return@authorize call.svar(it.tilResultat()) },
                            ),
                        ).fold(
                            { it.tilResultat() },
                            {
                                call.audit(it.revurdering.fnr, AuditLogEvent.Action.UPDATE, it.revurdering.id)
                                Resultat.json(HttpStatusCode.Created, it.json(satsFactory))
                            },
                        ),
                    )
                }
            }
        }
    }
}
