package no.nav.su.se.bakover.web.revurdering.formue

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
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest

internal fun ApplicationTestBuilder.leggTilFormue(
    sakId: String,
    behandlingId: String,
    fraOgMed: String,
    tilOgMed: String,
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    url: String = "/saker/$sakId/revurderinger/$behandlingId/formuegrunnlag",
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            url,
            listOf(brukerrolle),
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
                    "epsFormue":  {
                        "verdiIkkePrimærbolig": 0,
                        "verdiEiendommer": 0,
                        "verdiKjøretøy": 0,
                        "innskudd": 8000,
                        "verdipapir": 0,
                        "pengerSkyldt": 0,
                        "kontanter": 11000,
                        "depositumskonto": 0
                      },
                    "søkersFormue":  {
                        "verdiIkkePrimærbolig": 0,
                        "verdiEiendommer": 0,
                        "verdiKjøretøy": 0,
                        "innskudd": 50000,
                        "verdipapir": 5000,
                        "pengerSkyldt": 0,
                        "kontanter": 2000,
                        "depositumskonto": 45000
                      },
                    "begrunnelse": "Lagt til automatisk av Formue.kt#leggTilFormue()"
                  }
                ]
                """.trimIndent(),
            )
        }.apply {
            withClue("body=${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.OK
                contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
            }
        }.bodyAsText()
    }
}
