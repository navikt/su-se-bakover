package no.nav.su.se.bakover.web.routes.nøkkeltall

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.domain.nøkkeltall.Nøkkeltall
import no.nav.su.se.bakover.service.nøkkeltall.NøkkeltallService
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert

internal class NøkkeltallRoutesKtTest {
    @Test
    fun `må være innlogget for å få nøkkeltall`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            client.get(NØKKELTALL_PATH).apply {
                status shouldBe HttpStatusCode.Unauthorized
            }
        }
    }

    @Test
    fun `saksbehandler kan hente nøkkeltall`() {
        val nøkkelServiceMock = mock<NøkkeltallService> {
            on { hentNøkkeltall() } doReturn Nøkkeltall(
                søknader = Nøkkeltall.Søknader(
                    totaltAntall = 2,
                    iverksatteAvslag = 1,
                    iverksatteInnvilget = 0,
                    ikkePåbegynt = 0,
                    påbegynt = 1,
                    lukket = 0,
                    digitalsøknader = 0,
                    papirsøknader = 0,
                ),
                antallUnikePersoner = 1,
                løpendeSaker = 0,
            )
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services(nøkkeltallService = nøkkelServiceMock))
            }
            defaultRequest(HttpMethod.Get, NØKKELTALL_PATH, listOf(Brukerrolle.Saksbehandler)).apply {
                val expected = """
                {
                    "søknader": {
                        "totaltAntall": 2,
                        "iverksatteAvslag": 1,
                        "iverksatteInnvilget": 0,
                        "ikkePåbegynt": 0,
                        "påbegynt": 1,
                        "lukket": 0,
                        "digitalsøknader": 0,
                        "papirsøknader": 0
                    },
                    "antallUnikePersoner": 1,
                    "løpendeSaker": 0
                }
                """.trimIndent()
                val actual = bodyAsText()

                status shouldBe HttpStatusCode.OK
                JSONAssert.assertEquals(expected, actual, true)
            }
        }
    }
}
