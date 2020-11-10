package no.nav.su.se.bakover.service.behandling

import com.nhaarman.mockitokotlin2.mock
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService

object BehandlingTestUtils {
    internal fun createService(
        behandlingRepo: BehandlingRepo = mock(),
        hendelsesloggRepo: HendelsesloggRepo = mock(),
        utbetalingService: UtbetalingService = mock(),
        oppgaveService: OppgaveService = mock(),
        søknadService: SøknadService = mock(),
        søknadRepo: SøknadRepo = mock(),
        personOppslag: PersonOppslag = mock(),
        brevService: BrevService = mock(),
        behandlingMetrics: BehandlingMetrics = mock()
    ) = BehandlingServiceImpl(
        behandlingRepo = behandlingRepo,
        hendelsesloggRepo = hendelsesloggRepo,
        utbetalingService = utbetalingService,
        oppgaveService = oppgaveService,
        søknadService = søknadService,
        søknadRepo = søknadRepo,
        personOppslag = personOppslag,
        brevService = brevService,
        behandlingMetrics = behandlingMetrics
    )
}
