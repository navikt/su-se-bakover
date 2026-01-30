package no.nav.su.se.bakover.web.routes.drift

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleDriftOversiktService

internal fun Route.kontrollsamtalerDriftRoute(
    service: KontrollsamtaleDriftOversiktService,
) {
    get("$DRIFT_PATH/kontrollsamtaler") {
        authorize(Brukerrolle.Drift) {
            val result = service.hentKontrollsamtaleOversikt()
            call.svar(Resultat.json(HttpStatusCode.OK, serialize(result)))
        }
    }
}
