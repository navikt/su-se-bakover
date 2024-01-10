package økonomi.presentation.api

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.test.application.defaultRequest
import no.nav.su.se.bakover.test.application.runApplicationWithMocks
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert
import økonomi.application.utbetaling.KunneIkkeSendeUtbetalingPåNytt
import økonomi.application.utbetaling.ResendUtbetalingService

class ØkonomiRoutesKtTest {

    @Test
    fun `aksepterer, validerer, og responderer uten feil for sending av ny utbetaling`() {
        val failedUtbetaling = UUID30.randomUUID()
        val successUtbetaling = UUID30.randomUUID()
        val resendUtbetalingService = mock<ResendUtbetalingService> {
            on { this.resendUtbetalinger(any()) } doReturn listOf(
                KunneIkkeSendeUtbetalingPåNytt.FantIkkeUtbetalingPåSak(failedUtbetaling).left(),
                successUtbetaling.right(),
            )
        }
        val expectedJsonResponse = """
            {
              "success": [{"utbetalingId": "$successUtbetaling"}],
              "failed": [{"utbetalingId": "$failedUtbetaling", "feilmelding": "$failedUtbetaling - Fant ikke utbetaling på sak"}]
            }
        """.trimIndent()

        testApplication {
            application {
                runApplicationWithMocks(
                    resendUtbetalingService = resendUtbetalingService,
                )
            }
            defaultRequest(
                method = HttpMethod.Post,
                uri = "/okonomi/utbetalingslinjer",
                roller = listOf(Brukerrolle.Drift),
                client = this.client,
            ) {
                setBody(
                    """{"utbetalingslinjer": "$successUtbetaling, $failedUtbetaling"}""",
                )
            }.apply {
                status shouldBe HttpStatusCode.OK
                JSONAssert.assertEquals(
                    expectedJsonResponse,
                    bodyAsText(),
                    true,
                )
            }
        }
    }
}
