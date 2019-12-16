package no.nav.su.se.bakover.nais

import com.github.kittinunf.fuel.httpGet
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.origin
import no.nav.su.se.bakover.testServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class RoutesKtTest {

    @Test
    fun hello() {
        testServer {
            val (_, response, result) = "http://localhost:8088/hello".httpGet().responseString()
            assertEquals(HttpStatusCode.OK.value, response.statusCode)
            assertEquals(origin, response.header(HttpHeaders.AccessControlAllowOrigin).first())
            assertEquals("is it me you're looking for?", result.get())
        }
    }

}
