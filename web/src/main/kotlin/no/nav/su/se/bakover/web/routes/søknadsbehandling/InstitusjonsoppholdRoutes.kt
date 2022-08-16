package no.nav.su.se.bakover.web.routes.søknadsbehandling

import arrow.core.getOrHandle
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.vilkår.LeggTilInstitusjonsoppholdVilkårRequest
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.vilkår.institusjonsopphold.LeggTilVurderingsperiodeInstitusjonsoppholdJson
import no.nav.su.se.bakover.web.routes.vilkår.institusjonsopphold.tilResultat
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBehandlingId
import no.nav.su.se.bakover.web.withBody
import java.time.Clock

internal fun Route.institusjonsoppholdRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
    satsFactory: SatsFactory,
    clock: Clock,
) {
    post("$behandlingPath/{behandlingId}/institusjonsopphold") {
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
                            ).fold(
                                {
                                    it.tilResultat()
                                },
                                {
                                    Resultat.json(
                                        HttpStatusCode.Created,
                                        it.json(satsFactory),
                                    )
                                },
                            )
                        }.getOrHandle { it },
                    )
                }
            }
        }
    }
}
