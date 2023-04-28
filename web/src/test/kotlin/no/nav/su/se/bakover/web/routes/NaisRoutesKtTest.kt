package no.nav.su.se.bakover.web.routes

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class NaisRoutesKtTest {

    @Test
    fun naisRoutes() {
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            client.get("/isalive").apply {
                assertEquals(200, status.value)
                assertEquals("ALIVE", this.bodyAsText())
            }
            client.get("/isready").apply {
                assertEquals(200, status.value)
                assertEquals("READY", this.bodyAsText())
            }
            client.get("/metrics").apply {
                assertEquals(200, status.value)
            }
        }
    }
}
