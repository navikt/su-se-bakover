package no.nav.su.se.bakover.web.routes.regulering

import io.ktor.routing.Route
import no.nav.su.se.bakover.service.regulering.ReguleringService

internal const val reguleringPath = "/reguleringer"

internal fun Route.reguleringRoutes(
    reguleringService: ReguleringService,
) {
    hentListeAvVedtakSomKanReguleres(reguleringService)
    oppdaterReguleringer(reguleringService)
    reguleringOversiktRoutes(reguleringService)
}
