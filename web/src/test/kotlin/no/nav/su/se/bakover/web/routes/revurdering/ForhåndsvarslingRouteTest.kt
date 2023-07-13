package no.nav.su.se.bakover.web.routes.revurdering

import io.kotest.matchers.shouldBe
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class ForhåndsvarslingRouteTest {
    private val revurderingId = UUID.randomUUID()

    @Nested
    inner class `lagre forhåndsvarselvalg` {
        @Test
        fun `kun saksbehandler får lov til å opprette forhåndsvarsel`() {
            testApplication {
                application {
                    testSusebakoverWithMockedDb()
                }
                Brukerrolle.entries.toList().minus(Brukerrolle.Saksbehandler).forEach {
                    defaultRequest(
                        HttpMethod.Post,
                        "/saker/$sakId/revurderinger/$revurderingId/forhandsvarsel",
                        listOf(it),
                    ).apply {
                        status shouldBe HttpStatusCode.Forbidden
                        JSONAssert.assertEquals(
                            """
                                {
                                    "message":"Bruker mangler en av de tillatte rollene: [Saksbehandler]",
                                    "code":"mangler_rolle"
                                }
                            """.trimIndent(),
                            bodyAsText(),
                            true,
                        )
                    }
                }
            }
        }
    }
}
