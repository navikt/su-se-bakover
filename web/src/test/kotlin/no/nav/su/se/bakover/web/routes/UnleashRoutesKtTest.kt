package no.nav.su.se.bakover.web.routes

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.service.toggles.ToggleService
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class UnleashRoutesKtTest {

    @Test
    fun unleashRoutes() {
        val toggleMock = mock<ToggleService> {
            on { isEnabled("supstonad.enToggle") } doReturn true
            on { isEnabled("supstonad.annenToggle") } doReturn false
        }
        withTestApplication(
            {
                testSusebakover(
                    services = TestServicesBuilder.services().copy(toggles = toggleMock)
                )
            }
        ) {
            handleRequest(Get, "/toggles/supstonad.enToggle").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                JSONAssert.assertEquals("""{"supstonad.enToggle": true}""".trimIndent(), response.content!!, true)
            }
        }
    }
}
