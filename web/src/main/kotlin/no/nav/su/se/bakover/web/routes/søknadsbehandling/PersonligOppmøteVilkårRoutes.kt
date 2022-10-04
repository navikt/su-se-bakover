package no.nav.su.se.bakover.web.routes.søknadsbehandling

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.vilkår.LeggTilPersonligOppmøteVilkårRequest
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.vilkår.LeggTilVurderingsperiodePersonligOppmøteJson
import no.nav.su.se.bakover.web.routes.vilkår.tilResultat
import no.nav.su.se.bakover.web.routes.vilkår.toDomain
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBehandlingId
import no.nav.su.se.bakover.web.withBody

internal fun Route.personligOppmøteVilkårRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
    satsFactory: SatsFactory,
) {
    post("$behandlingPath/{behandlingId}/personligoppmøte") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBehandlingId {
                call.withBody<List<LeggTilVurderingsperiodePersonligOppmøteJson>> { body ->
                    call.svar(
                        søknadsbehandlingService.leggTilPersonligOppmøteVilkår(
                            request = LeggTilPersonligOppmøteVilkårRequest(
                                behandlingId = it,
                                vilkår = body.toDomain(),
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
                        ),
                    )
                }
            }
        }
    }
}
