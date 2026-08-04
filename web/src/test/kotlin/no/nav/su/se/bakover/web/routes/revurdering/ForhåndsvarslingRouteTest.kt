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
import no.nav.su.se.bakover.web.routes.revurdering.forhåndsvarsel.UGYLDIG_INPUT_FORHÅNDSVARSEL
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class ForhåndsvarslingRouteTest {
    private val revurderingId = UUID.randomUUID()

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

    @Test
    fun `returnerer UgyldigInputValideringFeilResponse når fritekst inneholder ugyldig innhold`() {
        val ugyldigeBodies = listOf(
            //language=JSON
            """
                    {
                        "fritekst": "<script>alert(1)</script>"
                    }
            """.trimIndent() to "inneholder tegn utenfor tillatt tegnsett: '<>'",
            //language=JSON
            """
                    {
                        "fritekst": "javascript:alert(1)"
                    }
            """.trimIndent() to "inneholder mistenkelig innhold",
        )

        ugyldigeBodies.forEach { (body, forventetBegrunnelse) ->
            testApplication {
                application {
                    testSusebakoverWithMockedDb()
                }
                defaultRequest(
                    HttpMethod.Post,
                    "/saker/$sakId/revurderinger/$revurderingId/forhandsvarsel",
                    listOf(Brukerrolle.Saksbehandler),
                ) {
                    setBody(body)
                }.apply {
                    status shouldBe HttpStatusCode.BadRequest
                    deserialize<ErrorJson>(bodyAsText()) shouldBe ErrorJson(
                        message = forventetBegrunnelse,
                        code = UGYLDIG_INPUT_FORHÅNDSVARSEL,
                    )
                }
            }
        }
    }
}
