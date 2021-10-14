package no.nav.su.se.bakover.web.routes.søknadsbehandling

import io.ktor.routing.Route
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.vedtak.VedtakService

internal fun Route.overordnetSøknadsbehandligRoutes(
    søknadsbehandlingService: SøknadsbehandlingService,
    vedtakService: VedtakService,
) {
    søknadsbehandlingRoutes(søknadsbehandlingService, vedtakService)

    leggTilGrunnlagSøknadsbehandlingRoutes(søknadsbehandlingService)

    leggTilGrunnlagBosituasjonRoutes(søknadsbehandlingService)

    leggTilGrunnlagFradrag(søknadsbehandlingService)
}
