package no.nav.su.se.bakover.web.routes.avstemming

import arrow.core.Either
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.service.ServiceBuilder
import no.nav.su.se.bakover.service.avstemming.AvstemmingFeilet
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.applicationConfig
import no.nav.su.se.bakover.web.dbMetricsStub
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.fixedClock
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal class AvstemmingRoutesKtTest {
    private val repos = DatabaseBuilder.build(
        embeddedDatasource = EmbeddedDatabase.instance(),
        dbMetrics = dbMetricsStub,
    )
    private val services = ServiceBuilder.build(
        databaseRepos = repos,
        clients = TestClientsBuilder.build(applicationConfig),
        behandlingMetrics = mock(),
        søknadMetrics = mock(),
        clock = fixedClock,
        unleash = mock()
    )

    private val dummyAvstemming = Avstemming(
        id = UUID30.randomUUID(),
        opprettet = Tidspunkt.now(),
        fraOgMed = Tidspunkt.now(),
        tilOgMed = Tidspunkt.now(),
        utbetalinger = listOf(),
        avstemmingXmlRequest = null
    )

    private val happyAvstemmingService = object : AvstemmingService {
        override fun avstemming(): Either<AvstemmingFeilet, Avstemming> =
            Either.Right(dummyAvstemming)

        override fun avstemming(
            fraOgMed: Tidspunkt,
            tilOgMed: Tidspunkt
        ): Either<AvstemmingFeilet, Avstemming> =
            Either.Right(dummyAvstemming)
    }

    @Test
    fun `kall uten parametre gir OK`() {
        withTestApplication({
            testSusebakover(
                services = services.copy(
                    avstemming = happyAvstemmingService
                ),
                clock = fixedClock,
            )
        }) {
            defaultRequest(
                HttpMethod.Post,
                "/avstem",
                listOf(Brukerrolle.Drift)
            ).apply {
                response.status() shouldBe HttpStatusCode.OK
            }
        }
    }

    @Test
    fun `kall med enten fraOgMed _eller_ tilOgMed feiler`() {
        withTestApplication({
            testSusebakover(
                services = services.copy(
                    avstemming = happyAvstemmingService
                )
            )
        }) {
            defaultRequest(
                HttpMethod.Post,
                "/avstem?fraOgMed=2020-11-01",
                listOf(Brukerrolle.Drift)
            ).apply {
                response.status() shouldBe HttpStatusCode.BadRequest
            }

            defaultRequest(
                HttpMethod.Post,
                "/avstem?tilOgMed=2020-11-01",
                listOf(Brukerrolle.Drift)
            ).apply {
                response.status() shouldBe HttpStatusCode.BadRequest
            }
        }
    }

    @Test
    fun `kall med fraOgMed eller tilOgMed på feil format feiler`() {
        withTestApplication({
            testSusebakover(
                services = services.copy(
                    avstemming = happyAvstemmingService
                )
            )
        }) {
            listOf(
                "/avstem?fraOgMed=2020-11-17T11:02:19Z&tilOgMed=2020-11-17",
                "/avstem?fraOgMed=2020-11-12T11:02:19Z&tilOgMed=2020-11-17T11:02:19Z",
            ).forEach {
                defaultRequest(
                    HttpMethod.Post,
                    it,
                    listOf(Brukerrolle.Drift)
                ).apply {
                    response.status() shouldBe HttpStatusCode.BadRequest
                }
            }
        }
    }

    @Test
    fun `kall med fraOgMed eller tilOgMed må ha fraOgMed før tilOgMed`() {
        withTestApplication({
            testSusebakover(
                services = services.copy(
                    avstemming = happyAvstemmingService
                )
            )
        }) {
            listOf(
                "/avstem?fraOgMed=2020-11-18&tilOgMed=2020-11-17",
                "/avstem?fraOgMed=2021-11-18&tilOgMed=2020-11-12",
            ).forEach {
                defaultRequest(
                    HttpMethod.Post,
                    it,
                    listOf(Brukerrolle.Drift)
                ).apply {
                    response.status() shouldBe HttpStatusCode.BadRequest
                }
            }
        }
    }

    @Test
    fun `kall med tilOgMed etter dagens dato feiler`() {
        withTestApplication({
            testSusebakover(
                services = services.copy(
                    avstemming = happyAvstemmingService
                )
            )
        }) {
            listOf(
                "/avstem?fraOgMed=2020-11-11&tilOgMed=${
                LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_DATE)
                }",
            ).forEach {
                defaultRequest(
                    HttpMethod.Post,
                    it,
                    listOf(Brukerrolle.Drift)
                ).apply {
                    response.status() shouldBe HttpStatusCode.BadRequest
                }
            }
        }
    }
}
