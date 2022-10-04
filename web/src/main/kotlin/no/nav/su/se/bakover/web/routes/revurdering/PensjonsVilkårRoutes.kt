package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.getOrHandle
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.vilkår.LeggTilPensjonsVilkårRequest
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.features.authorize
import no.nav.su.se.bakover.web.routes.vilkår.alder.LeggTilVurderingsperiodePensjonsvilkårJson
import no.nav.su.se.bakover.web.routes.vilkår.alder.tilResultat
import no.nav.su.se.bakover.web.routes.vilkår.alder.toDomain
import no.nav.su.se.bakover.web.svar
import no.nav.su.se.bakover.web.withBehandlingId
import no.nav.su.se.bakover.web.withBody

internal fun Route.pensjonsVilkårRoutes(
    revurderingService: RevurderingService,
    satsFactory: SatsFactory,
) {
    post("$revurderingPath/{revurderingId}/pensjon") {
        authorize(Brukerrolle.Saksbehandler) {
            call.withBehandlingId {
                call.withBody<List<LeggTilVurderingsperiodePensjonsvilkårJson>> { body ->
                    call.svar(
                        revurderingService.leggTilPensjonsVilkår(
                            request = LeggTilPensjonsVilkårRequest(
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
