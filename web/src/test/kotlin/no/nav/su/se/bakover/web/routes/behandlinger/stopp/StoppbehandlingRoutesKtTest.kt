package no.nav.su.se.bakover.web.routes.behandlinger.stopp

import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.DatabaseRepos
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.behandlinger.stopp.Stoppbehandling
import no.nav.su.se.bakover.domain.behandlinger.stopp.StoppbehandlingService
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.behandling.UtbetalingJson
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import java.time.format.DateTimeFormatter
import java.util.UUID

internal class StoppbehandlingRoutesKtTest {

    val fnr = Fnr("12345678911")

    @Test
    fun `stopp utbetalinger`() {
        val sakId = UUID.randomUUID()

        val stoppÅrsak = "Årsaken til stoppen."
        val stoppbehandling = Stoppbehandling.Simulert(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            sakId = sakId,
            utbetaling = Utbetaling(
                id = UUID30.randomUUID(),
                opprettet = Tidspunkt.EPOCH,
                simulering = null,
                kvittering = null,
                oppdragsmelding = null,
                utbetalingslinjer = listOf(),
                avstemmingId = null,
                fnr = fnr
            ),
            stoppÅrsak = stoppÅrsak,
            saksbehandler = Saksbehandler(id = "saksbehandler")
        )

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
        val stoppbehandlingServiceMock = mock<StoppbehandlingService> {
            on { stoppUtbetalinger(any(), any(), any()) } doReturn stoppbehandling.right()
        }
        val objectRepoMock = mock<ObjectRepo> {
            on { hentSak(sakId) } doReturn sak
        }

        withTestApplication({

            testSusebakover(
                databaseRepos = DatabaseRepos(objectRepoMock, mock()),
                stoppbehandlingService = stoppbehandlingServiceMock
            )
        }) {
            defaultRequest(HttpMethod.Post, "$sakPath/$sakId/utbetalinger/stopp") {
                setBody("""{ "stoppÅrsak": "$stoppÅrsak" }""")
            }.apply {
                response.status() shouldBe HttpStatusCode.OK
                objectMapper.readValue<StoppbehandlingJson>(response.content!!) shouldBe StoppbehandlingJson(
                    id = stoppbehandling.id.toString(),
                    opprettet = stoppbehandling.opprettet,
                    sakId = sakId.toString(),
                    status = stoppbehandling.status,
                    utbetaling = UtbetalingJson(
                        id = stoppbehandling.utbetaling.id.toString(),
                        opprettet = DateTimeFormatter.ISO_INSTANT.format(stoppbehandling.utbetaling.opprettet),
                        simulering = null
                    ),
                    stoppÅrsak = stoppÅrsak,
                    saksbehandler = "saksbehandler"
                )
            }
        }
    }
}
