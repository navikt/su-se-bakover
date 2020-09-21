package no.nav.su.se.bakover.web.routes.sak

import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.DatabaseRepos
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.ObjectRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandlinger.stopp.Stoppbehandling
import no.nav.su.se.bakover.domain.behandlinger.stopp.StoppbehandlingRepo
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.behandling.BehandlingJson
import no.nav.su.se.bakover.web.routes.behandllinger.stopp.StoppbehandlingJson
import no.nav.su.se.bakover.web.testSusebakover
import org.json.JSONObject
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

internal class SakRoutesKtTest {

    private val sakFnr01 = "12345678911"
    private val sakRepo: ObjectRepo by lazy {
        DatabaseBuilder.build(EmbeddedDatabase.instance()).objectRepo
    }

    @Test
    fun `henter sak for sak id`() {
        withTestApplication(
            (
                {
                    testSusebakover()
                }
                )
        ) {
            val opprettetSakId = sakRepo.opprettSak(Fnr(sakFnr01)).id

            defaultRequest(Get, "$sakPath/$opprettetSakId").apply {
                assertEquals(OK, response.status())
                assertEquals(sakFnr01, JSONObject(response.content).getString("fnr"))
            }
        }
    }

    @Test
    fun `henter sak for fødselsnummer`() {
        withTestApplication(
            (
                {
                    testSusebakover()
                }
                )
        ) {
            sakRepo.opprettSak(Fnr(sakFnr01))

            defaultRequest(Get, "$sakPath/?fnr=$sakFnr01").apply {
                assertEquals(OK, response.status())
                assertEquals(sakFnr01, JSONObject(response.content).getString("fnr"))
            }
        }
    }

    @Test
    fun `error handling`() {
        withTestApplication(
            (
                {
                    testSusebakover()
                }
                )
        ) {
            defaultRequest(Get, sakPath).apply {
                assertEquals(BadRequest, response.status(), "$sakPath gir 400 ved manglende fnr")
            }

            defaultRequest(Get, "$sakPath?fnr=12341234123").apply {
                assertEquals(NotFound, response.status(), "$sakPath?fnr= gir 404 ved ukjent fnr")
            }

            defaultRequest(Get, "$sakPath/${UUID.randomUUID()}").apply {
                assertEquals(NotFound, response.status(), "$sakPath/UUID gir 404 ved ikke-eksisterende sak-ID")
            }

            defaultRequest(Get, "$sakPath/adad").apply {
                assertEquals(BadRequest, response.status(), "$sakPath/UUID gir 400 ved ugyldig UUID")
            }
        }
    }

    @Test
    fun `kan opprette behandling på en sak og søknad`() {
        val nySak = sakRepo.opprettSak(Fnr(sakFnr01))
        val nySøknad = nySak.nySøknad(SøknadInnholdTestdataBuilder.build())

        withTestApplication({
            testSusebakover()
        }) {
            defaultRequest(HttpMethod.Post, "$sakPath/${nySak.id}/behandlinger") {
                setBody("""{ "soknadId": "${nySøknad.id}" }""")
            }.apply {
                response.status() shouldBe HttpStatusCode.Created
                val behandling = objectMapper.readValue<BehandlingJson>(response.content!!)
                behandling.søknad.id shouldBe nySøknad.id.toString()
            }
        }
    }

    @Test
    fun `stopp utbetalinger`() {
        val sakId = UUID.randomUUID()

        val stoppbehandling = Stoppbehandling.Simulert(
            id = UUID.randomUUID(),
            opprettet = Instant.EPOCH,
            sakId = sakId,
            utbetaling = Utbetaling(
                id = UUID30.randomUUID(),
                opprettet = Instant.EPOCH,
                simulering = null,
                kvittering = null,
                oppdragsmelding = null,
                utbetalingslinjer = listOf(),
                avstemmingId = null
            ),
            stoppÅrsak = "stoppÅrsak",
            saksbehandler = Saksbehandler(id = "saksbehandler")
        )

        val sakMock = mock<Sak> {
            on { stoppUtbetalinger(any(), any(), any()) } doReturn stoppbehandling.right()
        }
        val objectRepoMock = mock<ObjectRepo> {
            on { hentSak(sakId) } doReturn sakMock
        }

        withTestApplication({

            testSusebakover(databaseRepos = DatabaseRepos(objectRepoMock, mock<StoppbehandlingRepo>()))
        }) {
            defaultRequest(HttpMethod.Post, "$sakPath/$sakId/stopp-utbetalinger") {}.apply {
                response.status() shouldBe OK
                objectMapper.readValue<StoppbehandlingJson>(response.content!!) shouldBe StoppbehandlingJson(
                    id = stoppbehandling.id.toString(),
                    opprettet = stoppbehandling.opprettet,
                    sakId = sakId.toString(),
                    status = stoppbehandling.status
                )
            }
        }
    }
}
