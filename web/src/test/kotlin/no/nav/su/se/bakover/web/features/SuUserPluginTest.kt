package no.nav.su.se.bakover.web.features

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.util.UUID

internal class SuUserPluginTest {

    @Test
    fun `should run in the application pipeline`() {
        testApplication {
            application {
                testSusebakover()
            }
            defaultRequest(
                Get,
                "/saker/${UUID.randomUUID()}",
                roller = listOf(Brukerrolle.Veileder),
                navIdent = "navidenten",
            ).apply {
                fail("sjekk på suUserContext")
                //this.suUserContext.navIdent shouldBe "navidenten"
                //this.suUserContext.grupper shouldBe listOf(applicationConfig.azure.groups.veileder)
            }
        }
    }
}
