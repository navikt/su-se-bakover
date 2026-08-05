package no.nav.su.se.bakover.web.routes.revurdering

import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.UgyldigInputValideringFeilResponse
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.UgyldigInputValideringsfeil
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import java.util.UUID

internal class UnderkjennRevurderingKtTest {
    private val revurderingId = UUID.randomUUID()

    @Test
    fun `svarer med 400 når grunn eller kommentar inneholder ugyldig innhold`() {
        val ugyldigeBodies = listOf(
            //language=JSON
            """
                {
                    "grunn": "<script>alert(1)</script>",
                    "kommentar": "ok"
                }
            """.trimIndent() to UgyldigInputValideringsfeil(
                felt = "grunn",
                begrunnelse = "inneholder tegn utenfor tillatt tegnsett: '<>'",
            ),
            //language=JSON
            """
                {
                    "grunn": "MANGLER_DOKUMENTASJON",
                    "kommentar": "javascript:alert(1)"
                }
            """.trimIndent() to UgyldigInputValideringsfeil(
                felt = "kommentar",
                begrunnelse = "inneholder mistenkelig innhold",
            ),
        )

        ugyldigeBodies.forEach { (body, forventetFeil) ->
            testApplication {
                application {
                    testSusebakoverWithMockedDb()
                }
                defaultRequest(
                    HttpMethod.Patch,
                    "/saker/$sakId/revurderinger/$revurderingId/underkjenn",
                    listOf(Brukerrolle.Attestant),
                ) {
                    setBody(body)
                }.apply {
                    status shouldBe HttpStatusCode.BadRequest
                    deserialize<UgyldigInputValideringFeilResponse>(bodyAsText()) shouldBe UgyldigInputValideringFeilResponse(
                        message = "Ugyldig input underkjenn revurdering",
                        code = UGYLDIG_INPUT_UNDERKJENN_REVURDERING,
                        errors = listOf(forventetFeil),
                    )
                }
            }
        }
    }
}
