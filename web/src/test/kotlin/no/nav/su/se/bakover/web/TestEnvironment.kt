package no.nav.su.se.bakover.web

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.testing.ApplicationTestBuilder
import no.finn.unleash.FakeUnleash
import no.finn.unleash.Unleash
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.migratedDb
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.service.AccessCheckProxy
import no.nav.su.se.bakover.service.ServiceBuilder
import no.nav.su.se.bakover.service.Services
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.satsFactoryTest
import no.nav.su.se.bakover.web.stubs.JwtStub
import no.nav.su.se.bakover.web.stubs.asBearerToken
import java.time.Clock
import java.time.LocalDate

const val DEFAULT_CALL_ID = "her skulle vi sikkert hatt en korrelasjonsid"

val applicationConfig = ApplicationConfig(
    runtimeEnvironment = ApplicationConfig.RuntimeEnvironment.Test,
    naisCluster = null,
    leaderPodLookupPath = "leaderPodLookupPath",
    pdfgenLocal = false,
    serviceUser = ApplicationConfig.ServiceUserConfig(
        username = "serviceUserTestUsername",
        password = "serviceUserTestPassword",
    ),
    azure = ApplicationConfig.AzureConfig(
        clientSecret = "testClientSecret",
        wellKnownUrl = "http://localhost/test/wellKnownUrl",
        clientId = "testClientId",
        groups = ApplicationConfig.AzureConfig.AzureGroups(
            attestant = "d3340bf6-a8bd-ATTESTANT-97c3-a2144b9ac34a",
            saksbehandler = "d3340bf6-a8bd-SAKSBEHANDLER-97c3-a2144b9ac34a",
            veileder = "d3340bf6-a8bd-VEILEDER-97c3-a2144b9ac34a",
            drift = "d3340bf6-a8bd-DRIFT-97c3-a2144b9ac34a",
        ),
    ),
    frikort = ApplicationConfig.FrikortConfig(
        serviceUsername = "frikort",
        useStubForSts = true,
    ),
    oppdrag = ApplicationConfig.OppdragConfig(
        mqQueueManager = "testMqQueueManager",
        mqPort = -22,
        mqHostname = "testMqHostname",
        mqChannel = "testMqChannel",
        utbetaling = ApplicationConfig.OppdragConfig.UtbetalingConfig(
            mqSendQueue = "testMqSendQueue",
            mqReplyTo = "testMqReplyTo",
        ),
        avstemming = ApplicationConfig.OppdragConfig.AvstemmingConfig(mqSendQueue = "avstemmingMqTestSendQueue"),
        simulering = ApplicationConfig.OppdragConfig.SimuleringConfig(
            url = "simuleringTestUrl",
            stsSoapUrl = "simuleringStsTestSoapUrl",
        ),
        tilbakekreving = ApplicationConfig.OppdragConfig.TilbakekrevingConfig(
            mq = ApplicationConfig.OppdragConfig.TilbakekrevingConfig.Mq(
                mottak = "tilbakekrevingMqTestSendQueue",
            ),
            soap = ApplicationConfig.OppdragConfig.TilbakekrevingConfig.Soap(
                url = "tilbakekrevingUrl",
            ),
        ),
    ),
    database = ApplicationConfig.DatabaseConfig.StaticCredentials(
        jdbcUrl = "jdbcTestUrl",
    ),
    clientsConfig = ApplicationConfig.ClientsConfig(
        oppgaveConfig = ApplicationConfig.ClientsConfig.OppgaveConfig(
            clientId = "oppgaveClientId",
            url = "oppgaveUrl",
        ),
        pdlConfig = ApplicationConfig.ClientsConfig.PdlConfig(
            url = "pdlUrl",
            clientId = "pdlClientId",
        ),
        dokDistUrl = "dokDistUrl",
        pdfgenUrl = "pdfgenUrl",
        dokarkivUrl = "dokarkivUrl",
        kodeverkUrl = "kodeverkUrl",
        stsUrl = "stsUrl",
        skjermingUrl = "skjermingUrl",
        dkifUrl = "dkifUrl",
        kabalConfig = ApplicationConfig.ClientsConfig.KabalConfig("kabalUrl", "kabalClientId"),
        safConfig = ApplicationConfig.ClientsConfig.SafConfig("safUrlkabalUrl", "safClientId"),
    ),
    kafkaConfig = ApplicationConfig.KafkaConfig(
        producerCfg = ApplicationConfig.KafkaConfig.ProducerCfg(emptyMap()),
        consumerCfg = ApplicationConfig.KafkaConfig.ConsumerCfg(emptyMap()),
    ),
    unleash = ApplicationConfig.UnleashConfig("https://localhost", "su-se-bakover"),
    kabalKafkaConfig = ApplicationConfig.KabalKafkaConfig(
        kafkaConfig = emptyMap(),
    ),
)

