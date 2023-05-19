package no.nav.su.se.bakover.web.dokumenter

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest

fun opprettFritekstDokument(
    sakId: String,
    tittel: String = "Fritekst-brevets tittel",
    fritekst: String = "Innholdet i fritekst-brevet",
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.OK,
    client: HttpClient,
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
            "/saker/$sakId/fritekstDokument",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
        ) { setBody(body) }.apply {
            status shouldBe expectedHttpStatusCode
        }.bodyAsText()
    }
}
