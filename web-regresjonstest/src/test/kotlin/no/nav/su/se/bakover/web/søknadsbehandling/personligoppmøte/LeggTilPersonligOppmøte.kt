package no.nav.su.se.bakover.web.søknadsbehandling.personligoppmøte

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.test.application.defaultRequest

internal fun leggTilPersonligOppmøte(
    sakId: String,
    behandlingId: String,
    fraOgMed: String = "2021-01-01",
    tilOgMed: String = "2021-12-31",
    body: () -> String = { innvilgetPersonligOppmøteJson(fraOgMed, tilOgMed) },
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    url: String = "/saker/$sakId/behandlinger/$behandlingId/personligoppmøte",
    client: HttpClient,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            url,
            listOf(brukerrolle),
            client = client,
        ) {
            setBody(body())
        }.apply {
            withClue("body=${this.bodyAsText()}") {
                this.status shouldBe HttpStatusCode.Created
                contentType() shouldBe ContentType.parse("application/json")
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