internal val jwtStub = JwtStub(applicationConfig.azure)

internal val dbMetricsStub: DbMetrics = object : DbMetrics {
    override fun <T> timeQuery(label: String, block: () -> T): T {
        return block()
    }
}

internal fun mockedDb() = TestDatabaseBuilder.build()
internal fun embeddedPostgres(
    clock: Clock = fixedClock,
    satsFactory: SatsFactory = satsFactoryTest,
) = DatabaseBuilder.build(
    embeddedDatasource = migratedDb(),
    dbMetrics = dbMetricsStub,
    clock = clock,
    satsFactory = satsFactory,
)

internal fun Application.testSusebakover(
    clock: Clock = fixedClock,
    databaseRepos: DatabaseRepos = mockedDb(),
    clients: Clients = TestClientsBuilder(clock, databaseRepos)
        .build(applicationConfig),
    unleash: Unleash = FakeUnleash().apply { enableAll() },
    /** Bruk gjeldende satser i hht angitt [clock] */
    satsFactory: SatsFactory = satsFactoryTest.gjeldende(LocalDate.now(clock)),
    services: Services = ServiceBuilder.build(
        // build actual clients
        databaseRepos = databaseRepos,
        clients = clients,
        behandlingMetrics = org.mockito.kotlin.mock(),
        søknadMetrics = org.mockito.kotlin.mock(),
        clock = clock,
        unleash = unleash,
        satsFactory = satsFactory,
    ),
    accessCheckProxy: AccessCheckProxy = AccessCheckProxy(
        databaseRepos.person,
        services,
    ),
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

suspend fun ApplicationTestBuilder.defaultRequest(
    method: HttpMethod,
    uri: String,
    roller: List<Brukerrolle> = emptyList(),
    setup: HttpRequestBuilder.() -> Unit = {},
): HttpResponse {
    return this.client.request(uri) {
        this.method = method
        this.headers {
            append(HttpHeaders.XCorrelationId, DEFAULT_CALL_ID)
            append(HttpHeaders.Authorization, jwtStub.createJwtToken(roller = roller).asBearerToken())
        }
        setup()
    }
}

suspend fun ApplicationTestBuilder.defaultRequest(
    method: HttpMethod,
    uri: String,
    roller: List<Brukerrolle> = emptyList(),
    navIdent: String,
    setup: HttpRequestBuilder.() -> Unit = {},
): HttpResponse {
    return this.client.request(uri) {
        this.method = method
        this.headers {
            append(HttpHeaders.XCorrelationId, DEFAULT_CALL_ID)
            append(
                HttpHeaders.Authorization,
                jwtStub.createJwtToken(
                    roller = roller,
                    navIdent = navIdent,
                ).asBearerToken(),
            )
        }
        setup()
    }
}

suspend fun ApplicationTestBuilder.requestSomAttestant(
    method: HttpMethod,
    uri: String,
    navIdent: String? = navIdentAttestant,
    setup: HttpRequestBuilder.() -> Unit = {},
): HttpResponse {
    return this.client.request(uri) {
        this.method = method
        this.headers {
            append(HttpHeaders.XCorrelationId, DEFAULT_CALL_ID)
            append(
                HttpHeaders.Authorization,
                jwtStub.createJwtToken(
                    roller = listOf(Brukerrolle.Attestant),
                    navIdent = navIdent,
                ).asBearerToken(),
            )
        }
        setup()
    }
}

val navIdentAttestant = "random-attestant-id"
