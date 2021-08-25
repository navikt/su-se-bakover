package no.nav.su.se.bakover.web.routes.utbetaling.stans

import arrow.core.left
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeStanseUtbetalinger
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

internal class StansUtbetalingRoutesKtTest {

    private val sakId: UUID = UUID.randomUUID()
    private val services = TestServicesBuilder.services()

    @Test
    fun `fant ikke sak returnerer not found`() {
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { stansUtbetalinger(any(), any(), any()) } doReturn KunneIkkeStanseUtbetalinger.FantIkkeSak.left()
        }
        withTestApplication(
            {
                testSusebakover(services = services.copy(utbetaling = utbetalingServiceMock))
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/$sakId/utbetalinger/stans",
                listOf(Brukerrolle.Saksbehandler),
            ).apply {
                response.status() shouldBe HttpStatusCode.NotFound
                response.content shouldContain "Fant ikke sak"
            }
        }
    }

    @Test
    fun `simulering av stans feiler svarer med 500`() {
        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                stansUtbetalinger(
                    any(),
                    any(),
                    any(),
                )
            } doReturn KunneIkkeStanseUtbetalinger.SimuleringAvStansFeilet.left()
        }
        withTestApplication(
            {
                testSusebakover(services = services.copy(utbetaling = utbetalingServiceMock))
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/$sakId/utbetalinger/stans",
                listOf(Brukerrolle.Saksbehandler),
            ).apply {
                response.status() shouldBe HttpStatusCode.InternalServerError
                response.content shouldContain "Simulering av stans feilet"
            }
        }
    }

    @Test
    fun `oversendelse til utbetaling svarer med 500`() {
        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                stansUtbetalinger(
                    any(),
                    any(),
                    any(),
                )
            } doReturn KunneIkkeStanseUtbetalinger.SendingAvUtbetalingTilOppdragFeilet.left()
        }
        withTestApplication(
            {
                testSusebakover(services = services.copy(utbetaling = utbetalingServiceMock))
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/$sakId/utbetalinger/stans",
                listOf(Brukerrolle.Saksbehandler),
            ).apply {
                response.status() shouldBe HttpStatusCode.InternalServerError
                response.content shouldContain "Oversendelse til oppdrag feilet"
            }
        }
    }

    @Test
    fun `simulert stans inneholder beløp ulikt 0 svarer med 500`() {
        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                stansUtbetalinger(
                    any(),
                    any(),
                    any(),
                )
            } doReturn KunneIkkeStanseUtbetalinger.KontrollAvSimuleringFeilet.left()
        }
        withTestApplication(
            {
                testSusebakover(services = services.copy(utbetaling = utbetalingServiceMock))
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/$sakId/utbetalinger/stans",
                listOf(Brukerrolle.Saksbehandler),
            ).apply {
                response.status() shouldBe HttpStatusCode.InternalServerError
                response.content shouldContain "Kontroll av simulering feilet"
            }
        }
    }
}
