package no.nav.su.se.bakover.web

import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.client.ClientsBuilder
import no.nav.su.se.bakover.client.JournalførClients
import no.nav.su.se.bakover.client.journalfør.skatt.påsak.JournalførSkattedokumentPåSakFakeClient
import no.nav.su.se.bakover.client.journalfør.skatt.utenforsak.JournalførSkattedokumentUtenforSakFakeClient
import no.nav.su.se.bakover.client.journalpost.QueryJournalpostClientStub
import no.nav.su.se.bakover.client.kabal.KlageClientStub
import no.nav.su.se.bakover.client.stubs.azure.AzureClientStub
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
import no.nav.su.se.bakover.common.infrastructure.auth.TokenOppslagStub
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.dokument.infrastructure.database.journalføring.JournalpostIdGeneratorForFakes
import no.nav.su.se.bakover.dokument.infrastructure.database.journalføring.brev.JournalførBrevFakeClient
import no.nav.su.se.bakover.dokument.infrastructure.database.journalføring.søknad.JournalførSøknadFakeClient
import no.nav.su.se.bakover.domain.DatabaseRepos
import org.mockito.kotlin.mock
import vilkår.skatt.infrastructure.client.SkatteClientStub
import java.time.Clock
import java.time.LocalDate

data class TestClientsBuilder(
    val clock: Clock,
    val databaseRepos: DatabaseRepos,
) : ClientsBuilder {
    private val journalpostIdGenerator = JournalpostIdGeneratorForFakes()
    private val testClients = Clients(
        oauth = AzureClientStub,
        personOppslag = PersonOppslagStub,
        tokenOppslag = TokenOppslagStub,
        pdfGenerator = PdfGeneratorStub,
        journalførClients = JournalførClients(
            skattedokumentUtenforSak = JournalførSkattedokumentUtenforSakFakeClient(journalpostIdGenerator),
            skattedokumentPåSak = JournalførSkattedokumentPåSakFakeClient(journalpostIdGenerator),
            brev = JournalførBrevFakeClient(journalpostIdGenerator),
            søknad = JournalførSøknadFakeClient(journalpostIdGenerator),
        ),
        oppgaveClient = OppgaveClientStub,
        kodeverk = mock(),
        simuleringClient = SimuleringStub(
            clock = clock,
            utbetalingerKjørtTilOgMed = { LocalDate.now(clock) },
            utbetalingRepo = databaseRepos.utbetaling,
        ),
        utbetalingPublisher = UtbetalingStub,
        dokDistFordeling = DokDistFordelingStub,
        avstemmingPublisher = AvstemmingStub,
        identClient = IdentClientStub,
        kontaktOgReservasjonsregister = KontaktOgReservasjonsregisterStub,
        leaderPodLookup = LeaderPodLookupStub,
        kafkaPublisher = KafkaPublisherStub,
        klageClient = KlageClientStub,
        queryJournalpostClient = QueryJournalpostClientStub,
        tilbakekrevingClient = TilbakekrevingClientStub(clock),
        skatteOppslag = SkatteClientStub(clock),
    )

    override fun build(applicationConfig: ApplicationConfig): Clients = testClients
}
