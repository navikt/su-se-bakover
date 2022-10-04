package no.nav.su.se.bakover.web

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import no.finn.unleash.FakeUnleash
import no.finn.unleash.Unleash
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
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.migratedDb
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.satser.SatsFactoryForSupplerendeStønad
import no.nav.su.se.bakover.service.AccessCheckProxy
import no.nav.su.se.bakover.service.ServiceBuilder
import no.nav.su.se.bakover.service.Services
import no.nav.su.se.bakover.test.applicationConfig
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.satsFactoryTest
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.web.stubs.JwtStub
import no.nav.su.se.bakover.web.stubs.asBearerToken
import org.mockito.kotlin.mock
import java.time.Clock
import java.time.LocalDate
import javax.sql.DataSource

/**
 * TODO jah: Dette er foreløpig en kopi av TestEnvironment.kt og TestClientsBuilder.kt fra web/src/test (på sikt bør det meste av dette slettes derfra)
 * Vurder å trekk ut ting til test-common for de tingene som både web og web-regresjonstest trenger.
 */
internal object SharedRegressionTestData {
    internal val fnr: String = Fnr.generer().toString()
    internal val epsFnr: String = Fnr.generer().toString()

    private const val DEFAULT_CALL_ID = "her skulle vi sikkert hatt en korrelasjonsid"

    private val applicationConfig = applicationConfig()
    private val jwtStub = JwtStub(applicationConfig.azure)

    private val dbMetricsStub: DbMetrics = object : DbMetrics {
        override fun <T> timeQuery(label: String, block: () -> T): T {
            return block()
        }
    }

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
        test: ApplicationTestBuilder.() -> Unit,
    ) {
        withMigratedDb { dataSource ->
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
    }

    private fun Application.testSusebakover(
        clock: Clock = fixedClock,
        satsFactory: SatsFactoryForSupplerendeStønad = satsFactoryTest,
        databaseRepos: DatabaseRepos = databaseRepos(
            clock = clock,
            satsFactory = satsFactory,
        ),
        clients: Clients = TestClientsBuilder(clock, databaseRepos).build(applicationConfig),
        unleash: Unleash = FakeUnleash().apply { enableAll() },
        services: Services = ServiceBuilder.build(
            databaseRepos = databaseRepos,
            clients = clients,
            behandlingMetrics = mock(),
            søknadMetrics = mock(),
            clock = clock,
            unleash = unleash,
            satsFactory = satsFactoryTestPåDato(LocalDate.now(clock)),
            applicationConfig = applicationConfig(),
        ),
        accessCheckProxy: AccessCheckProxy = AccessCheckProxy(databaseRepos.person, services),
    ) {
        return susebakover(
            clock = clock,
            applicationConfig = applicationConfig,
            databaseRepos = databaseRepos,
            clients = clients,
            services = services,
            accessCheckProxy = accessCheckProxy,
        )
    }

    fun ApplicationTestBuilder.defaultRequest(
        method: HttpMethod,
        uri: String,
        roller: List<Brukerrolle> = emptyList(),
        navIdent: String = "Z990Lokal",
        setup: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse {
        return runBlocking {
            client.request(uri) {
                this.method = method
                this.headers {
                    append(HttpHeaders.XCorrelationId, DEFAULT_CALL_ID)
                    append(
                        HttpHeaders.Authorization,
                        jwtStub.createJwtToken(roller = roller, navIdent = navIdent).asBearerToken(),
                    )
                }
                setup()
            }
        }
    }
}

data class TestClientsBuilder(
    val clock: Clock,
    val databaseRepos: DatabaseRepos,
) : ClientsBuilder {
    private val testClients = Clients(
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
        skatteOppslag = SkatteClientStub(clock),
        maskinportenClient = MaskinportenClientStub(clock),
    )

    override fun build(applicationConfig: ApplicationConfig): Clients = testClients
}
