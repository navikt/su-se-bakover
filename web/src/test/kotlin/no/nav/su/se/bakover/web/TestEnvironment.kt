package no.nav.su.se.bakover.web

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.testing.ApplicationTestBuilder
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.test.applicationConfig
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.jwt.JwtStub
import no.nav.su.se.bakover.test.jwt.asBearerToken
import no.nav.su.se.bakover.test.persistence.dbMetricsStub
import no.nav.su.se.bakover.test.satsFactoryTest
import no.nav.su.se.bakover.web.services.AccessCheckProxy
import no.nav.su.se.bakover.web.services.ServiceBuilder
import no.nav.su.se.bakover.web.services.Services
import java.time.Clock
import java.time.LocalDate

const val DEFAULT_CALL_ID = "her skulle vi sikkert hatt en korrelasjonsid"

private val applicationConfig = applicationConfig()
internal val jwtStub = JwtStub(applicationConfig.azure)

internal fun mockedDb() = MockDatabaseBuilder.build()

internal fun Application.testSusebakoverWithMockedDb(
    clock: Clock = fixedClock,
    databaseRepos: DatabaseRepos = mockedDb(),
    clients: Clients = TestClientsBuilder(clock, databaseRepos).build(applicationConfig),
    /** Bruk gjeldende satser i hht angitt [clock] */
    satsFactory: SatsFactory = satsFactoryTest.gjeldende(LocalDate.now(clock)),
    services: Services = run {
        ServiceBuilder.build(
            // build actual clients
            databaseRepos = databaseRepos,
            clients = clients,
            behandlingMetrics = org.mockito.kotlin.mock(),
            søknadMetrics = org.mockito.kotlin.mock(),
            clock = clock,
            satsFactory = satsFactory,
            formuegrenserFactory = satsFactory.formuegrenserFactory,
            applicationConfig = applicationConfig(),
            dbMetrics = dbMetricsStub,
        )
    },
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
    jwtSubject: String = "serviceUserTestUsername",
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
                    subject = jwtSubject,
                ).asBearerToken(),
            )
        }
        setup()
    }
}

suspend fun ApplicationTestBuilder.requestSomAttestant(
    method: HttpMethod,
    uri: String,
    navIdent: String? = NAV_IDENT_ATTESTANT,
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

const val NAV_IDENT_ATTESTANT = "random-attestant-id"
