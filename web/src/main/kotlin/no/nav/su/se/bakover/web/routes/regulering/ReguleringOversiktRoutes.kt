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
import no.nav.su.se.bakover.domain.satser.SatsFactory

internal fun Route.reguleringOversiktRoutes(
    reguleringService: ReguleringService,
    satsFactory: SatsFactory,
) {
    get("$reguleringPath/status") {
        authorize(Brukerrolle.Saksbehandler) {
            val json = reguleringService.hentStatus().map { it.toJson(satsFactory) }
            call.svar(Resultat.json(HttpStatusCode.OK, serialize(json)))
        }
    }

    get("$reguleringPath/saker/apneBehandlinger") {
        val json = reguleringService.hentSakerMedÅpenBehandlingEllerStans()
        call.svar(Resultat.json(HttpStatusCode.OK, serialize(json)))
    }
}
