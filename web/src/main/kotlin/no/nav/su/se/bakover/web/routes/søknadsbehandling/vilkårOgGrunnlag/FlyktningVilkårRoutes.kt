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
import no.nav.su.se.bakover.domain.vilkår.flyktning.LeggTilFlyktningVilkårRequest
import no.nav.su.se.bakover.web.inputvalidation.withValidertBody
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SØKNADSBEHANDLING_PATH
import no.nav.su.se.bakover.web.routes.søknadsbehandling.json
import no.nav.su.se.bakover.web.routes.vilkår.flyktning.LeggTilVurderingsperiodeFlyktningVilkårJson
import no.nav.su.se.bakover.web.routes.vilkår.flyktning.tilResultat
import no.nav.su.se.bakover.web.routes.vilkår.flyktning.toDomain
import org.slf4j.LoggerFactory
import vilkår.formue.domain.FormuegrenserFactory
import java.time.Clock

internal fun Route.flyktningVilkårRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
    formuegrenserFactory: FormuegrenserFactory,
    clock: Clock,
) {
    val log = LoggerFactory.getLogger(this::class.java)

    post("$SØKNADSBEHANDLING_PATH/{behandlingId}/flyktning") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBehandlingId { behandlingId ->
                call.withValidertBody<List<LeggTilVurderingsperiodeFlyktningVilkårJson>>(log) { body ->
                    either {
                        val vilkår = body.toDomain(clock).bind()
                        val søknadsbehandling = søknadsbehandlingService.leggTilFlyktningVilkår(
                            request = LeggTilFlyktningVilkårRequest(
                                behandlingId = SøknadsbehandlingId(behandlingId),
                                vilkår = vilkår,
                            ),
                            saksbehandler = call.suUserContext.saksbehandler,
                        ).mapLeft { kunneIkkeLeggeTilFlyktningVilkår -> kunneIkkeLeggeTilFlyktningVilkår.tilResultat() }.bind()

                        call.audit(søknadsbehandling.fnr, AuditLogEvent.Action.UPDATE, søknadsbehandling.id.value)
                        Resultat.json(HttpStatusCode.Created, søknadsbehandling.json(formuegrenserFactory))
                    }.merge().let { resultat -> call.svar(resultat) }
                }
            }
        }
    }
}
