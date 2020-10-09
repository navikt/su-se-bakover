package no.nav.su.se.bakover.web.routes.sak

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.behandling.BehandlingJson
import no.nav.su.se.bakover.web.testSusebakover
import org.json.JSONObject
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

internal class SakRoutesKtTest {

    private val sakFnr01 = "12345678911"
    val fnr = Fnr(sakFnr01)
    private val repos = DatabaseBuilder.build(EmbeddedDatabase.instance())
    private val søknadRepo = repos.søknad

    @Test
    fun `henter sak for sak id`() {
        withTestApplication(
            (
                {
                    testSusebakover()
                }
                )
        ) {
            val opprettetSakId = repos.sak.opprettSak(Fnr(sakFnr01)).id

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
            repos.sak.opprettSak(Fnr(sakFnr01))

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

        val nySak = repos.sak.opprettSak(fnr)
        val nySøknad = søknadRepo.opprettSøknad(nySak.id, Søknad(sakId = nySak.id, søknadInnhold = SøknadInnholdTestdataBuilder.build()))

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
}
