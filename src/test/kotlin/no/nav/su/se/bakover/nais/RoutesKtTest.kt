package no.nav.su.se.bakover.nais

import io.ktor.config.MapApplicationConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.ktor.util.KtorExperimentalAPI
import io.mockk.every
import io.mockk.mockk
import no.nav.su.se.bakover.inntekt.SuInntektClient
import no.nav.su.se.bakover.module
import no.nav.su.se.bakover.person.SuPersonClient
import no.nav.su.se.bakover.susebakover
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@KtorExperimentalAPI
internal class RoutesKtTest {

    @Test
    fun hello() {
        val suPersonClient = mockk<SuPersonClient>();
        every { suPersonClient.person() } returns "ALIVE"
        val suInntektClient = mockk<SuInntektClient>();
        every { suInntektClient.inntekt() } returns "A million dollars"

        withTestApplication(
            {
                testEnv()
                this.module(suPersonClient, suInntektClient)
            }
        ) {
            handleRequest(HttpMethod.Get, "/hello")
        }.apply {
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals("http://localhost", response.headers.get(HttpHeaders.AccessControlAllowOrigin))
            assertTrue(response.content?.contains(", cause i.. i.. i... i")!!);
        }
    }

}
