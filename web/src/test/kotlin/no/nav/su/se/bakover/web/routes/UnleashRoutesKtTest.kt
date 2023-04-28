package no.nav.su.se.bakover.web.routes

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.toggle.domain.ToggleClient
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert

internal class UnleashRoutesKtTest {

    @Test
    fun unleashRoutes() {
        val toggleMock = mock<ToggleClient> {
            on { isEnabled("supstonad.enToggle") } doReturn true
            on { isEnabled("supstonad.annenToggle") } doReturn false
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(toggles = toggleMock),
                )
            }
            val res = client.get("/toggles/supstonad.enToggle")
            assertEquals(HttpStatusCode.OK, res.status)
            JSONAssert.assertEquals("""{"supstonad.enToggle": true}""".trimIndent(), res.bodyAsText(), true)
        }
    }
}
