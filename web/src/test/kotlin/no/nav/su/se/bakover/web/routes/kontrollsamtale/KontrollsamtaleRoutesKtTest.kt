package no.nav.su.se.bakover.web.routes.kontrollsamtale

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.service.kontrollsamtale.KontrollsamtaleService
import no.nav.su.se.bakover.service.kontrollsamtale.KunneIkkeHenteKontrollsamtale
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.kontrollsamtale
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.LocalDate
import java.util.UUID

internal class KontrollsamtaleRoutesKtTest {

    private val validBody = """
        {"sakId": "${UUID.randomUUID()}", "nyDato": "${LocalDate.now(fixedClock).plusMonths(2)}"}
    """.trimIndent()

    @Test
    fun `må være innlogget for å endre dato på kontrollsamtale`() {
        testApplication {
            application { testSusebakover() }
            client.post("/kontrollsamtale/nyDato").apply {
                status shouldBe HttpStatusCode.Unauthorized
            }
        }
    }

    @Test
    fun `saksbehandler skal kunne endre dato`() {
        val kontrollsamtaleMock = mock<KontrollsamtaleService> {
            on { nyDato(any(), any()) } doReturn Unit.right()
        }
        testApplication {
            application {
                testSusebakover(
                    services = TestServicesBuilder.services(kontrollsamtaleService = kontrollsamtaleMock),
                )
            }
            defaultRequest(HttpMethod.Post, "/kontrollsamtale/nyDato", listOf(Brukerrolle.Saksbehandler)) {
                setBody(validBody)
            }.apply {
                status shouldBe HttpStatusCode.OK
            }
        }
    }

    @Test
    fun `må være innlogget for å hente kontrollsamtale`() {
        testApplication {
            application { testSusebakover() }
            client.get("/kontrollsamtale/hent/${UUID.randomUUID()}").apply {
                status shouldBe HttpStatusCode.Unauthorized
            }
        }
    }

    @Test
    fun `saksbehandler skal kunne hente neste planlagte kontrollsamtale`() {
        val kontrollsamtaleMock = mock<KontrollsamtaleService> {
            on { hentNestePlanlagteKontrollsamtale(any(), anyOrNull()) } doReturn kontrollsamtale().right()
            on { defaultSessionContext() } doReturn TestSessionFactory.sessionContext
        }
        testApplication {
            application {
                testSusebakover(
                    services = TestServicesBuilder.services(kontrollsamtaleService = kontrollsamtaleMock),
                )
            }
            defaultRequest(
                HttpMethod.Get,
                "/kontrollsamtale/hent/${UUID.randomUUID()}",
                listOf(Brukerrolle.Saksbehandler),
            ).apply {
                status shouldBe HttpStatusCode.OK
            }
        }
    }

    @Test
    fun `hent neste planlagte kontrollsamtale skal returnere 'null' om man ikke finner noen planlagte`() {
        val kontrollsamtaleMock = mock<KontrollsamtaleService> {
            on {
                hentNestePlanlagteKontrollsamtale(
                    any(),
                    anyOrNull(),
                )
            } doReturn KunneIkkeHenteKontrollsamtale.FantIkkeKontrollsamtale.left()
            on { defaultSessionContext() } doReturn TestSessionFactory.sessionContext
        }
        testApplication {
            application {
                testSusebakover(
                    services = TestServicesBuilder.services(kontrollsamtaleService = kontrollsamtaleMock),
                )
            }
            defaultRequest(
                HttpMethod.Get,
                "/kontrollsamtale/hent/${UUID.randomUUID()}",
                listOf(Brukerrolle.Saksbehandler),
            ).apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe "null"
            }
        }
    }

    @Test
    fun `hent neste planlagte kontrollsamtale skal feile ved andre feil`() {
        val kontrollsamtaleMock = mock<KontrollsamtaleService> {
            on {
                hentNestePlanlagteKontrollsamtale(
                    any(),
                    anyOrNull(),
                )
            } doReturn KunneIkkeHenteKontrollsamtale.KunneIkkeHenteKontrollsamtaler.left()
        }
        testApplication {
            application {
                testSusebakover(
                    services = TestServicesBuilder.services(kontrollsamtaleService = kontrollsamtaleMock),
                )
            }
            defaultRequest(
                HttpMethod.Get,
                "/kontrollsamtale/hent/${UUID.randomUUID()}",
                listOf(Brukerrolle.Saksbehandler),
            ).apply {
                status shouldBe HttpStatusCode.InternalServerError
            }
        }
    }
}
