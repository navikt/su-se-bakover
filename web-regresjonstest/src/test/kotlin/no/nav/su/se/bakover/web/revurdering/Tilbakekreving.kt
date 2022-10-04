package no.nav.su.se.bakover.web.revurdering

import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest

internal fun ApplicationTestBuilder.avgjørTilbakekreving(
    sakId: String,
    behandlingId: String,
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    avgjørelse: () -> String = {
        """
            {"avgjørelse":"TILBAKEKREV"}
        """.trimIndent()
    },
    url: String = "/saker/$sakId/revurderinger/$behandlingId/tilbakekreving",
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            url,
            listOf(brukerrolle),
        ) {
            setBody(avgjørelse())
        }.apply {
            status shouldBe HttpStatusCode.OK
            contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
        }.bodyAsText()
    }
}
