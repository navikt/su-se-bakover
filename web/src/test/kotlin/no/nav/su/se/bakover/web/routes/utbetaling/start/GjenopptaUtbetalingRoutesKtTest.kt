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
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.service.Services
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeGjenopptaUtbetalinger
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.argThat
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.SakJson
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class GjenopptaUtbetalingRoutesKtTest {

    private val sakId = UUID.randomUUID()
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
    private val saksbehandler = NavIdentBruker.Saksbehandler("navident")

    @Test
    fun `Fant ikke sak returnerer not found`() {
        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                gjenopptaUtbetalinger(
                    argThat {
                        it shouldBe sakId
                    },
                    argThat {
                        it shouldBe saksbehandler
                    }
                )
            } doReturn KunneIkkeGjenopptaUtbetalinger.FantIkkeSak.left()
        }
        withTestApplication({
            testSusebakover(
                services = services.copy(
                    utbetaling = utbetalingServiceMock
                )
            )
        }) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/$sakId/utbetalinger/gjenoppta",
                listOf(Brukerrolle.Saksbehandler)
            ) {
            }.apply {
                response.status() shouldBe HttpStatusCode.NotFound
                response.content shouldContain "Fant ikke sak"
            }
        }
    }

    @Test
    fun `Har ingen oversendte utbetalinger returnerer bad request`() {
        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                gjenopptaUtbetalinger(
                    sakId,
                    saksbehandler
                )
            } doReturn KunneIkkeGjenopptaUtbetalinger.HarIngenOversendteUtbetalinger.left()
        }
        withTestApplication({
            testSusebakover(
                services = services.copy(
                    utbetaling = utbetalingServiceMock
                )
            )
        }) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/$sakId/utbetalinger/gjenoppta",
                listOf(Brukerrolle.Saksbehandler)
            ) {
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "Ingen utbetalinger"
            }
        }
    }

    @Test
    fun `Siste utbetaling er ikke en stansbetaling returnerer bad request`() {
        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                gjenopptaUtbetalinger(
                    sakId,
                    saksbehandler
                )
            } doReturn KunneIkkeGjenopptaUtbetalinger.SisteUtbetalingErIkkeEnStansutbetaling.left()
        }
        withTestApplication({
            testSusebakover(
                services = services.copy(
                    utbetaling = utbetalingServiceMock
                )
            )
        }) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/$sakId/utbetalinger/gjenoppta",
                listOf(Brukerrolle.Saksbehandler)
            ) {
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "Siste utbetaling er ikke en stans"
            }
        }
    }

    @Test
    fun `Simulering av gjenoppta utbetaling returnerer internal server error`() {
        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                gjenopptaUtbetalinger(
                    sakId,
                    saksbehandler
                )
            } doReturn KunneIkkeGjenopptaUtbetalinger.SimuleringAvStartutbetalingFeilet.left()
        }
        withTestApplication({
            testSusebakover(
                services = services.copy(
                    utbetaling = utbetalingServiceMock
                )
            )
        }) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/$sakId/utbetalinger/gjenoppta",
                listOf(Brukerrolle.Saksbehandler)
            ) {
            }.apply {
                response.status() shouldBe HttpStatusCode.InternalServerError
                response.content shouldContain "Simulering feilet"
            }
        }
    }

    @Test
    fun `Sending av utbetaling til oppdrag returnerer internal server error`() {
        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                gjenopptaUtbetalinger(
                    sakId,
                    saksbehandler
                )
            } doReturn KunneIkkeGjenopptaUtbetalinger.SendingAvUtebetalingTilOppdragFeilet.left()
        }
        withTestApplication({
            testSusebakover(
                services = services.copy(
                    utbetaling = utbetalingServiceMock
                )
            )
        }) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/$sakId/utbetalinger/gjenoppta",
                listOf(Brukerrolle.Saksbehandler)
            ) {
            }.apply {
                response.status() shouldBe HttpStatusCode.InternalServerError
                response.content shouldContain "Oversendelse til oppdrag feilet"
            }
        }
    }

    @Test
    fun `Starter utbetalinger OK`() {
        val sakId = UUID.randomUUID()
        val utbetaling = Utbetaling.OversendtUtbetaling.UtenKvittering(
            id = UUID30.fromString("423fed12-1324-4be6-a8c7-1ee7e4"),
            opprettet = Tidspunkt.EPOCH,
            utbetalingslinjer = listOf(),
            fnr = Fnr("12345678911"),
            type = Utbetaling.UtbetalingsType.GJENOPPTA,
            oppdragId = UUID30.randomUUID(),
            behandler = NavIdentBruker.Attestant("Z123"),
            avstemmingsnøkkel = Avstemmingsnøkkel(),
            simulering = Simulering(
                gjelderId = FnrGenerator.random(),
                gjelderNavn = "",
                datoBeregnet = LocalDate.now(),
                nettoBeløp = 0.0,
                periodeList = listOf()
            ),
            utbetalingsrequest = Utbetalingsrequest("")
        )
        val sak = Sak(
            fnr = FnrGenerator.random(),
            oppdrag = Oppdrag(
                id = UUID30.randomUUID(),
                opprettet = Tidspunkt.now(),
                sakId = sakId,
                utbetalinger = listOf(utbetaling)
            )
        )
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { gjenopptaUtbetalinger(sakId, saksbehandler) } doReturn sak.right()
        }
        withTestApplication({
            testSusebakover(
                services = services.copy(
                    utbetaling = utbetalingServiceMock
                )
            )
        }) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/$sakId/utbetalinger/gjenoppta",
                listOf(Brukerrolle.Saksbehandler)
            ) {
            }.apply {
                response.status() shouldBe HttpStatusCode.OK
                objectMapper.readValue<SakJson>(response.content!!) shouldBe sak.toJson()
            }
        }
    }
}
