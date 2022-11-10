package no.nav.su.se.bakover.web.dokumenter

import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest

fun ApplicationTestBuilder.lagreOgSendFritekstDokument(
    sakId: String,
    tittel: String = "Fritekst-brevets tittel",
    fritekst: String = "Innholdet i fritekst-brevet",
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
): String {
    //language=JSON
    val body = """
      {
        "tittel": "$tittel",
        "fritekst": "$fritekst"
      }
    """.trimIndent()
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/fritekstDokument/lagreOgSend",
            listOf(Brukerrolle.Saksbehandler),
        ) { setBody(body) }.apply {
            status shouldBe expectedHttpStatusCode
        }.bodyAsText()
    }
}
