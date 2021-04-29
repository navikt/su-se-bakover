package no.nav.su.se.bakover.web.routes

import io.ktor.routing.Route
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.web.routes.søknadsbehandling.leggTilGrunnlagSøknadsbehandlingRoutes
import no.nav.su.se.bakover.web.routes.søknadsbehandling.søknadsbehandlingRoutes

@KtorExperimentalAPI
internal fun Route.overordnetSøknadsbehandligRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
) {
    søknadsbehandlingRoutes(søknadsbehandlingService)

    leggTilGrunnlagSøknadsbehandlingRoutes(søknadsbehandlingService)
}
