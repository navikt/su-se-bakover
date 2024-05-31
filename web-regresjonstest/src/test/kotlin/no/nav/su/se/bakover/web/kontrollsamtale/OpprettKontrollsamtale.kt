package no.nav.su.se.bakover.web.kontrollsamtale

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.test.application.defaultRequest

internal fun opprettKontrollsamtale(
    sakId: String,
    innkallingsmåned: String,
    client: HttpClient,
    expectedStatus: HttpStatusCode = HttpStatusCode.OK,
): String {
    val body = """
        {
            "innkallingsmåned": "$innkallingsmåned"
        }
    """.trimIndent()
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/kontrollsamtaler",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
        ) { setBody(body) }.apply {
            status shouldBe expectedStatus
        }.bodyAsText()
    }
}
