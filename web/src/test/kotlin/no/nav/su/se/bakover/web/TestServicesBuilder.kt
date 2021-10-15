package no.nav.su.se.bakover.web

import no.nav.su.se.bakover.service.Services
import org.mockito.kotlin.mock

object TestServicesBuilder {
    fun services(): Services = Services(
        avstemming = mock(),
        utbetaling = mock(),
        sak = mock(),
        søknad = mock(),
        brev = mock(),
        lukkSøknad = mock(),
        oppgave = mock(),
        person = mock(),
        statistikk = mock(),
        toggles = mock(),
        søknadsbehandling = mock(),
        ferdigstillVedtak = mock(),
        revurdering = mock(),
        vedtakService = mock(),
        grunnlagService = mock(),
        avslåSøknadManglendeDokumentasjon = mock(),
    )
}
