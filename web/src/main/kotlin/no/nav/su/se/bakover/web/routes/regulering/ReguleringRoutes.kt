package no.nav.su.se.bakover.web.routes.regulering

import io.ktor.server.routing.Route
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.domain.regulering.ReguleringService
import vilkår.formue.domain.FormuegrenserFactory
import java.time.Clock

internal const val REGULERING_PATH = "/reguleringer"

internal fun Route.reguleringRoutes(
    reguleringService: ReguleringService,
    clock: Clock,
    runtimeEnvironment: ApplicationConfig.RuntimeEnvironment,
    formuegrenserFactory: FormuegrenserFactory,
) {
    reguler(reguleringService, clock, runtimeEnvironment, formuegrenserFactory)
    reguleringOversiktRoutes(reguleringService)
}
