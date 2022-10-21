package no.nav.su.se.bakover.web.utenlandsopphold

import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest

fun ApplicationTestBuilder.annullerUtenlandsopphold(
    sakId: String,
    saksversjon: Long,
    annullererVersjon: Long,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.OK,
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
        ) { setBody(body) }.apply {
            status shouldBe expectedHttpStatusCode
        }.bodyAsText()
    }
}
