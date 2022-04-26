package no.nav.su.se.bakover.web.revurdering.iverksett

import io.kotest.matchers.shouldBe
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.contentType
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest

internal fun TestApplicationEngine.iverksett(
    sakId: String,
    behandlingId: String,
    brukerrolle: Brukerrolle = Brukerrolle.Attestant,
    url: String = "/saker/$sakId/revurderinger/$behandlingId/iverksett",
): String {
    return defaultRequest(
        HttpMethod.Post,
        url,
        listOf(brukerrolle),
        "automatiskAttesteringAvSøknadsbehandling",
    ) {
        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }.apply {
        response.status() shouldBe HttpStatusCode.OK
        response.contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
    }.response.content!!
}
