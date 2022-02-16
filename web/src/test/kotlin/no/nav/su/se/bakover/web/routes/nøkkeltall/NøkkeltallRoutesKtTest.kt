package no.nav.su.se.bakover.web.routes.nøkkeltall

import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.domain.bruker.Brukerrolle
import no.nav.su.se.bakover.domain.nøkkeltall.Nøkkeltall
import no.nav.su.se.bakover.service.nøkkeltall.NøkkeltallService
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert

internal class NøkkeltallRoutesKtTest {
    @Test
    fun `må være innlogget for å få nøkkeltall`() {
        withTestApplication(
            {
                testSusebakover()
            },
        ) {
            handleRequest(HttpMethod.Get, nøkkeltallPath)
        }.apply {
            response.status() shouldBe HttpStatusCode.Unauthorized
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

        withTestApplication(
            {
                testSusebakover(services = TestServicesBuilder.services().copy(nøkkeltallService = nøkkelServiceMock))
            },
        ) {
            defaultRequest(HttpMethod.Get, nøkkeltallPath, listOf(Brukerrolle.Saksbehandler))
        }.apply {
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
            val actual = response.content

            response.status() shouldBe HttpStatusCode.OK
            JSONAssert.assertEquals(expected, actual, true)
        }
    }
}
