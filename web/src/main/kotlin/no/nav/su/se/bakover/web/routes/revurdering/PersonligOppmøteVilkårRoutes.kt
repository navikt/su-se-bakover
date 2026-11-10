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
import no.nav.su.se.bakover.domain.vilkår.oppmøte.LeggTilPersonligOppmøteVilkårRequest
import no.nav.su.se.bakover.web.inputvalidation.withValidertBody
import no.nav.su.se.bakover.web.routes.vilkår.LeggTilVurderingsperiodePersonligOppmøteJson
import no.nav.su.se.bakover.web.routes.vilkår.tilResultat
import no.nav.su.se.bakover.web.routes.vilkår.toDomain
import org.slf4j.LoggerFactory
import vilkår.formue.domain.FormuegrenserFactory
import java.time.Clock

internal fun Route.personligOppmøteVilkårRoutes(
    revurderingService: RevurderingService,
    formuegrenserFactory: FormuegrenserFactory,
    clock: Clock,
) {
    val log = LoggerFactory.getLogger(this::class.java)

    post("$REVURDERING_PATH/{revurderingId}/personligoppmøte") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withRevurderingId { revurderingId ->
                call.withValidertBody<List<LeggTilVurderingsperiodePersonligOppmøteJson>>(log) { body ->
                    either {
                        val vilkår = body.toDomain(clock).bind()
                        val oppdatert = revurderingService.leggTilPersonligOppmøteVilkår(
                            request = LeggTilPersonligOppmøteVilkårRequest(
                                behandlingId = RevurderingId(revurderingId),
                                vilkår = vilkår,
                            ),
                        ).mapLeft { kunneIkkeLeggeTilPersonligOppmøteVilkår -> kunneIkkeLeggeTilPersonligOppmøteVilkår.tilResultat() }.bind()

                        call.audit(oppdatert.revurdering.fnr, AuditLogEvent.Action.UPDATE, oppdatert.revurdering.id.value)
                        Resultat.json(HttpStatusCode.Created, oppdatert.json(formuegrenserFactory))
                    }.merge().let { resultat -> call.svar(resultat) }
                }
            }
        }
    }
}
