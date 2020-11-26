package no.nav.su.se.bakover.web.routes.utbetaling.stans

import arrow.core.left
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.service.Services
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeStanseUtbetalinger
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import java.util.UUID

internal class StansUtbetalingRoutesKtTest {

    private val fnr = Fnr("12345678911")
    private val sakId: UUID = UUID.randomUUID()
    private val saksnummer = Math.random().toLong()
    private val tidspunkt = Tidspunkt.EPOCH
    private val sak: Sak = Sak(
        id = sakId,
        saksnummer = Saksnummer(saksnummer),
        opprettet = tidspunkt,
        fnr = fnr,
        oppdrag = Oppdrag(
            id = UUID30.randomUUID(),
            opprettet = tidspunkt,
            sakId = sakId
        )
    )
    private val services = Services(
        avstemming = mock(),
        utbetaling = mock(),
        oppdrag = mock(),
        behandling = mock(),
        sak = mock(),
        søknad = mock(),
        brev = mock(),
        lukkSøknad = mock(),
        oppgave = mock()
    )

    @Test
    fun `fant ikke sak returnerer not found`() {
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { stansUtbetalinger(any(), any()) } doReturn KunneIkkeStanseUtbetalinger.FantIkkeSak.left()
        }
        withTestApplication({
            testSusebakover(services = services.copy(utbetaling = utbetalingServiceMock))
        }) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/$sakId/utbetalinger/stans",
                listOf(Brukerrolle.Saksbehandler)
            ) {
            }.apply {
                response.status() shouldBe HttpStatusCode.NotFound
                response.content shouldContain "Fant ikke sak"
            }
        }
    }

    @Test
    fun `simulering av stans feiler svarer med 500`() {
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { stansUtbetalinger(any(), any()) } doReturn KunneIkkeStanseUtbetalinger.SimuleringAvStansFeilet.left()
        }
        withTestApplication({
            testSusebakover(services = services.copy(utbetaling = utbetalingServiceMock))
        }) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/$sakId/utbetalinger/stans",
                listOf(Brukerrolle.Saksbehandler)
            ) {
            }.apply {
                response.status() shouldBe HttpStatusCode.InternalServerError
                response.content shouldContain "Simulering av stans feilet"
            }
        }
    }

    @Test
    fun `oversendelse til utbetaling svarer med 500`() {
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { stansUtbetalinger(any(), any()) } doReturn KunneIkkeStanseUtbetalinger.SendingAvUtebetalingTilOppdragFeilet.left()
        }
        withTestApplication({
            testSusebakover(services = services.copy(utbetaling = utbetalingServiceMock))
        }) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/$sakId/utbetalinger/stans",
                listOf(Brukerrolle.Saksbehandler)
            ) {
            }.apply {
                response.status() shouldBe HttpStatusCode.InternalServerError
                response.content shouldContain "Oversendelse til oppdrag feilet"
            }
        }
    }

    @Test
    fun `simulert stans inneholder beløp ulikt 0 svarer med 500`() {
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { stansUtbetalinger(any(), any()) } doReturn KunneIkkeStanseUtbetalinger.SimulertStansHarBeløpUlikt0.left()
        }
        withTestApplication({
            testSusebakover(services = services.copy(utbetaling = utbetalingServiceMock))
        }) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/$sakId/utbetalinger/stans",
                listOf(Brukerrolle.Saksbehandler)
            ) {
            }.apply {
                response.status() shouldBe HttpStatusCode.InternalServerError
                response.content shouldContain "beløp for utbetaling ulikt 0"
            }
        }
    }
}
