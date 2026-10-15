package no.nav.su.se.bakover.web.routes.drift

import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.svar
import vilkår.flyktnings.domain.UlfClient

internal fun Route.ulfKlientRoute(
    klient: UlfClient,
) {
    get("$DRIFT_PATH/ulf-klient/endringer") {
        authorize(Brukerrolle.Drift) {
            klient.hentEndringer()
            call.svar(Resultat.accepted())
        }
    }
}
