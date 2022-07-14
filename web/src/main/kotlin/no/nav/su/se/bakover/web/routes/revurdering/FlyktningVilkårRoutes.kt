package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.getOrHandle
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.vilkår.LeggTilFlyktningVilkårRequest
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.vilkår.flyktning.LeggTilVurderingsperiodeFlyktningVilkårJson
import no.nav.su.se.bakover.web.routes.vilkår.flyktning.tilResultat
import no.nav.su.se.bakover.web.routes.vilkår.flyktning.toDomain
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBody
import no.nav.su.se.bakover.web.withRevurderingId

internal fun Route.flyktningVilkårRoutes(
    revurderingService: RevurderingService,
    satsFactory: SatsFactory,
) {
    post("$revurderingPath/{revurderingId}/flyktning") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withRevurderingId {
                call.withBody<List<LeggTilVurderingsperiodeFlyktningVilkårJson>> { body ->
                    call.svar(
                        revurderingService.leggTilFlyktningVilkår(
                            request = LeggTilFlyktningVilkårRequest(
                                behandlingId = it,
                                vilkår = body.toDomain().getOrHandle { return@withBody call.svar(it.tilResultat()) },
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
