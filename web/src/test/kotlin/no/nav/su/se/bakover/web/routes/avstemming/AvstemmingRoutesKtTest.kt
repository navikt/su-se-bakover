package no.nav.su.se.bakover.web.routes.avstemming

import arrow.core.Either
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.endOfDay
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.DatabaseRepos
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.service.ServiceBuilder
import no.nav.su.se.bakover.service.avstemming.AvstemmingFeilet
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.applicationConfig
import no.nav.su.se.bakover.web.dbMetricsStub
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.sql.DataSource

internal class AvstemmingRoutesKtTest {
    private fun repos(dataSource: DataSource) = DatabaseBuilder.build(
        embeddedDatasource = dataSource,
        dbMetrics = dbMetricsStub,
        clock = fixedClock,
    )

    private fun services(databaseRepos: DatabaseRepos) = ServiceBuilder.build(
        databaseRepos = databaseRepos,
        clients = TestClientsBuilder.build(applicationConfig),
        behandlingMetrics = mock(),
        søknadMetrics = mock(),
        clock = fixedClock,
        unleash = mock(),
    )

    private val dummyAvstemming = Avstemming.Grensesnittavstemming(
        id = UUID30.randomUUID(),
        opprettet = fixedTidspunkt,
        fraOgMed = fixedTidspunkt,
        tilOgMed = fixedTidspunkt,
        utbetalinger = listOf(),
        avstemmingXmlRequest = null,
    )

    private val happyAvstemmingService = object : AvstemmingService {
        override fun grensesnittsavstemming(): Either<AvstemmingFeilet, Avstemming.Grensesnittavstemming> {
            return dummyAvstemming.right()
        }

        override fun grensesnittsavstemming(
            fraOgMed: Tidspunkt,
            tilOgMed: Tidspunkt,
        ): Either<AvstemmingFeilet, Avstemming.Grensesnittavstemming> {
            return dummyAvstemming.right()
        }

        override fun konsistensavstemming(
            løpendeFraOgMed: LocalDate,
        ): Either<AvstemmingFeilet, Avstemming.Konsistensavstemming.Ny> {
            return Avstemming.Konsistensavstemming.Ny(
                id = UUID30.randomUUID(),
                opprettet = fixedTidspunkt,
                løpendeFraOgMed = løpendeFraOgMed.startOfDay(),
                opprettetTilOgMed = løpendeFraOgMed.endOfDay(),
                utbetalinger = listOf(),
                avstemmingXmlRequest = null,
            ).right()
        }
    }

    @Test
    fun `kall uten parametre gir OK`() {
        withMigratedDb { dataSource ->
            val repos = repos(dataSource)
            val services = services(repos).copy(
                avstemming = happyAvstemmingService,
            )

            withTestApplication(
                {
                    testSusebakover(
                        services = services.copy(
                            avstemming = happyAvstemmingService,
                        ),
                        clock = fixedClock,
                    )
                },
            ) {
                defaultRequest(
                    HttpMethod.Post,
                    "/avstemming/grensesnitt",
                    listOf(Brukerrolle.Drift),
                ).apply {
                    response.status() shouldBe HttpStatusCode.OK
                }
            }
        }
    }

    @Test
    fun `kall med enten fraOgMed _eller_ tilOgMed feiler`() {
        withMigratedDb { dataSource ->
            val repos = repos(dataSource)
            val services = services(repos).copy(
                avstemming = happyAvstemmingService,
            )
            withTestApplication(
                {
                    testSusebakover(
                        services = services,
                    )
                },
            ) {
                defaultRequest(
                    HttpMethod.Post,
                    "/avstemming/grensesnitt?fraOgMed=2020-11-01",
                    listOf(Brukerrolle.Drift),
                ).apply {
                    response.status() shouldBe HttpStatusCode.BadRequest
                }

                defaultRequest(
                    HttpMethod.Post,
                    "/avstemming/grensesnitt?tilOgMed=2020-11-01",
                    listOf(Brukerrolle.Drift),
                ).apply {
                    response.status() shouldBe HttpStatusCode.BadRequest
                }
            }
        }
    }

