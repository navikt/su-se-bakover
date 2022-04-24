package no.nav.su.se.bakover.web.revurdering.forhåndsvarsel

import io.kotest.matchers.shouldBe
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.contentType
import io.ktor.server.testing.setBody
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest

internal fun TestApplicationEngine.leggTilIngenForhåndsvarsel(
    sakId: String,
    behandlingId: String,
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    url: String = "/saker/$sakId/revurderinger/$behandlingId/forhandsvarsel",
): String {
    return defaultRequest(
        HttpMethod.Post,
        url,
        listOf(brukerrolle),
    ) {
        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
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
        response.status() shouldBe HttpStatusCode.OK
        response.contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
    }.response.content!!
}
