package no.nav.su.se.bakover.web.søknadsbehandling.fastopphold

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

internal fun ApplicationTestBuilder.leggTilFastOppholdINorge(
    sakId: String,
    behandlingId: String,
    fraOgMed: String,
    tilOgMed: String,
    body: () -> String = { innvilgetFastOppholdJson(fraOgMed, tilOgMed) },
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    url: String = "/saker/$sakId/behandlinger/$behandlingId/fastopphold",
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
                status shouldBe HttpStatusCode.Created
                contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
            }
        }.bodyAsText()
    }
}

internal fun innvilgetFastOppholdJson(fraOgMed: String, tilOgMed: String): String {
    return """
       [
          {
            "periode": {
              "fraOgMed": "$fraOgMed",
              "tilOgMed": "$tilOgMed"
            },
            "vurdering": "VilkårOppfylt"
          }
        ]
    """.trimIndent()
}

internal fun avslåttFastOppholdJson(fraOgMed: String, tilOgMed: String): String {
    return """
       [
          {
            "periode": {
              "fraOgMed": "$fraOgMed",
              "tilOgMed": "$tilOgMed"
            },
            "vurdering": "VilkårIkkeOppfylt"
          }
        ]
    """.trimIndent()
}
