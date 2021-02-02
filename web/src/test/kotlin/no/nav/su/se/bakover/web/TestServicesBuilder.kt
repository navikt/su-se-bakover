package no.nav.su.se.bakover.web

import com.nhaarman.mockitokotlin2.mock
import no.nav.su.se.bakover.service.Services

object TestServicesBuilder {
    fun services(): Services = Services(
        avstemming = mock(),
        utbetaling = mock(),
        behandling = mock(),
        sak = mock(),
        søknad = mock(),
        brev = mock(),
        lukkSøknad = mock(),
        oppgave = mock(),
        person = mock(),
        statistikk = mock(),
        toggles = mock(),
        søknadsbehandling = mock(),
    )
}
