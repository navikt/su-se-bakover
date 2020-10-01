package no.nav.su.se.bakover.web.routes.utbetaling.stans

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
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
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.DatabaseRepos
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.service.utbetaling.StansUtbetalingService
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.behandling.UtbetalingJson
import no.nav.su.se.bakover.web.routes.behandling.UtbetalingJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

internal class StansUtbetalingRoutesKtTest {

    val fnr = Fnr("12345678911")
    val sakId = UUID.randomUUID()
    val tidspunkt = Instant.EPOCH
    val sak = Sak(
        id = sakId,
        opprettet = Tidspunkt.EPOCH,
        fnr = fnr,
        oppdrag = Oppdrag(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            sakId = sakId
        )
    )
    val utbetalingId = UUID30.fromString("423fed12-1324-4be6-a8c7-1ee7e4")
    val nyUtbetaling = Utbetaling(
        id = utbetalingId,
        opprettet = Tidspunkt.EPOCH,
        simulering = null,
        kvittering = null,
        oppdragsmelding = null,
        utbetalingslinjer = listOf(),
        avstemmingId = null,
        fnr = fnr
    )

    @Test
    fun `stans utbetaling feiler`() {
        val sakRepoMock = mock<SakRepo> {
            on { hentSak(sakId) } doReturn sak
        }
        val stansutbetalingServiceMock = mock<StansUtbetalingService> {
            on { stansUtbetalinger(any()) } doReturn StansUtbetalingService.KunneIkkeStanseUtbetalinger.left()
        }
        withTestApplication({
            val services = no.nav.su.se.bakover.service.ServiceBuilder(
                databaseRepos = DatabaseRepos(
                    objectRepo = mock(),
                    avstemmingRepo = mock(),
                    utbetalingRepo = mock(),
                    oppdragRepo = mock(),
                    søknadRepo = mock(),
                    behandlingRepo = mock(),
                    hendelsesloggRepo = mock(),
                    beregningRepo = mock(),
                    sakRepo = sakRepoMock,
                ),
                clients = TestClientsBuilder.build()
            ).build()
            testSusebakover(
                services = services.copy(
                    stansUtbetalingService = stansutbetalingServiceMock
                )
            )
        }) {
            defaultRequest(HttpMethod.Post, "$sakPath/$sakId/utbetalinger/stans") {
            }.apply {
                response.status() shouldBe HttpStatusCode.InternalServerError
                response.content shouldContain "Kunne ikke stanse utbetalinger for sak med id $sakId"
            }
        }
    }

    @Test
    fun `stans utbetalinger`() {
        val sakRepoMock = mock<SakRepo> {
            on { hentSak(sakId) } doReturn sak
        }

        val stansUtbetalingServiceMock = mock<StansUtbetalingService> {
            on { stansUtbetalinger(any()) } doReturn nyUtbetaling.right()
        }

        withTestApplication({
            val services = no.nav.su.se.bakover.service.ServiceBuilder(
                databaseRepos = DatabaseRepos(
                    objectRepo = mock(),
                    avstemmingRepo = mock(),
                    utbetalingRepo = mock(),
                    oppdragRepo = mock(),
                    søknadRepo = mock(),
                    behandlingRepo = mock(),
                    hendelsesloggRepo = mock(),
                    beregningRepo = mock(),
                    sakRepo = sakRepoMock,
                ),
                clients = TestClientsBuilder.build()
            ).build()
            testSusebakover(
                services = services.copy(
                    stansUtbetalingService = stansUtbetalingServiceMock
                )
            )
        }) {
            defaultRequest(HttpMethod.Post, "$sakPath/$sakId/utbetalinger/stans") {
            }.apply {
                response.status() shouldBe HttpStatusCode.OK
                objectMapper.readValue<UtbetalingJson>(response.content!!) shouldBe nyUtbetaling.toJson()
            }
        }
    }
}
