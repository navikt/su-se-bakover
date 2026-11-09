package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.merge
import arrow.core.raise.either
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.audit.AuditLogEvent
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withRevurderingId
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.domain.vilkår.flyktning.LeggTilFlyktningVilkårRequest
import no.nav.su.se.bakover.web.inputvalidation.withValidertBody
import no.nav.su.se.bakover.web.routes.vilkår.flyktning.LeggTilVurderingsperiodeFlyktningVilkårJson
import no.nav.su.se.bakover.web.routes.vilkår.flyktning.tilResultat
import no.nav.su.se.bakover.web.routes.vilkår.flyktning.toDomain
import org.slf4j.LoggerFactory
import vilkår.formue.domain.FormuegrenserFactory
import java.time.Clock

internal fun Route.flyktningVilkårRoutes(
    revurderingService: RevurderingService,
    formuegrenserFactory: FormuegrenserFactory,
    clock: Clock,
) {
    val log = LoggerFactory.getLogger(this::class.java)

    post("$REVURDERING_PATH/{revurderingId}/flyktning") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withRevurderingId { revurderingId ->
                call.withValidertBody<List<LeggTilVurderingsperiodeFlyktningVilkårJson>>(log) { body ->
                    either {
                        val vilkår = body.toDomain(clock).bind()
                        val oppdatert = revurderingService.leggTilFlyktningVilkår(
                            request = LeggTilFlyktningVilkårRequest(
                                behandlingId = RevurderingId(revurderingId),
                                vilkår = vilkår,
                            ),
                        ).mapLeft { kunneIkkeLeggeTilFlyktningVilkår -> kunneIkkeLeggeTilFlyktningVilkår.tilResultat() }.bind()

                        call.audit(oppdatert.revurdering.fnr, AuditLogEvent.Action.UPDATE, oppdatert.revurdering.id.value)
                        Resultat.json(HttpStatusCode.Created, oppdatert.json(formuegrenserFactory))
                    }.merge().let { resultat -> call.svar(resultat) }
                }
            }
        }
    }
}
