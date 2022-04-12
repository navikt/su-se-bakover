package no.nav.su.se.bakover.web.søknadsbehandling.beregning

import io.kotest.matchers.shouldBe
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.http.HttpHeaders
import io.ktor.server.http.HttpMethod
import io.ktor.server.server.testing.TestApplicationEngine
import io.ktor.server.server.testing.contentType
import io.ktor.server.server.testing.setBody
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest

internal fun TestApplicationEngine.beregn(
    sakId: String,
    behandlingId: String,
    begrunnelse: String = "Beregning er kjørt automatisk av Beregn.kt",
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
): String {
    return defaultRequest(
        HttpMethod.Post,
        "/saker/$sakId/behandlinger/$behandlingId/beregn",
        listOf(brukerrolle),
    ) {
        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        setBody(
            //language=JSON
            """
              {
                "begrunnelse": "$begrunnelse"
              }
            """.trimIndent(),
        )
    }.apply {
        status shouldBe HttpStatusCode.Created
        response.contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
    }.response.content!!
}
