package no.nav.su.se.bakover.service

import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.database.DatabaseRepos
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import no.nav.su.se.bakover.service.avstemming.AvstemmingServiceImpl
import no.nav.su.se.bakover.service.oppdrag.OppdragService
import no.nav.su.se.bakover.service.oppdrag.OppdragServiceImpl
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingServiceImpl

class ServiceBuilder(
    private val databaseRepos: DatabaseRepos,
    private val clients: Clients
) {
    fun build() = Services(
        avstemmingService = AvstemmingServiceImpl(
            repo = databaseRepos.avstemmingRepo,
            publisher = clients.avstemmingPublisher
        ),
        utbetalingService = UtbetalingServiceImpl(
            repo = databaseRepos.utbetalingRepo,
        ),
        oppdragService = OppdragServiceImpl(
            repo = databaseRepos.oppdragRepo
        )
    )
}

data class Services(
    val avstemmingService: AvstemmingService,
    val utbetalingService: UtbetalingService,
    val oppdragService: OppdragService
)
