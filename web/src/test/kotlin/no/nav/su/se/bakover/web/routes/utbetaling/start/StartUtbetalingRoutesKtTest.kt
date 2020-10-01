package no.nav.su.se.bakover.web.routes.utbetaling.start

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.service.Services
import no.nav.su.se.bakover.service.utbetaling.StartUtbetalingFeilet
import no.nav.su.se.bakover.service.utbetaling.StartUtbetalingerService
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.behandling.UtbetalingJson
import no.nav.su.se.bakover.web.routes.behandling.UtbetalingJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import java.util.UUID

internal class StartUtbetalingRoutesKtTest {

    val sakId = UUID.randomUUID()

    @Test
    fun `Fant ikke sak returnerer not found`() {
        val utbetalingServiceMock = mock<StartUtbetalingerService> {
            on { startUtbetalinger(sakId) } doReturn StartUtbetalingFeilet.FantIkkeSak.left()
        }
        withTestApplication({ testSusebakover(services = Services(mock(), utbetalingServiceMock)) }) {
            defaultRequest(HttpMethod.Post, "$sakPath/$sakId/utbetalinger/start") {
            }.apply {
                response.status() shouldBe HttpStatusCode.NotFound
                response.content shouldContain "Fant ikke sak"
            }
        }
    }

    @Test
    fun `Har ingen oversendte utbetalinger returnerer bad request`() {
        val utbetalingServiceMock = mock<StartUtbetalingerService> {
            on { startUtbetalinger(sakId) } doReturn StartUtbetalingFeilet.HarIngenOversendteUtbetalinger.left()
        }
        withTestApplication({ testSusebakover(services = Services(mock(), utbetalingServiceMock)) }) {
            defaultRequest(HttpMethod.Post, "$sakPath/$sakId/utbetalinger/start") {
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "Ingen utbetalinger"
            }
        }
    }

    @Test
    fun `Siste utbetaling er ikke en stansbetaling returnerer bad request`() {
        val utbetalingServiceMock = mock<StartUtbetalingerService> {
            on { startUtbetalinger(sakId) } doReturn StartUtbetalingFeilet.SisteUtbetalingErIkkeEnStansutbetaling.left()
        }
        withTestApplication({ testSusebakover(services = Services(mock(), utbetalingServiceMock)) }) {
            defaultRequest(HttpMethod.Post, "$sakPath/$sakId/utbetalinger/start") {
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "Siste utbetaling er ikke en stans"
            }
        }
    }

    @Test
    fun `Simulering av start utbetaling returnerer internal server error`() {
        val utbetalingServiceMock = mock<StartUtbetalingerService> {
            on { startUtbetalinger(sakId) } doReturn StartUtbetalingFeilet.SimuleringAvStartutbetalingFeilet.left()
        }
        withTestApplication({ testSusebakover(services = Services(mock(), utbetalingServiceMock)) }) {
            defaultRequest(HttpMethod.Post, "$sakPath/$sakId/utbetalinger/start") {
            }.apply {
                response.status() shouldBe HttpStatusCode.InternalServerError
                response.content shouldContain "Simulering feilet"
            }
        }
    }

    @Test
    fun `Sending av utbetaling til oppdrag returnerer internal server error`() {
        val utbetalingServiceMock = mock<StartUtbetalingerService> {
            on { startUtbetalinger(sakId) } doReturn StartUtbetalingFeilet.SendingAvUtebetalingTilOppdragFeilet.left()
        }
        withTestApplication({ testSusebakover(services = Services(mock(), utbetalingServiceMock)) }) {
            defaultRequest(HttpMethod.Post, "$sakPath/$sakId/utbetalinger/start") {
            }.apply {
                response.status() shouldBe HttpStatusCode.InternalServerError
                response.content shouldContain "Oversendelse til oppdrag feilet"
            }
        }
    }

    @Test
    fun `Starter utbetalinger OK`() {
        val sakId = UUID.randomUUID()
        val utbetaling = Utbetaling(
            id = UUID30.fromString("423fed12-1324-4be6-a8c7-1ee7e4"),
            opprettet = Tidspunkt.EPOCH,
            simulering = null,
            kvittering = null,
            oppdragsmelding = null,
            utbetalingslinjer = listOf(),
            avstemmingId = null,
            fnr = Fnr("12345678911")
        )
        val utbetalingServiceMock = mock<StartUtbetalingerService> {
            on { startUtbetalinger(sakId) } doReturn utbetaling.right()
        }
        withTestApplication({ testSusebakover(services = Services(mock(), utbetalingServiceMock)) }) {
            defaultRequest(HttpMethod.Post, "$sakPath/$sakId/utbetalinger/start") {
            }.apply {
                response.status() shouldBe HttpStatusCode.OK
                objectMapper.readValue<UtbetalingJson>(response.content!!) shouldBe utbetaling.toJson()
            }
        }
    }
}
