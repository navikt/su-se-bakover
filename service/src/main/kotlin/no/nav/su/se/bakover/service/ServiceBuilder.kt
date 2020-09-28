package no.nav.su.se.bakover.service

import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.database.DatabaseRepos
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import no.nav.su.se.bakover.service.avstemming.AvstemmingServiceImpl

class ServiceBuilder(
    private val databaseRepos: DatabaseRepos,
    private val clients: Clients
) {
    fun build() = Services(
        avstemmingService = AvstemmingServiceImpl(
            repo = databaseRepos.avstemmingRepo,
            publisher = clients.avstemmingPublisher
        )
    )
}

data class Services(
    val avstemmingService: AvstemmingService
)
