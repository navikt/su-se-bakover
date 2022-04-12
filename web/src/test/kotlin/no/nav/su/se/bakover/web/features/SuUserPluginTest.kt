package no.nav.su.se.bakover.web.features

import io.kotest.matchers.shouldBe
import io.ktor.server.http.HttpMethod
import io.ktor.server.server.testing.withTestApplication
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.web.applicationConfig
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SuUserPluginTest {

    @Test
    fun `should run in the application pipeline`() {
        testApplication({
            testSusebakover()
        }) {
            defaultRequest(
                HttpMethod.Get,
                "/saker/${UUID.randomUUID()}",
                roller = listOf(Brukerrolle.Veileder),
                navIdent = "navidenten"
            ).apply {
                this.suUserContext.navIdent shouldBe "navidenten"
                this.suUserContext.grupper shouldBe listOf(applicationConfig.azure.groups.veileder)
            }
        }
    }
}
