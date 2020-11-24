package no.nav.su.se.bakover.service.behandling

import com.nhaarman.mockitokotlin2.mock
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.person.PersonOppslag
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import java.time.Clock
import java.time.ZoneOffset

object BehandlingTestUtils {

    internal val tidspunkt = 15.juni(2020).atStartOfDay().toTidspunkt(ZoneOffset.UTC)

    private val fixedClock = Clock.fixed(15.juni(2020).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

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
        behandlingMetrics = behandlingMetrics,
        clock = fixedClock
    )
}
