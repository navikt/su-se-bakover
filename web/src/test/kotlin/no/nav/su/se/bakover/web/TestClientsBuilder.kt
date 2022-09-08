package no.nav.su.se.bakover.web

import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.client.ClientsBuilder
import no.nav.su.se.bakover.client.journalpost.JournalpostClientStub
import no.nav.su.se.bakover.client.kabal.KlageClientStub
import no.nav.su.se.bakover.client.maskinporten.MaskinportenClientStub
import no.nav.su.se.bakover.client.skatteetaten.SkatteClientStub
import no.nav.su.se.bakover.client.stubs.azure.AzureClientStub
import no.nav.su.se.bakover.client.stubs.dokarkiv.DokArkivStub
import no.nav.su.se.bakover.client.stubs.dokdistfordeling.DokDistFordelingStub
import no.nav.su.se.bakover.client.stubs.kafka.KafkaPublisherStub
import no.nav.su.se.bakover.client.stubs.krr.KontaktOgReservasjonsregisterStub
import no.nav.su.se.bakover.client.stubs.nais.LeaderPodLookupStub
import no.nav.su.se.bakover.client.stubs.oppdrag.AvstemmingStub
import no.nav.su.se.bakover.client.stubs.oppdrag.SimuleringStub
import no.nav.su.se.bakover.client.stubs.oppdrag.TilbakekrevingClientStub
import no.nav.su.se.bakover.client.stubs.oppdrag.UtbetalingStub
import no.nav.su.se.bakover.client.stubs.oppgave.OppgaveClientStub
import no.nav.su.se.bakover.client.stubs.pdf.PdfGeneratorStub
import no.nav.su.se.bakover.client.stubs.person.IdentClientStub
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.client.stubs.sts.TokenOppslagStub
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.domain.DatabaseRepos
import org.mockito.kotlin.mock
import java.time.Clock

data class TestClientsBuilder(
    val clock: Clock,
    val databaseRepos: DatabaseRepos,
) : ClientsBuilder {

    val testClients = Clients(
        oauth = AzureClientStub,
        personOppslag = PersonOppslagStub,
        tokenOppslag = TokenOppslagStub,
        pdfGenerator = PdfGeneratorStub,
        dokArkiv = DokArkivStub,
        oppgaveClient = OppgaveClientStub,
        kodeverk = mock(),
        simuleringClient = SimuleringStub(clock, databaseRepos.utbetaling),
        utbetalingPublisher = UtbetalingStub,
        dokDistFordeling = DokDistFordelingStub,
        avstemmingPublisher = AvstemmingStub,
        identClient = IdentClientStub,
        kontaktOgReservasjonsregister = KontaktOgReservasjonsregisterStub,
        leaderPodLookup = LeaderPodLookupStub,
        kafkaPublisher = KafkaPublisherStub,
        klageClient = KlageClientStub,
        journalpostClient = JournalpostClientStub,
        tilbakekrevingClient = TilbakekrevingClientStub(clock),
        skatteOppslag = SkatteClientStub(),
        maskinportenClient = MaskinportenClientStub(clock)
    )

    override fun build(applicationConfig: ApplicationConfig): Clients = testClients
}
