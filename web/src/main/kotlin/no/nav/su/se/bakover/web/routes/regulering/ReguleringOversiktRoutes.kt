package no.nav.su.se.bakover.web.routes.regulering

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.regulering.ReguleringService

internal fun Route.reguleringOversiktRoutes(
    reguleringService: ReguleringService,
) {
    get("$REGULERING_PATH/status") {
        authorize(Brukerrolle.Saksbehandler) {
            call.svar(Resultat.json(HttpStatusCode.OK, reguleringService.hentStatusForÅpneManuelleReguleringer().toJson()))
        }
    }

    get("$REGULERING_PATH/saker/apneBehandlinger") {
        val json = reguleringService.hentSakerMedÅpenBehandlingEllerStans()
        call.svar(Resultat.json(HttpStatusCode.OK, serialize(json)))
    }
}
