package no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag

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
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBehandlingId
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.vilkår.pensjon.LeggTilPensjonsVilkårRequest
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SØKNADSBEHANDLING_PATH
import no.nav.su.se.bakover.web.routes.søknadsbehandling.json
import no.nav.su.se.bakover.web.routes.vilkår.pensjon.LeggTilVurderingsperiodePensjonsvilkårJson
import no.nav.su.se.bakover.web.routes.vilkår.pensjon.tilResultat
import no.nav.su.se.bakover.web.routes.vilkår.pensjon.toDomain
import vilkår.formue.domain.FormuegrenserFactory
import java.time.Clock

internal fun Route.pensjonsVilkårRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
    formuegrenserFactory: FormuegrenserFactory,
    clock: Clock,
) {
    post("$SØKNADSBEHANDLING_PATH/{behandlingId}/pensjon") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBehandlingId {
                call.withBody<List<LeggTilVurderingsperiodePensjonsvilkårJson>> { body ->
                    call.svar(
                        søknadsbehandlingService.leggTilPensjonsVilkår(
                            request = LeggTilPensjonsVilkårRequest(
                                behandlingId = SøknadsbehandlingId(it),
                                vilkår = body.toDomain(clock).getOrElse { return@withBody call.svar(it.tilResultat()) },
                            ),
                            saksbehandler = call.suUserContext.saksbehandler,
                        ).fold(
                            { it.tilResultat() },
                            {
                                call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id.value)
                                Resultat.json(HttpStatusCode.Created, it.json(formuegrenserFactory))
                            },
                        ),
                    )
                }
            }
        }
    }
}
