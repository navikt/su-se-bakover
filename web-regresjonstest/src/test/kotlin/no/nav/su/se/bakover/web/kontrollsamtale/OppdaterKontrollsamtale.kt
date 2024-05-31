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

internal fun oppdaterInnkallingsmånedPåKontrollsamtale(
    sakId: String,
    kontrollsamtaleId: String,
    innkallingsmåned: String,
    client: HttpClient,
    expectedStatus: HttpStatusCode = HttpStatusCode.OK,
): String {
    val body = "{\"innkallingsmåned\":\"$innkallingsmåned\"}"
    return runBlocking {
        defaultRequest(
            HttpMethod.Patch,
            "/saker/$sakId/kontrollsamtaler/$kontrollsamtaleId/innkallingsmåned",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
        ) { setBody(body) }.apply {
            status shouldBe expectedStatus
        }.bodyAsText()
    }
}

internal fun oppdaterStatusPåKontrollsamtale(
    sakId: String,
    kontrollsamtaleId: String,
    status: String,
    journalpostId: String? = null,
    client: HttpClient,
): String {
    @Suppress("RemoveSingleExpressionStringTemplate")
    val body = """
     {
        "status": "$status",
        "journalpostId": ${journalpostId?.let { "$it" }}
      }
    """.trimIndent()
    return runBlocking {
        defaultRequest(
            HttpMethod.Patch,
            "/saker/$sakId/kontrollsamtaler/$kontrollsamtaleId/status",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
        ) { setBody(body) }.apply {
            status shouldBe HttpStatusCode.OK
        }.bodyAsText()
    }
}
