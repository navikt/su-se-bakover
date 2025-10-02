package no.nav.su.se.bakover.test.application

import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingServices
import no.nav.su.se.bakover.web.services.Services
import org.mockito.Mockito.mock

fun mockedServices() = Services(
    avstemming = mock(),
    utbetaling = mock(),
    sak = mock(),
    søknad = mock(),
    brev = mock(),
    lukkSøknad = mock(),
    oppgave = mock(),
    person = mock(),
    søknadsbehandling = SøknadsbehandlingServices(
        iverksettSøknadsbehandlingService = mock(),
        søknadsbehandlingService = mock(),
    ),
    ferdigstillVedtak = mock(),
    revurdering = mock(),
    stansYtelse = mock(),
    gjenopptaYtelse = mock(),
    vedtakService = mock(),
    nøkkeltallService = mock(),
    avslåSøknadManglendeDokumentasjonService = mock(),
    klageService = mock(),
    klageinstanshendelseService = mock(),
    reguleringService = mock(),
    sendPåminnelserOmNyStønadsperiodeService = mock(),
    skatteService = mock(),
    kontrollsamtaleSetup = mock(),
    resendStatistikkhendelserService = mock(),
    personhendelseService = mock(),
    stønadStatistikkJobService = mock(),
    statistikkEventObserver = mock(),
)
