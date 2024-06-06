package no.nav.su.se.bakover.web

import io.ktor.server.application.Application
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
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
import no.nav.su.se.bakover.client.stubs.oppdrag.UtbetalingStub
import no.nav.su.se.bakover.client.stubs.oppgave.OppgaveClientStub
import no.nav.su.se.bakover.client.stubs.pdf.PdfGeneratorStub
import no.nav.su.se.bakover.client.stubs.person.IdentClientStub
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.common.infrastructure.auth.TokenOppslagStub
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.JournalpostIdGeneratorForFakes
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.brev.JournalførBrevFakeClient
import no.nav.su.se.bakover.dokument.infrastructure.client.journalføring.søknad.JournalførSøknadFakeClient
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.test.applicationConfig
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.persistence.dbMetricsStub
import no.nav.su.se.bakover.test.persistence.migratedDb
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.satsFactoryTest
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.komponenttest.testSusebakover
import no.nav.su.se.bakover.web.services.AccessCheckProxy
import no.nav.su.se.bakover.web.services.ServiceBuilder
import no.nav.su.se.bakover.web.services.Services
import org.mockito.kotlin.mock
import person.domain.PersonOppslag
import satser.domain.supplerendestønad.SatsFactoryForSupplerendeStønad
import tilbakekreving.presentation.consumer.KravgrunnlagDtoMapper
import vilkår.formue.domain.FormuegrenserFactory
import vilkår.skatt.infrastructure.client.SkatteClientStub
import java.time.Clock
import java.time.LocalDate
import javax.sql.DataSource

/**
 * TODO jah: Dette er foreløpig en kopi av TestEnvironment.kt og TestClientsBuilder.kt fra web/src/test (på sikt bør det meste av dette slettes derfra)
 * Vurder å trekk ut ting til test-common for de tingene som både web og web-regresjonstest trenger.
 */
data object SharedRegressionTestData {
    internal val fnr: String = Fnr.generer().toString()
    internal val epsFnr: String = Fnr.generer().toString()

    private val applicationConfig = applicationConfig()

    internal const val DOKEMENT_DATA =
        "JVBERi0xLjAKICAgICAgICAgICAgICAgIDEgMCBvYmo8PC9UeXBlL0NhdGFsb2cvUGFnZXMgMiAwIFI+PmVuZG9iaiAyIDAgb2JqPDwvVHlwZS9QYWdlcy9LaWRzWzMgMCBSXS9Db3VudCAxPj5lbmRvYmogMyAwIG9iajw8L1R5cGUvUGFnZS9NZWRpYUJveFswIDAgMyAzXT4+ZW5kb2JqCiAgICAgICAgICAgICAgICB4cmVmCiAgICAgICAgICAgICAgICAwIDQKICAgICAgICAgICAgICAgIDAwMDAwMDAwMDAgNjU1MzUgZgogICAgICAgICAgICAgICAgMDAwMDAwMDAxMCAwMDAwMCBuCiAgICAgICAgICAgICAgICAwMDAwMDAwMDUzIDAwMDAwIG4KICAgICAgICAgICAgICAgIDAwMDAwMDAxMDIgMDAwMDAgbgogICAgICAgICAgICAgICAgdHJhaWxlcjw8L1NpemUgNC9Sb290IDEgMCBSPj4KICAgICAgICAgICAgICAgIHN0YXJ0eHJlZgogICAgICAgICAgICAgICAgMTQ5CiAgICAgICAgICAgICAgICAlRU9G"
    internal val pdf = """%PDF-1.0
                1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj 2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj 3 0 obj<</Type/Page/MediaBox[0 0 3 3]>>endobj
                xref
                0 4
                0000000000 65535 f
                0000000010 00000 n
                0000000053 00000 n
                0000000102 00000 n
                trailer<</Size 4/Root 1 0 R>>
                startxref
                149
                %EOF
    """.trimIndent()

