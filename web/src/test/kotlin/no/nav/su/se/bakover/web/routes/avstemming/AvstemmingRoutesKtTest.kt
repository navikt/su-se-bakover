package no.nav.su.se.bakover.web.routes.avstemming

import arrow.core.Either
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.endOfDay
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Fagområde
import no.nav.su.se.bakover.service.avstemming.AvstemmingFeilet
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal class AvstemmingRoutesKtTest {

    private val dummyAvstemming = Avstemming.Grensesnittavstemming(
        id = UUID30.randomUUID(),
        opprettet = fixedTidspunkt,
        fraOgMed = fixedTidspunkt,
        tilOgMed = fixedTidspunkt,
        utbetalinger = listOf(),
        avstemmingXmlRequest = null,
        fagområde = Fagområde.SUUFORE,
    )

    private fun happyAvstemmingService() = object : AvstemmingService {
        override fun grensesnittsavstemming(fagområde: Fagområde): Either<AvstemmingFeilet, Avstemming.Grensesnittavstemming> {
            return dummyAvstemming.right()
        }

        override fun grensesnittsavstemming(
            fraOgMed: Tidspunkt,
            tilOgMed: Tidspunkt,
            fagområde: Fagområde,
        ): Either<AvstemmingFeilet, Avstemming.Grensesnittavstemming> {
            return dummyAvstemming.right()
        }

        override fun konsistensavstemming(
            løpendeFraOgMed: LocalDate,
            fagområde: Fagområde,
        ): Either<AvstemmingFeilet, Avstemming.Konsistensavstemming.Ny> {
            return Avstemming.Konsistensavstemming.Ny(
                id = UUID30.randomUUID(),
                opprettet = fixedTidspunkt,
                løpendeFraOgMed = løpendeFraOgMed.startOfDay(),
                opprettetTilOgMed = løpendeFraOgMed.endOfDay(),
                utbetalinger = listOf(),
                avstemmingXmlRequest = null,
                fagområde = Fagområde.SUUFORE,
            ).right()
        }

        override fun konsistensavstemmingUtførtForOgPåDato(dato: LocalDate, fagområde: Fagområde): Boolean {
            return false
        }
    }

    private fun services() = TestServicesBuilder.services().copy(
        avstemming = happyAvstemmingService(),
    )

    @Test
    fun `kall uten periode parametre gir OK`() {
        testApplication {
            application {
                testSusebakover(services = services())
            }
            defaultRequest(
                Post,
                "/avstemming/grensesnitt?fagomrade=SUUFORE",
                listOf(Brukerrolle.Drift),
            ).apply {
                status shouldBe HttpStatusCode.Created
            }
        }
    }

    @Test
    fun `kall med enten fraOgMed _eller_ tilOgMed feiler`() {
        testApplication {
            application {
                testSusebakover(services = services())
            }
            defaultRequest(
                Post,
                "/avstemming/grensesnitt?fraOgMed=2020-11-01&fagomrade=SUUFORE",
                listOf(Brukerrolle.Drift),
            ).apply {
                status shouldBe HttpStatusCode.BadRequest
            }

            defaultRequest(
                Post,
                "/avstemming/grensesnitt?tilOgMed=2020-11-01&fagomrade=SUUFORE",
                listOf(Brukerrolle.Drift),
            ).apply {
                status shouldBe HttpStatusCode.BadRequest
            }
        }
    }

    @Test
    fun `kall med fraOgMed eller tilOgMed på feil format feiler`() {
        testApplication {
            application {
                testSusebakover(services = services())
            }
            listOf(
                "/avstemming/grensesnitt?fraOgMed=2020-11-17T11:02:19Z&tilOgMed=2020-11-17&fagomrade=SUUFORE",
                "/avstemming/grensesnitt?fraOgMed=2020-11-12T11:02:19Z&tilOgMed=2020-11-17T11:02:19Z&fagomrade=SUUFORE",
            ).forEach {
                defaultRequest(
                    Post,
                    it,
                    listOf(Brukerrolle.Drift),
                ).apply {
                    status shouldBe HttpStatusCode.BadRequest
                }
            }
        }
    }

    @Test
    fun `kall med fraOgMed eller tilOgMed må ha fraOgMed før tilOgMed`() {
        testApplication {
            application {
                testSusebakover(services = services())
            }
            listOf(
                "/avstemming/grensesnitt?fraOgMed=2020-11-18&tilOgMed=2020-11-17&fagomrade=SUUFORE",
                "/avstemming/grensesnitt?fraOgMed=2021-11-18&tilOgMed=2020-11-12&fagomrade=SUUFORE",
            ).forEach {
                defaultRequest(
                    Post,
                    it,
                    listOf(Brukerrolle.Drift),
                ).apply {
                    status shouldBe HttpStatusCode.BadRequest
                }
            }
        }
    }

    @Test
    fun `kall med tilOgMed etter dagens dato feiler`() {
        testApplication {
            application { testSusebakover(services = services()) }

            listOf(
                "/avstemming/grensesnitt?fraOgMed=2020-11-11&tilOgMed=${
                fixedLocalDate.plusDays(1).format(DateTimeFormatter.ISO_DATE)
                }&fagomrade=SUUFORE",
            ).forEach {
                defaultRequest(
                    Post,
                    it,
                    listOf(Brukerrolle.Drift),
                ).apply {
                    status shouldBe HttpStatusCode.BadRequest
                }
            }
        }
    }

    @Test
    fun `kall til konsistensavstemming uten fraOgMed går ikke`() {
        testApplication {
            application { testSusebakover(services = services()) }

            listOf(
                "/avstemming/konsistens",
            ).forEach {
                defaultRequest(
                    Post,
                    it,
                    listOf(Brukerrolle.Drift),
                ).apply {
                    status shouldBe HttpStatusCode.BadRequest
                    bodyAsText() shouldContain "'fraOgMed' mangler"
                }
            }
        }
    }

    @Test
    fun `kall til konsistensavstemming går fint`() {
        testApplication {
            application { testSusebakover(services = services()) }

            listOf(
                "/avstemming/konsistens?fraOgMed=2021-01-01&fagomrade=SUUFORE",
            ).forEach {
                defaultRequest(
                    Post,
                    it,
                    listOf(Brukerrolle.Drift),
                ).apply {
                    status shouldBe HttpStatusCode.OK
                }
            }
        }
    }

    @Test
    fun `kun driftspersonell kan kalle endepunktet for avstemming`() {
        testApplication {
            application { testSusebakover(services = services()) }

            Brukerrolle.values()
                .filterNot { it == Brukerrolle.Drift }
                .forEach { _ ->
                    defaultRequest(
                        Post,
                        "/avstemming/konsistens?fraOgMed=2021-01-01&fagomrade=SUUFORE",
                        listOf(Brukerrolle.Veileder),
                    ).apply {
                        status shouldBe HttpStatusCode.Forbidden
                    }
                }
        }
    }
}
