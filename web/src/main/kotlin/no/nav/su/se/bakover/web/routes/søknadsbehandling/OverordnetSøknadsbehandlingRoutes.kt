package no.nav.su.se.bakover.web.routes.søknadsbehandling

import io.ktor.routing.Route
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService

@KtorExperimentalAPI
internal fun Route.overordnetSøknadsbehandligRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
) {
    søknadsbehandlingRoutes(søknadsbehandlingService)

    leggTilGrunnlagSøknadsbehandlingRoutes(søknadsbehandlingService)

    leggTilGrunnlagBosituasjonRoutes(søknadsbehandlingService)
}
