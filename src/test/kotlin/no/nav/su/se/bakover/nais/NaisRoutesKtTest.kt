package no.nav.su.se.bakover.nais

import com.github.kittinunf.fuel.httpGet
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.testServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class NaisRoutesKtTest {
    @Test
    fun naisRoutes() {
        testServer {
            val (_, _, isalive) = "http://localhost:8088/isalive".httpGet().responseString()
            assertEquals("ALIVE", isalive.get())

            val (_, _, isready) = "http://localhost:8088/isready".httpGet().responseString()
            assertEquals("READY", isready.get())
        }
    }

    @Test
    fun httpCodes() {
        testServer {
            val (_, notFound, _) = "http://localhost:8088/notfound".httpGet().responseString()
            assertEquals(HttpStatusCode.NotFound.value, notFound.statusCode)
        }
    }
}