    @Test
    fun `kall med fraOgMed eller tilOgMed på feil format feiler`() {
        withMigratedDb { dataSource ->
            val repos = repos(dataSource)
            val services = services(repos).copy(
                avstemming = happyAvstemmingService,
            )
            withTestApplication(
                {
                    testSusebakover(
                        services = services,
                    )
                },
            ) {
                listOf(
                    "/avstemming/grensesnitt?fraOgMed=2020-11-17T11:02:19Z&tilOgMed=2020-11-17",
                    "/avstemming/grensesnitt?fraOgMed=2020-11-12T11:02:19Z&tilOgMed=2020-11-17T11:02:19Z",
                ).forEach {
                    defaultRequest(
                        HttpMethod.Post,
                        it,
                        listOf(Brukerrolle.Drift),
                    ).apply {
                        response.status() shouldBe HttpStatusCode.BadRequest
                    }
                }
            }
        }
    }

    @Test
    fun `kall med fraOgMed eller tilOgMed må ha fraOgMed før tilOgMed`() {
        withMigratedDb { dataSource ->
            val repos = repos(dataSource)
            val services = services(repos).copy(
                avstemming = happyAvstemmingService,
            )
            withTestApplication(
                {
                    testSusebakover(
                        services = services.copy(
                            avstemming = happyAvstemmingService,
                        ),
                    )
                },
            ) {
                listOf(
                    "/avstemming/grensesnitt?fraOgMed=2020-11-18&tilOgMed=2020-11-17",
                    "/avstemming/grensesnitt?fraOgMed=2021-11-18&tilOgMed=2020-11-12",
                ).forEach {
                    defaultRequest(
                        HttpMethod.Post,
                        it,
                        listOf(Brukerrolle.Drift),
                    ).apply {
                        response.status() shouldBe HttpStatusCode.BadRequest
                    }
                }
            }
        }
    }

    @Test
    fun `kall med tilOgMed etter dagens dato feiler`() {
        withMigratedDb { dataSource ->
            val repos = repos(dataSource)
            val services = services(repos).copy(
                avstemming = happyAvstemmingService,
            )
            withTestApplication(
                {
                    testSusebakover(
                        services = services,
                    )
                },
            ) {
                listOf(
                    "/avstemming/grensesnitt?fraOgMed=2020-11-11&tilOgMed=${
                    fixedLocalDate.plusDays(1).format(DateTimeFormatter.ISO_DATE)
                    }",
                ).forEach {
                    defaultRequest(
                        HttpMethod.Post,
                        it,
                        listOf(Brukerrolle.Drift),
                    ).apply {
                        response.status() shouldBe HttpStatusCode.BadRequest
                    }
                }
            }
        }
    }

    @Test
    fun `kall til konsistensavstemming uten fraOgMed går ikke`() {
        withMigratedDb { dataSource ->
            val repos = repos(dataSource)
            val services = services(repos).copy(
                avstemming = happyAvstemmingService,
            )
            withTestApplication(
                {
                    testSusebakover(
                        services = services,
                    )
                },
            ) {
                listOf(
                    "/avstemming/konsistens",
                ).forEach {
                    defaultRequest(
                        HttpMethod.Post,
                        it,
                        listOf(Brukerrolle.Drift),
                    ).apply {
                        response.status() shouldBe HttpStatusCode.BadRequest
                        response.content shouldContain "'fraOgMed' mangler"
                    }
                }
            }
        }
    }

    @Test
    fun `kall til konsistensavstemming går fint`() {
        withMigratedDb { dataSource ->
            val repos = repos(dataSource)
            val services = services(repos).copy(
                avstemming = happyAvstemmingService,
            )
            withTestApplication(
                {
                    testSusebakover(
                        services = services.copy(
                            avstemming = happyAvstemmingService,
                        ),
                    )
                },
            ) {
                listOf(
                    "/avstemming/konsistens?fraOgMed=2021-01-01",
                ).forEach {
                    defaultRequest(
                        HttpMethod.Post,
                        it,
                        listOf(Brukerrolle.Drift),
                    ).apply {
                        response.status() shouldBe HttpStatusCode.OK
                    }
                }
            }
        }
    }
}
