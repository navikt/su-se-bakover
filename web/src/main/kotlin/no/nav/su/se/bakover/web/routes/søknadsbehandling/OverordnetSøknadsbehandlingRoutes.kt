package no.nav.su.se.bakover.web.routes.søknadsbehandling

import io.ktor.routing.Route
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import java.time.Clock

internal fun Route.overordnetSøknadsbehandligRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
    clock: Clock,
) {
    søknadsbehandlingRoutes(søknadsbehandlingService)

    leggTilUføregrunnlagRoutes(søknadsbehandlingService)

    leggTilGrunnlagBosituasjonRoutes(søknadsbehandlingService)

    leggTilGrunnlagFradrag(søknadsbehandlingService, clock)

    leggTilOppholdIUtlandet(søknadsbehandlingService)
}
