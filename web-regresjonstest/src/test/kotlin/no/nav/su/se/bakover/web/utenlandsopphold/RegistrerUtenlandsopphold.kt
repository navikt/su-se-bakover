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

fun ApplicationTestBuilder.nyttUtenlandsopphold(
    sakId: String,
    fraOgMed: String = "2021-05-05",
    tilOgMed: String = "2021-10-10",
    journalpostIder: String = "[1234567]",
    dokumentasjon: String = "Sannsynliggjort",
    saksversjon: Long = 1,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
): String {
    val body = """
      {
        "periode":{
          "fraOgMed": "$fraOgMed",
          "tilOgMed": "$tilOgMed"
        },
        "journalposter": $journalpostIder,
        "dokumentasjon": "$dokumentasjon",
        "saksversjon": $saksversjon
      }
    """.trimIndent()
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/utenlandsopphold",
            listOf(Brukerrolle.Saksbehandler),
        ) { setBody(body) }.apply {
            status shouldBe expectedHttpStatusCode
        }.bodyAsText()
    }
}
