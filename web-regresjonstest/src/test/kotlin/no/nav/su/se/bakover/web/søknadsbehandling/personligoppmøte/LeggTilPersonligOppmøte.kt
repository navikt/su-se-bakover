package no.nav.su.se.bakover.web.søknadsbehandling.personligoppmøte

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest

internal fun ApplicationTestBuilder.leggTilPersonligOppmøte(
    sakId: String,
    behandlingId: String,
    fraOgMed: String,
    tilOgMed: String,
    body: () -> String = { innvilgetPersonligOppmøteJson(fraOgMed, tilOgMed) },
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    url: String = "/saker/$sakId/behandlinger/$behandlingId/personligoppmøte",
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            url,
            listOf(brukerrolle),
        ) {
            setBody(body())
        }.apply {
            withClue("body=${this.bodyAsText()}") {
                this.status shouldBe HttpStatusCode.Created
                contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
            }
        }.bodyAsText()
    }
}

internal fun innvilgetPersonligOppmøteJson(fraOgMed: String, tilOgMed: String): String {
    return """
       [
          {
            "periode": {
              "fraOgMed": "$fraOgMed",
              "tilOgMed": "$tilOgMed"
            },
            "vurdering": "MøttPersonlig"
          }
        ]
    """.trimIndent()
}

internal fun avslåttPersonligOppmøteJson(fraOgMed: String, tilOgMed: String): String {
    return """
       [
          {
            "periode": {
              "fraOgMed": "$fraOgMed",
              "tilOgMed": "$tilOgMed"
            },
            "vurdering": "IkkeMøttPersonlig"
          }
        ]
    """.trimIndent()
}
