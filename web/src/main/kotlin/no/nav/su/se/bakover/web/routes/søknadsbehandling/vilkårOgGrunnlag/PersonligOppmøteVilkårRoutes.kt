package no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag

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
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBehandlingId
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.vilkår.oppmøte.LeggTilPersonligOppmøteVilkårRequest
import no.nav.su.se.bakover.web.inputvalidation.withValidertBody
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SØKNADSBEHANDLING_PATH
import no.nav.su.se.bakover.web.routes.søknadsbehandling.json
import no.nav.su.se.bakover.web.routes.vilkår.LeggTilVurderingsperiodePersonligOppmøteJson
import no.nav.su.se.bakover.web.routes.vilkår.tilResultat
import no.nav.su.se.bakover.web.routes.vilkår.toDomain
import org.slf4j.LoggerFactory
import vilkår.formue.domain.FormuegrenserFactory
import java.time.Clock

internal fun Route.personligOppmøteVilkårRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
    formuegrenserFactory: FormuegrenserFactory,
    clock: Clock,
) {
    val log = LoggerFactory.getLogger(this::class.java)

    post("$SØKNADSBEHANDLING_PATH/{behandlingId}/personligoppmøte") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBehandlingId { behandlingId ->
                call.withValidertBody<List<LeggTilVurderingsperiodePersonligOppmøteJson>>(log) { body ->
                    either {
                        val vilkår = body.toDomain(clock).bind()
                        val søknadsbehandling = søknadsbehandlingService.leggTilPersonligOppmøteVilkår(
                            request = LeggTilPersonligOppmøteVilkårRequest(
                                behandlingId = SøknadsbehandlingId(behandlingId),
                                vilkår = vilkår,
                            ),
                            saksbehandler = call.suUserContext.saksbehandler,
                        ).mapLeft { kunneIkkeLeggeTilPersonligOppmøteVilkår -> kunneIkkeLeggeTilPersonligOppmøteVilkår.tilResultat() }.bind()

                        call.audit(søknadsbehandling.fnr, AuditLogEvent.Action.UPDATE, søknadsbehandling.id.value)
                        Resultat.json(HttpStatusCode.Created, søknadsbehandling.json(formuegrenserFactory))
                    }.merge().let { resultat -> call.svar(resultat) }
                }
            }
        }
    }
}
