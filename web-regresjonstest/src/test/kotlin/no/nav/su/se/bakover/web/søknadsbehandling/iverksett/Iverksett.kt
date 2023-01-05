package no.nav.su.se.bakover.web.søknadsbehandling.iverksett

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest

internal fun iverksett(
    sakId: String,
    behandlingId: String,
    brukerrolle: Brukerrolle = Brukerrolle.Attestant,
    assertResponse: Boolean = true,
    client: HttpClient,
    navIdent: String = "automatiskAttesteringAvSøknadsbehandling",
): String {
    return runBlocking {
        defaultRequest(
            method = HttpMethod.Patch,
            uri = "/saker/$sakId/behandlinger/$behandlingId/iverksett",
            roller = listOf(brukerrolle),
            navIdent = navIdent,
            client = client,
        ).apply {
            if (assertResponse) {
                status shouldBe HttpStatusCode.OK
            }
            contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
        }.bodyAsText()
    }
}
