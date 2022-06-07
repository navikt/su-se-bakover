package no.nav.su.se.bakover.web.revurdering.forhåndsvarsel

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest

internal fun ApplicationTestBuilder.leggTilIngenForhåndsvarsel(
    sakId: String,
    behandlingId: String,
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    url: String = "/saker/$sakId/revurderinger/$behandlingId/forhandsvarsel",
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            url,
            listOf(brukerrolle),
        ) {
            setBody(
                //language=JSON
                """
                {
                  "forhåndsvarselhandling": "INGEN_FORHÅNDSVARSEL",
                  "fritekst": ""
                }
                """.trimIndent(),
            )
        }.apply {
            withClue("body=${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.OK
                contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
            }
        }.bodyAsText()
    }
}
