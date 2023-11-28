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
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.domain.vilkår.flyktning.LeggTilFlyktningVilkårRequest
import no.nav.su.se.bakover.web.routes.vilkår.flyktning.LeggTilVurderingsperiodeFlyktningVilkårJson
import no.nav.su.se.bakover.web.routes.vilkår.flyktning.tilResultat
import no.nav.su.se.bakover.web.routes.vilkår.flyktning.toDomain
import vilkår.formue.domain.FormuegrenserFactory
import java.time.Clock

internal fun Route.flyktningVilkårRoutes(
    revurderingService: RevurderingService,
    formuegrenserFactory: FormuegrenserFactory,
    clock: Clock,
) {
    post("$REVURDERING_PATH/{revurderingId}/flyktning") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withRevurderingId {
                call.withBody<List<LeggTilVurderingsperiodeFlyktningVilkårJson>> { body ->
                    call.svar(
                        revurderingService.leggTilFlyktningVilkår(
                            request = LeggTilFlyktningVilkårRequest(
                                behandlingId = it,
                                vilkår = body.toDomain(clock).getOrElse { return@authorize call.svar(it.tilResultat()) },
                            ),
                        ).fold(
                            { it.tilResultat() },
                            {
                                call.audit(it.revurdering.fnr, AuditLogEvent.Action.UPDATE, it.revurdering.id)
                                Resultat.json(HttpStatusCode.Created, it.json(formuegrenserFactory))
                            },
                        ),
                    )
                }
            }
        }
    }
}
