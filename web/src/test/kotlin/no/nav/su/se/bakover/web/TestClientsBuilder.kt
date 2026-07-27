package no.nav.su.se.bakover.web

import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.client.ClientsBuilder
import no.nav.su.se.bakover.client.JournalførClients
import no.nav.su.se.bakover.client.aap.AapApiInternClient
import no.nav.su.se.bakover.client.aap.AapApiInternClientStub
import no.nav.su.se.bakover.client.antivirus.ClamAVClient
import no.nav.su.se.bakover.client.journalfør.notat.JournalførVedtaksnotatFakeClient
import no.nav.su.se.bakover.client.journalfør.skatt.påsak.JournalførSkattedokumentPåSakFakeClient
import no.nav.su.se.bakover.client.journalfør.skatt.utenforsak.JournalførSkattedokumentUtenforSakFakeClient
import no.nav.su.se.bakover.client.journalpost.QueryJournalpostClientStub
import no.nav.su.se.bakover.client.kabal.KlageClientStub
import no.nav.su.se.bakover.client.kodeverk.Kodeverk
import no.nav.su.se.bakover.client.pesys.PesysClient
import no.nav.su.se.bakover.client.pesys.PesysclientStub
import no.nav.su.se.bakover.client.proxy.SuProxyClientStub
import no.nav.su.se.bakover.client.regoppslag.RegoppslagKlient
import no.nav.su.se.bakover.client.stubs.azure.AzureClientStub
import no.nav.su.se.bakover.client.stubs.dokdistfordeling.DokDistFordelingStub
import no.nav.su.se.bakover.client.stubs.kafka.KafkaPublisherStub
import no.nav.su.se.bakover.client.stubs.krr.KontaktOgReservasjonsregisterStub
import no.nav.su.se.bakover.client.stubs.nais.LeaderPodLookupStub
import no.nav.su.se.bakover.client.stubs.oppdrag.AvstemmingStub
import no.nav.su.se.bakover.client.stubs.oppdrag.SimuleringStub
import no.nav.su.se.bakover.client.stubs.oppdrag.UtbetalingStub
import no.nav.su.se.bakover.client.stubs.oppgave.OppgaveClientStub
import no.nav.su.se.bakover.client.stubs.oppgave.OppgaveV2ClientStub
import no.nav.su.se.bakover.client.stubs.pdf.PdfGeneratorStub
import no.nav.su.se.bakover.client.stubs.person.IdentClientStub
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.JournalpostIdGeneratorForFakes
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.brev.JournalførBrevFakeClient
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.søknad.JournalførSøknadFakeClient
import no.nav.su.se.bakover.domain.DatabaseRepos
import org.mockito.kotlin.mock
import person.domain.PersonOppslag
import vilkår.skatt.infrastructure.client.SkatteClientStub
import java.time.Clock
import java.time.LocalDate

data class TestClientsBuilder(
    val clock: Clock,
    val databaseRepos: DatabaseRepos,
    val azureAd: AzureAd = AzureClientStub,
    val personOppslag: PersonOppslag = PersonOppslagStub(),
    val pdfGenerator: PdfGeneratorStub = PdfGeneratorStub,
    val journalførClients: JournalførClients = JournalførClients(
        skattedokumentUtenforSak = JournalførSkattedokumentUtenforSakFakeClient(JournalpostIdGeneratorForFakes()),
        skattedokumentPåSak = JournalførSkattedokumentPåSakFakeClient(JournalpostIdGeneratorForFakes()),
        brev = JournalførBrevFakeClient(JournalpostIdGeneratorForFakes()),
        søknad = JournalførSøknadFakeClient(JournalpostIdGeneratorForFakes()),
        vedtaksnotat = JournalførVedtaksnotatFakeClient(JournalpostIdGeneratorForFakes()),
    ),
    val oppgaveClient: OppgaveClientStub = OppgaveClientStub,
    val oppgaveV2Client: OppgaveV2ClientStub = OppgaveV2ClientStub,
    val kodeverk: Kodeverk = mock(),
    val simuleringClient: SimuleringStub = SimuleringStub(
        clock = clock,
        utbetalingerKjørtTilOgMed = { LocalDate.now(clock) },
        utbetalingRepo = databaseRepos.utbetaling,
    ),
    val utbetalingPublisher: UtbetalingStub = UtbetalingStub,
    val dokDistFordeling: DokDistFordelingStub = DokDistFordelingStub,
    val avstemmingPublisher: AvstemmingStub = AvstemmingStub,
    val identClient: IdentClientStub = IdentClientStub,
    val kontaktOgReservasjonsregister: KontaktOgReservasjonsregisterStub = KontaktOgReservasjonsregisterStub,
    val leaderPodLookup: LeaderPodLookupStub = LeaderPodLookupStub,
    val kafkaPublisher: KafkaPublisherStub = KafkaPublisherStub,
    val klageClient: KlageClientStub = KlageClientStub,
    val queryJournalpostClient: QueryJournalpostClientStub = QueryJournalpostClientStub,
    val skatteOppslag: SkatteClientStub = SkatteClientStub(clock),
    val pesysClient: PesysClient = PesysclientStub(),
    val aapApiInternClient: AapApiInternClient = AapApiInternClientStub(),
    val suProxyClient: SuProxyClientStub = SuProxyClientStub(),
    val regoppslagKlient: RegoppslagKlient = mock(),
    val clamavClient: ClamAVClient = mock(),
) : ClientsBuilder {
    private val testClients = Clients(
        azureAd = azureAd,
        personOppslag = personOppslag,
        pdfGenerator = pdfGenerator,
        journalførClients = journalførClients,
        oppgaveClient = oppgaveClient,
        oppgaveV2Client = oppgaveV2Client,
        kodeverk = kodeverk,
        simuleringClient = simuleringClient,
        utbetalingPublisher = utbetalingPublisher,
        dokDistFordeling = dokDistFordeling,
        avstemmingPublisher = avstemmingPublisher,
        identClient = identClient,
        kontaktOgReservasjonsregister = kontaktOgReservasjonsregister,
        leaderPodLookup = leaderPodLookup,
        kafkaPublisher = kafkaPublisher,
        klageClient = klageClient,
        queryJournalpostClient = queryJournalpostClient,
        skatteOppslag = skatteOppslag,
        pesysklient = pesysClient,
        aapApiInternClient = aapApiInternClient,
        suProxyClient = suProxyClient,
        regoppslagKlient = regoppslagKlient,
        clamavClient = clamavClient,
    )

    override fun build(applicationConfig: ApplicationConfig): Clients = testClients
}
