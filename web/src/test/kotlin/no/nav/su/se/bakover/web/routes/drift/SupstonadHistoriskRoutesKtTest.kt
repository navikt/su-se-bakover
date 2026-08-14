package no.nav.su.se.bakover.web.routes.drift

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.client.historisk.CountResponse
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.service.historisk.SupstonadHistoriskService
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert

internal class SupstonadHistoriskRoutesKtTest {

    private val uri = "$DRIFT_PATH/supstonadhistorisk/tellrader"
    private val requestBody = """{ "tabellnavn": "sak" }"""

    @Test
    fun `Kun Drift har tilgang til tellrader-endepunktet`() {
        Brukerrolle.entries.filterNot { it == Brukerrolle.Drift }.forEach {
            testApplication {
                application {
                    testSusebakoverWithMockedDb(services = TestServicesBuilder.services())
                }
                defaultRequest(
                    method = HttpMethod.Post,
                    uri = uri,
                    roller = listOf(it),

                ) { setBody(requestBody) }.apply {
                    status shouldBe HttpStatusCode.Forbidden
                }
            }
        }
    }

    @Test
    fun `tellrader gir OK med antall rader`() {
        val supstonadHistoriskService = mock<SupstonadHistoriskService> {
            on { tellRader(any()) } doReturn CountResponse(antall = 42).right()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        supstonadHistoriskService = supstonadHistoriskService,
                    ),
                )
            }
            defaultRequest(
                method = HttpMethod.Post,
                uri = uri,
                roller = listOf(Brukerrolle.Drift),
            ) { setBody(requestBody) }.apply {
                status shouldBe HttpStatusCode.OK
                JSONAssert.assertEquals(
                    """{ "antall": 42 }""",
                    this.bodyAsText(),
                    true,
                )
            }
        }
    }

    @Test
    fun `tellrader gir feilstatus og feilmelding når klienten feiler`() {
        val supstonadHistoriskService = mock<SupstonadHistoriskService> {
            on { tellRader(any()) } doReturn ClientError(
                httpStatus = 502,
                message = "Klarte ikke å nå supstonad-historisk",
            ).left()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        supstonadHistoriskService = supstonadHistoriskService,
                    ),
                )
            }
            defaultRequest(
                method = HttpMethod.Post,
                uri = uri,
                roller = listOf(Brukerrolle.Drift),
            ) { setBody(requestBody) }.apply {
                status shouldBe HttpStatusCode.BadGateway
                JSONAssert.assertEquals(
                    """{ "message": "Klarte ikke å nå supstonad-historisk", "code": "supstonad_historisk_feil" }""",
                    this.bodyAsText(),
                    true,
                )
            }
        }
    }
}
