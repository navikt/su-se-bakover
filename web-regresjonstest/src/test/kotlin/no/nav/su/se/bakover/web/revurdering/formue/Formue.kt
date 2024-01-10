package no.nav.su.se.bakover.web.revurdering.formue

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

internal fun leggTilFormue(
    sakId: String,
    behandlingId: String,
    fraOgMed: String,
    tilOgMed: String,
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    url: String = "/saker/$sakId/revurderinger/$behandlingId/formuegrunnlag",
    client: HttpClient,
    søkersFormue: String = """
        {
          "verdiIkkePrimærbolig": 0,
          "verdiEiendommer": 0,
          "verdiKjøretøy": 0,
          "innskudd": 6000,
          "verdipapir": 5000,
          "pengerSkyldt": 0,
          "kontanter": 2000,
          "depositumskonto": 4500
        }
    """.trimIndent(),
    epsFormue: String = """
        {
          "verdiIkkePrimærbolig": 0,
          "verdiEiendommer": 0,
          "verdiKjøretøy": 0,
          "innskudd": 8000,
          "verdipapir": 0,
          "pengerSkyldt": 0,
          "kontanter": 11000,
          "depositumskonto": 0
        }
    """.trimIndent(),
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            url,
            listOf(brukerrolle),
            client = client,
        ) {
            setBody(
                //language=JSON
                """
                [
                  {
                    "periode": {
                      "fraOgMed": "$fraOgMed",
                      "tilOgMed": "$tilOgMed"
                    },
                    "epsFormue": $epsFormue,
                    "søkersFormue": $søkersFormue,
                    "begrunnelse": "Lagt til automatisk av Formue.kt#leggTilFormue()"
                  }
                ]
                """.trimIndent(),
            )
        }.apply {
            withClue("body=${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.OK
                contentType() shouldBe ContentType.parse("application/json")
            }
        }.bodyAsText()
    }
}