    internal fun databaseRepos(
        dataSource: DataSource = migratedDb(),
        clock: Clock = fixedClock,
        satsFactory: SatsFactoryForSupplerendeStønad = satsFactoryTest,
    ): DatabaseRepos {
        return DatabaseBuilder.build(
            embeddedDatasource = dataSource,
            dbMetrics = dbMetricsStub,
            clock = clock,
            satsFactory = satsFactory,
            råttKravgrunnlagMapper = KravgrunnlagDtoMapper::toKravgrunnlag,
        )
    }

    /**
     * Uses the local docker-database as datasource.
     * @param clock defaults to system UTC
     */
    internal fun withTestApplicationAndDockerDb(
        clock: Clock = Clock.systemUTC(),
        test: ApplicationTestBuilder.() -> Unit,
    ) {
        val dataSource = DatabaseBuilder.newLocalDataSource()
        DatabaseBuilder.migrateDatabase(dataSource)

        testApplication {
            application {
                testSusebakover(
                    clock = clock,
                    databaseRepos = databaseRepos(
                        dataSource = dataSource,
                        clock = clock,
                    ),
                )
            }
            test()
        }
    }

    internal fun withTestApplicationAndEmbeddedDb(
        clock: Clock = fixedClock,
        utbetalingerKjørtTilOgMed: (clock: Clock) -> LocalDate = { LocalDate.now(it) },
        satsFactory: SatsFactoryForSupplerendeStønad = satsFactoryTest,
        test: ApplicationTestBuilder.(appComponents: AppComponents) -> Unit,
    ) {
        withMigratedDb { dataSource ->
            val appComponents = AppComponents.from(dataSource, clock, utbetalingerKjørtTilOgMed, satsFactory, applicationConfig)
            testApplication {
                application {
                    testSusebakover(appComponents = appComponents)
                }
                test(appComponents)
            }
        }
    }

    fun Application.testSusebakover(
        clock: Clock = fixedClock,
        utbetalingerKjørtTilOgMed: (clock: Clock) -> LocalDate = { LocalDate.now(it) },
        satsFactory: SatsFactoryForSupplerendeStønad = satsFactoryTest,
        databaseRepos: DatabaseRepos = databaseRepos(
            clock = clock,
            satsFactory = satsFactory,
        ),
        clients: Clients = TestClientsBuilder(
            clock = clock,
            utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
            databaseRepos = databaseRepos,
        ).build(applicationConfig),
        services: Services = run {
            val satsFactoryIDag = satsFactoryTestPåDato(LocalDate.now(clock))
            val formuegrenserFactoryIDag = FormuegrenserFactory.createFromGrunnbeløp(
                grunnbeløpFactory = satsFactoryIDag.grunnbeløpFactory,
                tidligsteTilgjengeligeMåned = satsFactoryIDag.tidligsteTilgjengeligeMåned,
            )
            ServiceBuilder.build(
                databaseRepos = databaseRepos,
                clients = clients,
                clock = clock,
                satsFactory = satsFactoryIDag,
                formuegrenserFactory = formuegrenserFactoryIDag,
                applicationConfig = applicationConfig(),
                dbMetrics = dbMetricsStub,
            )
        },
        accessCheckProxy: AccessCheckProxy = AccessCheckProxy(databaseRepos.person, services),
    ) {
        return susebakover(
            clock = clock,
            applicationConfig = applicationConfig,
            databaseRepos = databaseRepos,
            clients = clients,
            services = services,
            accessCheckProxy = accessCheckProxy,
            disableConsumersAndJobs = true,
        )
    }
}

data class TestClientsBuilder(
    val clock: Clock,
    val utbetalingerKjørtTilOgMed: (clock: Clock) -> LocalDate = { LocalDate.now(it) },
    val databaseRepos: DatabaseRepos,
    val personOppslag: PersonOppslag = PersonOppslagStub(),
) : ClientsBuilder {
    private val journalpostIdGenerator = JournalpostIdGeneratorForFakes()
    private val testClients = Clients(
        oauth = AzureClientStub,
        personOppslag = personOppslag,
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
            utbetalingerKjørtTilOgMed = utbetalingerKjørtTilOgMed,
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
        skatteOppslag = SkatteClientStub(clock),
    )

    override fun build(applicationConfig: ApplicationConfig): Clients = testClients
}
