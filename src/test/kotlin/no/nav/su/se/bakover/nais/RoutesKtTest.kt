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
import org.junit.jupiter.api.Assertions.assertTrue
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
            assertTrue(response.content?.contains(", cause i.. i.. i... i")!!);
        }
    }

}
