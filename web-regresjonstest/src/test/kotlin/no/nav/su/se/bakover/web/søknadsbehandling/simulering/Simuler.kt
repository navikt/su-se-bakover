package no.nav.su.se.bakover.web.søknadsbehandling.simulering

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.test.application.defaultRequest

internal fun simuler(
    sakId: String,
    behandlingId: String,
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    client: HttpClient,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/behandlinger/$behandlingId/simuler",
            listOf(brukerrolle),
            client = client,
        ).apply {
            status shouldBe HttpStatusCode.OK
            contentType() shouldBe ContentType.parse("application/json")
        }.bodyAsText()
    }
}
