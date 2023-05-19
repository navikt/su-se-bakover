package no.nav.su.se.bakover.web.utenlandsopphold

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest

fun nyttUtenlandsopphold(
    sakId: String,
    fraOgMed: String = "2021-05-05",
    tilOgMed: String = "2021-10-10",
    journalpostIder: String = "[1234567]",
    dokumentasjon: String = "Sannsynliggjort",
    begrunnelse: String = "Har sendt inn kopi av flybiletter. Se journalpost",
    saksversjon: Long = 1,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
    client: HttpClient,
): String {
    val body = """
      {
        "periode":{
          "fraOgMed": "$fraOgMed",
          "tilOgMed": "$tilOgMed"
        },
        "journalposter": $journalpostIder,
        "dokumentasjon": "$dokumentasjon",
        "begrunnelse": "$begrunnelse",
        "saksversjon": $saksversjon
      }
    """.trimIndent()
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/utenlandsopphold",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
        ) { setBody(body) }.apply {
            status shouldBe expectedHttpStatusCode
        }.bodyAsText()
    }
}
