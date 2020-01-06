package no.nav.su.se.bakover.nais

import io.ktor.config.MapApplicationConfig
import io.ktor.http.HttpMethod
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import no.nav.su.se.bakover.susebakover
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@KtorExperimentalAPI
internal class NaisRoutesKtTest {

    @Test
    fun naisRoutes() {
        withTestApplication(
            {
                testEnv()
                this.susebakover()
            }
        ) {
            handleRequest(HttpMethod.Get, "/isalive").apply {
                assertEquals(200, response.status()!!.value)
                assertEquals("ALIVE", response.content!!)
            }
            handleRequest(HttpMethod.Get, "/isready").apply {
                assertEquals(200, response.status()!!.value)
                assertEquals("READY", response.content!!)
            }
        }
    }

    @Test
    fun notFound() {
        withTestApplication(
            {
                testEnv()
                this.susebakover()
            }
        ) {
            handleRequest(HttpMethod.Get, "/notfound").apply {
                assertEquals(false, requestHandled)
            }
        }
    }
}