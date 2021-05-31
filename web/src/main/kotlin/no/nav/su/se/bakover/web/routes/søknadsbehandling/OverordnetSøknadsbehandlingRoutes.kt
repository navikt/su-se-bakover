package no.nav.su.se.bakover.web.routes.søknadsbehandling

import io.ktor.routing.Route
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService

internal fun Route.overordnetSøknadsbehandligRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
) {
    søknadsbehandlingRoutes(søknadsbehandlingService)

    leggTilGrunnlagSøknadsbehandlingRoutes(søknadsbehandlingService)

    leggTilGrunnlagBosituasjonRoutes(søknadsbehandlingService)
}
