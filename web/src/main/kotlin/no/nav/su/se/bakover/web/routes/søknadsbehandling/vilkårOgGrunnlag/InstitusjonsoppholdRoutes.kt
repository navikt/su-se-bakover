package no.nav.su.se.bakover.web.routes.søknadsbehandling.vilkårOgGrunnlag

import arrow.core.merge
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
import no.nav.su.se.bakover.domain.vilkår.institusjonsopphold.LeggTilInstitusjonsoppholdVilkårRequest
import no.nav.su.se.bakover.web.routes.søknadsbehandling.json
import no.nav.su.se.bakover.web.routes.søknadsbehandling.søknadsbehandlingPath
import no.nav.su.se.bakover.web.routes.vilkår.institusjonsopphold.LeggTilVurderingsperiodeInstitusjonsoppholdJson
import no.nav.su.se.bakover.web.routes.vilkår.institusjonsopphold.tilResultat
import java.time.Clock

internal fun Route.institusjonsoppholdRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
    satsFactory: SatsFactory,
    clock: Clock,
) {
    post("$søknadsbehandlingPath/{behandlingId}/institusjonsopphold") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBehandlingId { behandlingId ->
                call.withBody<LeggTilVurderingsperiodeInstitusjonsoppholdJson> { body ->
                    call.svar(
                        body.toDomain(clock).map { vilkår ->
                            søknadsbehandlingService.leggTilInstitusjonsoppholdVilkår(
                                request = LeggTilInstitusjonsoppholdVilkårRequest(
                                    behandlingId = behandlingId,
                                    vilkår = vilkår,
                                ),
                                saksbehandler = call.suUserContext.saksbehandler,
                            ).fold(
                                { it.tilResultat() },
                                {
                                    call.audit(it.fnr, AuditLogEvent.Action.UPDATE, it.id)
                                    Resultat.json(HttpStatusCode.Created, it.json(satsFactory))
                                },
                            )
                        }.merge(),
                    )
                }
            }
        }
    }
}
