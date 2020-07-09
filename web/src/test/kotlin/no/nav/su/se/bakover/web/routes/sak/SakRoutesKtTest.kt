package no.nav.su.se.bakover.web.routes.sak

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testEnv
import no.nav.su.se.bakover.web.testSusebakover
import org.json.JSONObject
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

@KtorExperimentalAPI
@KtorExperimentalLocationsAPI
internal class SakRoutesKtTest {

    private val sakFnr01 = "12345678911"
    private val sakRepo = DatabaseBuilder.build(EmbeddedDatabase.instance())

    @Test
    fun `henter sak for sak id`() {
        withTestApplication(({
            testEnv()
            testSusebakover()
        })) {
            val opprettetSakId = sakRepo.opprettSak(Fnr(sakFnr01)).toDto().id

            defaultRequest(Get, "$sakPath/$opprettetSakId").apply {
                assertEquals(OK, response.status())
                assertEquals(sakFnr01, JSONObject(response.content).getString("fnr"))
            }
        }
    }

    @Test
    fun `error handling`() {
        withTestApplication(({
            testEnv()
            testSusebakover()
        })) {
            defaultRequest(Get, "$sakPath/${UUID.randomUUID()}").apply {
                assertEquals(NotFound, response.status())
            }

            defaultRequest(Get, "$sakPath/adad").apply {
                assertEquals(BadRequest, response.status())
            }
        }
    }
}
