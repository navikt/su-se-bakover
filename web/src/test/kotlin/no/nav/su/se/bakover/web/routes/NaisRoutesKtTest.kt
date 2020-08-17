package no.nav.su.se.bakover.web.routes

import io.ktor.http.HttpMethod.Companion.Get

import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication

import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class NaisRoutesKtTest {

    @Test
    fun naisRoutes() {
        withTestApplication({
            testSusebakover()
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
