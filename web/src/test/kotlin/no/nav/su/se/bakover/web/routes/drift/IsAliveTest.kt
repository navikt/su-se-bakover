package no.nav.su.se.bakover.web.routes.drift

import io.kotest.matchers.shouldBe
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class IsAliveTest {

    private val services = TestServicesBuilder.services()

    @Test
    fun `Kun Drift har tilgang til isAlive-endepunktet`() {
        Brukerrolle.values().filterNot { it == Brukerrolle.Drift }.forEach {
            testApplication {
                application {
                    testSusebakoverWithMockedDb(services = services)
                }
                defaultRequest(
                    method = HttpMethod.Get,
                    uri = "$DRIFT_PATH/isalive",
                    roller = listOf(it),
                ) {
                }.apply {
                    status shouldBe HttpStatusCode.Forbidden
                }
            }
        }
    }

    @Test
    fun `isAlive-endepunktet gir status ok`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            defaultRequest(
                HttpMethod.Get,
                "$DRIFT_PATH/isalive",
                listOf(Brukerrolle.Drift),
            ) {
            }.apply {
                status shouldBe HttpStatusCode.OK
                JSONAssert.assertEquals(
                    """{ "Status" : "OK"}""".trimIndent(),
                    this.bodyAsText(),
                    true,
                )
            }
        }
    }
}
