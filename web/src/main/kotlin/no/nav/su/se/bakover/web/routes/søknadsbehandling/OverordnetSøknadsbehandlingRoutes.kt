package no.nav.su.se.bakover.web.routes.søknadsbehandling

import io.ktor.server.routing.Route
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import java.time.Clock

internal fun Route.overordnetSøknadsbehandligRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
    clock: Clock,
) {
    søknadsbehandlingRoutes(søknadsbehandlingService, clock)

    leggTilUføregrunnlagRoutes(søknadsbehandlingService)

    leggTilGrunnlagBosituasjonRoutes(søknadsbehandlingService)

    leggTilGrunnlagFradrag(søknadsbehandlingService, clock)

    leggTilUtenlandsopphold(søknadsbehandlingService)
}
