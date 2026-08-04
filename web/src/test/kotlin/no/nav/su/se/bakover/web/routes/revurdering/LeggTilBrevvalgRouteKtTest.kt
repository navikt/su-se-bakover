package no.nav.su.se.bakover.web.routes.revurdering

import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.web.ErrorJson
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import java.util.UUID

internal class LeggTilBrevvalgRouteKtTest {
    private val revurderingId = UUID.randomUUID()

    @Test
    fun `returnerer ErrorJson når begrunnelse inneholder ugyldig innhold`() {
        val ugyldigeBodies = listOf(
            //language=JSON
            """
                {
                    "valg": "SEND",
                    "begrunnelse": "<script>alert(1)</script>"
                }
            """.trimIndent() to "inneholder tegn utenfor tillatt tegnsett",
            //language=JSON
            """
                {
                    "valg": "SEND",
                    "begrunnelse": "javascript:alert(1)"
                }
            """.trimIndent() to "inneholder mistenkelig innhold",
            //language=JSON
            """
                {
                    "valg": "SEND",
                    "begrunnelse": "${"x".repeat(2001)}"
                }
            """.trimIndent() to "for lang verdi",
        )

        ugyldigeBodies.forEach { (body, forventetBegrunnelse) ->
            testApplication {
                application {
                    testSusebakoverWithMockedDb()
                }
                defaultRequest(
                    HttpMethod.Post,
                    "/saker/$sakId/revurderinger/$revurderingId/brevvalg",
                    listOf(Brukerrolle.Saksbehandler),
                ) {
                    setBody(body)
                }.apply {
                    status shouldBe HttpStatusCode.BadRequest
                    deserialize<ErrorJson>(bodyAsText()) shouldBe ErrorJson(
                        message = forventetBegrunnelse,
                        code = UGYLDIG_INPUT_LEGG_TIL_BREVVALG,
                    )
                }
            }
        }
    }
}
