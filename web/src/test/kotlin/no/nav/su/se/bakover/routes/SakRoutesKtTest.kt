package no.nav.su.se.bakover.routes

import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.*
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.EmbeddedDatabase
import org.json.JSONObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@KtorExperimentalAPI
@KtorExperimentalLocationsAPI
internal class SakRoutesKtTest : ComponentTest() {

    private val sakFnr01 = "12345678911"
    private val sakRepo = DatabaseBuilder.fromDatasource(EmbeddedDatabase.database)

    @Test
    fun `henter sak for sak id`() {
        withTestApplication(({
            testEnv(wireMockServer)
            susebakover()
        })) {
            val opprettetSakId = JSONObject(sakRepo.opprettSak(Fnr(sakFnr01)).toJson()).getLong("id")

            withCorrelationId(Get, "$sakPath/$opprettetSakId") {
                addHeader(Authorization, jwt)
            }.apply {
                assertEquals(OK, response.status())
                assertEquals(sakFnr01, JSONObject(response.content).getString("fnr"))
            }
        }
    }

    @Test
    fun `error handling`() {
        withTestApplication(({
            testEnv(wireMockServer)
            susebakover()
        })) {
            withCorrelationId(Get, "$sakPath/999") {
                addHeader(Authorization, jwt)
            }.apply {
                assertEquals(NotFound, response.status())
            }

            withCorrelationId(Get, "$sakPath/adad") {
                addHeader(Authorization, jwt)
            }.apply {
                assertEquals(BadRequest, response.status())
            }
        }
    }
}