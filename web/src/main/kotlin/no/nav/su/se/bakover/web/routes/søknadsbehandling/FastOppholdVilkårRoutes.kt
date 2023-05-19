package no.nav.su.se.bakover.web.routes.søknadsbehandling

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
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.vilkår.fastopphold.LeggTilFastOppholdINorgeRequest
import no.nav.su.se.bakover.web.routes.vilkår.fastopphold.LeggTilVurderingsperiodeFastOppholdJson
import no.nav.su.se.bakover.web.routes.vilkår.fastopphold.tilResultat
import no.nav.su.se.bakover.web.routes.vilkår.fastopphold.toDomain
import java.time.Clock

internal fun Route.fastOppholdVilkårRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
    satsFactory: SatsFactory,
    clock: Clock,
) {
    post("$behandlingPath/{behandlingId}/fastopphold") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBehandlingId {
                call.withBody<List<LeggTilVurderingsperiodeFastOppholdJson>> { body ->
                    call.svar(
                        søknadsbehandlingService.leggTilFastOppholdINorgeVilkår(
                            request = LeggTilFastOppholdINorgeRequest(
                                behandlingId = it,
                                vilkår = body.toDomain(clock).getOrElse { return@authorize call.svar(it.tilResultat()) },
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
