package no.nav.su.se.bakover.test.application

import no.nav.su.se.bakover.domain.DatabaseRepos
import org.mockito.Mockito.mock

fun mockedDatabaseRepos() = DatabaseRepos(
    avstemming = mock(),
    utbetaling = mock(),
    søknad = mock(),
    sak = mock(),
    person = mock(),
    søknadsbehandling = mock(),
    revurderingRepo = mock(),
    vedtakRepo = mock(),
    personhendelseRepo = mock(),
    dokumentRepo = mock(),
    nøkkeltallRepo = mock(),
    sessionFactory = mock(),
    klageRepo = mock(),
    klageinstanshendelseRepo = mock(),
    reguleringRepo = mock(),
    tilbakekrevingRepo = mock(),
    sendPåminnelseNyStønadsperiodeJobRepo = mock(),
    hendelseRepo = mock(),
    utenlandsoppholdRepo = mock(),
    dokumentSkattRepo = mock(),
    institusjonsoppholdHendelseRepo = mock(),
    oppgaveHendelseRepo = mock(),
    hendelsekonsumenterRepo = mock(),
    dokumentHendelseRepo = mock(),
)
