package no.nav.su.se.bakover.nais

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.mockk.every
import io.mockk.mockk
import no.nav.su.se.bakover.inntekt.SuInntektClient
import no.nav.su.se.bakover.origin
import no.nav.su.se.bakover.person.SuPersonClient
import no.nav.su.se.bakover.testApp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class RoutesKtTest {

    @Test
    fun hello() {
        val suPersonClient = mockk<SuPersonClient>();
        every { suPersonClient.person() } returns "ALIVE"
        val suInntektClient = mockk<SuInntektClient>();
        every { suInntektClient.inntekt() } returns "A million dollars"

        withTestApplication({ testApp(suPersonClient = suPersonClient, suInntektClient = suInntektClient) }) {
            handleRequest(HttpMethod.Get, "/hello")
        }.apply {
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals(origin, response.headers.get(HttpHeaders.AccessControlAllowOrigin))
            assertEquals("is it me you're looking for? great, cause i.. i.. i... i... I'm staying ALIVE and i have A million dollars in the bank", response.content)
        }

//        testServer {
//            val (_, response, result) = "http://localhost:8088/hello".httpGet().responseString()
//            assertEquals(HttpStatusCode.OK.value, response.statusCode)
//            assertEquals(origin, response.header(HttpHeaders.AccessControlAllowOrigin).first())
//            assertEquals("is it me you're looking for?, i.. i.. i... i... I'am staying ALIVE", result.get())
//        }
    }

}
