package no.nav.su.se.bakover.service.søknadsbehandling

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.service.beregning.BeregningService
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.snapshot.OpprettVedtakssnapshotService
import java.time.Clock

internal fun createSøknadsbehandlingService(
    søknadsbehandlingRepo: SøknadsbehandlingRepo = mock(),
    utbetalingService: UtbetalingService = mock(),
    oppgaveService: OppgaveService = mock(),
    søknadService: SøknadService = mock(),
    søknadRepo: SøknadRepo = mock(),
    personService: PersonService = mock(),
    behandlingMetrics: BehandlingMetrics = mock(),
    iverksettAvslåttBehandlingService: IverksettAvslåttSøknadsbehandlingService = mock(),
    observer: EventObserver = mock { on { handle(any()) }.doNothing() },
    beregningService: BeregningService = mock(),
    microsoftGraphApiOppslag: MicrosoftGraphApiOppslag = mock(),
    brevService: BrevService = mock(),
    opprettVedtakssnapshotService: OpprettVedtakssnapshotService = mock(),
    clock: Clock = Clock.systemUTC(),
) = SøknadsbehandlingServiceImpl(
    søknadService,
    søknadRepo,
    søknadsbehandlingRepo,
    utbetalingService,
    personService,
    oppgaveService,
    iverksettAvslåttBehandlingService,
    behandlingMetrics,
    beregningService,
    microsoftGraphApiOppslag,
    brevService,
    opprettVedtakssnapshotService,
    clock,
).apply { addObserver(observer) }
