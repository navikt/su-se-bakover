package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.getOrHandle
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.audit.application.AuditLogEvent
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.audit
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBehandlingId
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.vilkår.flyktning.LeggTilFlyktningVilkårRequest
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.vilkår.flyktning.LeggTilVurderingsperiodeFlyktningVilkårJson
import no.nav.su.se.bakover.web.routes.vilkår.flyktning.tilResultat
import no.nav.su.se.bakover.web.routes.vilkår.flyktning.toDomain

internal fun Route.flyktningVilkårRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
    satsFactory: SatsFactory,
) {
    post("$behandlingPath/{behandlingId}/flyktning") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBehandlingId {
                call.withBody<List<LeggTilVurderingsperiodeFlyktningVilkårJson>> { body ->
                    call.svar(
                        søknadsbehandlingService.leggTilFlyktningVilkår(
                            request = LeggTilFlyktningVilkårRequest(
                                behandlingId = it,
                                vilkår = body.toDomain().getOrHandle { return@withBody call.svar(it.tilResultat()) },
                            ),
                            saksbehandler = call.suUserContext.saksbehandler,
                        ).fold(
                            { it.tilResultat() },
                            {
                                call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                                Resultat.json(HttpStatusCode.Created, it.json(satsFactory))
                            },
                        ),
                    )
                }
            }
        }
    }
}
