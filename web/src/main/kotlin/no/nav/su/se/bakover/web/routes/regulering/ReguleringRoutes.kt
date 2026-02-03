package no.nav.su.se.bakover.web.routes.regulering

import io.ktor.server.routing.Route
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.domain.regulering.ReguleringAutomatiskService
import no.nav.su.se.bakover.domain.regulering.ReguleringManuellService
import java.time.Clock

internal const val REGULERING_PATH = "/reguleringer"

internal fun Route.reguleringRoutes(
    reguleringManuellService: ReguleringManuellService,
    reguleringAutomatiskService: ReguleringAutomatiskService,
    clock: Clock,
    runtimeEnvironment: ApplicationConfig.RuntimeEnvironment,
) {
    reguler(reguleringManuellService, reguleringAutomatiskService, clock, runtimeEnvironment)
    reguleringOversiktRoutes(reguleringManuellService)
}
