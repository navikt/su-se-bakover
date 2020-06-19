package no.nav.su.se.bakover.routes

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.testEnv
import no.nav.su.se.bakover.usingMocks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@KtorExperimentalLocationsAPI
@KtorExperimentalAPI
internal class NaisRoutesKtTest {

    @Test
    fun naisRoutes() {
        withTestApplication({
            testEnv()
            usingMocks()
        }
        ) {
            handleRequest(Get, "/isalive").apply {
                assertEquals(200, response.status()!!.value)
                assertEquals("ALIVE", response.content!!)
            }
            handleRequest(Get, "/isready").apply {
                assertEquals(200, response.status()!!.value)
                assertEquals("READY", response.content!!)
            }
            handleRequest(Get, "/metrics").apply {
                assertEquals(200, response.status()!!.value)
            }
        }
    }
}
