package no.nav.su.se.bakover.test.application

import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.client.JournalførClients
import no.nav.su.se.bakover.client.stubs.azure.AzureClientStub
import org.mockito.Mockito.mock

/**
 * oath og tokenOppslag bruker stubs pga. ktor auth setup
 */
fun mockedClients() = Clients(
    azureAd = AzureClientStub,
    personOppslag = mock(),
    pdfGenerator = mock(),
    journalførClients = JournalførClients(
        skattedokumentUtenforSak = mock(),
        skattedokumentPåSak = mock(),
        brev = mock(),
        søknad = mock(),
    ),
    oppgaveClient = mock(),
    kodeverk = mock(),
    simuleringClient = mock(),
    utbetalingPublisher = mock(),
    dokDistFordeling = mock(),
    avstemmingPublisher = mock(),
    identClient = mock(),
    kontaktOgReservasjonsregister = mock(),
    leaderPodLookup = mock(),
    kafkaPublisher = mock(),
    klageClient = mock(),
    queryJournalpostClient = mock(),
    skatteOppslag = mock(),
    pesysklient = mock(),
    suProxyClient = mock(),
)
