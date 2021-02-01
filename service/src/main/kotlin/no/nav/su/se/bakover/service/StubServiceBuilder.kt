package no.nav.su.se.bakover.service

import no.finn.unleash.Unleash
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.database.DatabaseRepos
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.søknad.SøknadMetrics
import no.nav.su.se.bakover.service.toggles.ToggleService
import java.time.Clock

object StubServiceBuilder : ServiceBuilder {
    override fun build(
        databaseRepos: DatabaseRepos,
        clients: Clients,
        behandlingMetrics: BehandlingMetrics,
        søknadMetrics: SøknadMetrics,
        clock: Clock,
        unleash: Unleash
    ): Services {
        return ProdServiceBuilder.build(databaseRepos, clients, behandlingMetrics, søknadMetrics, clock, unleash)
            .copy(
                toggles = ToggleServiceStub
            )
    }
}

object ToggleServiceStub : ToggleService {
    override fun isEnabled(toggleName: String): Boolean {
        return true
    }
}
