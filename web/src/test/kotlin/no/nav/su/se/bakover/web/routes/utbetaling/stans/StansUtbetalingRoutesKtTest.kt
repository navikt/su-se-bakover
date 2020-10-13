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
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.service.utbetaling.StansUtbetalingService
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.SakJson
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import java.util.UUID

internal class StansUtbetalingRoutesKtTest {

    private val fnr = Fnr("12345678911")
    private val sakId: UUID = UUID.randomUUID()
    private val tidspunkt = Tidspunkt.EPOCH
    private val sak: Sak = Sak(
        id = sakId,
        opprettet = tidspunkt,
        fnr = fnr,
        oppdrag = Oppdrag(
            id = UUID30.randomUUID(),
            opprettet = tidspunkt,
            sakId = sakId
        )
    )

    @Test
    fun `stans utbetaling feiler`() {
        val sakRepoMock = mock<SakRepo> {
            on { hentSak(sakId) } doReturn sak
        }
        val stansutbetalingServiceMock = mock<StansUtbetalingService> {
            on { stansUtbetalinger(any(), any()) } doReturn StansUtbetalingService.KunneIkkeStanseUtbetalinger.left()
        }
        withTestApplication({
            val services = no.nav.su.se.bakover.service.ServiceBuilder(
                databaseRepos = DatabaseRepos(
                    avstemming = mock(),
                    utbetaling = mock(),
                    oppdrag = mock(),
                    søknad = mock(),
                    behandling = mock(),
                    hendelseslogg = mock(),
                    beregning = mock(),
                    sak = sakRepoMock,
                ),
                clients = TestClientsBuilder.build()
            ).build()
            testSusebakover(
                services = services.copy(
                    stansUtbetaling = stansutbetalingServiceMock
                )
            )
        }) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/$sakId/utbetalinger/stans",
                listOf(Brukerrolle.Saksbehandler)
            ) {
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
            on { stansUtbetalinger(any(), any()) } doReturn sak.right()
        }

        withTestApplication({
            val services = no.nav.su.se.bakover.service.ServiceBuilder(
                databaseRepos = DatabaseRepos(
                    avstemming = mock(),
                    utbetaling = mock(),
                    oppdrag = mock(),
                    søknad = mock(),
                    behandling = mock(),
                    hendelseslogg = mock(),
                    beregning = mock(),
                    sak = sakRepoMock,
                ),
                clients = TestClientsBuilder.build()
            ).build()
            testSusebakover(
                services = services.copy(
                    stansUtbetaling = stansUtbetalingServiceMock
                )
            )
        }) {
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/$sakId/utbetalinger/stans",
                listOf(Brukerrolle.Saksbehandler)
            ) {
            }.apply {
                response.status() shouldBe HttpStatusCode.OK
                objectMapper.readValue<SakJson>(response.content!!) shouldBe sak.toJson()
            }
        }
    }
}
