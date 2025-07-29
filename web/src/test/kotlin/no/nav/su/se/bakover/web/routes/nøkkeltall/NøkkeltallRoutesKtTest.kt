package no.nav.su.se.bakover.web.routes.nøkkeltall

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.service.nøkkeltall.NøkkeltallService
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import nøkkeltall.domain.Nøkkeltall
import nøkkeltall.domain.NøkkeltallPerSakstype
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
        val testdata = listOf(
            NøkkeltallPerSakstype(
                sakstype = Sakstype.UFØRE,
                Nøkkeltall(
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
                ),
            ),
        )
        val nøkkelServiceMock = mock<NøkkeltallService> {
            on { hentNøkkeltallSakstyper() } doReturn testdata
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services(nøkkeltallService = nøkkelServiceMock))
            }
            defaultRequest(HttpMethod.Get, NØKKELTALL_PATH, listOf(Brukerrolle.Saksbehandler)).apply {
                val expected = serialize(testdata.toJson())

                val actual = bodyAsText()

                status shouldBe HttpStatusCode.OK
                JSONAssert.assertEquals(expected, actual, true)
            }
        }
    }
}
