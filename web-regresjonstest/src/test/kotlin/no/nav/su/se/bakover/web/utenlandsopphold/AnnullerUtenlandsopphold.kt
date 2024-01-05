package no.nav.su.se.bakover.web.utenlandsopphold

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.test.application.defaultRequest

fun annullerUtenlandsopphold(
    sakId: String,
    saksversjon: Long,
    annullererVersjon: Long,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.OK,
    client: HttpClient,
): String {
    return runBlocking {
        val body = """
          {
            "saksversjon": $saksversjon
          }
        """.trimIndent()
        defaultRequest(
            HttpMethod.Patch,
            "/saker/$sakId/utenlandsopphold/$annullererVersjon",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
        ) { setBody(body) }.apply {
            status shouldBe expectedHttpStatusCode
        }.bodyAsText()
    }
}
