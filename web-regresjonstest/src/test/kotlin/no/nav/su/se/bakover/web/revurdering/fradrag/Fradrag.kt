package no.nav.su.se.bakover.web.revurdering.fradrag

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

internal fun ApplicationTestBuilder.leggTilFradrag(
    sakId: String,
    behandlingId: String,
    fraOgMed: String,
    tilOgMed: String,
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    body: () -> String =
        {
            """
                {
                  "fradrag": [
                    {
                      "periode": {
                        "fraOgMed": "$fraOgMed",
                        "tilOgMed": "$tilOgMed"
                      },
                      "type": "Arbeidsinntekt",
                      "beløp": 5000.0,
                      "utenlandskInntekt": null,
                      "tilhører": "BRUKER"
                    },
                    {
                      "periode": {
                        "fraOgMed": "$fraOgMed",
                        "tilOgMed": "$tilOgMed"
                      },
                      "type": "PrivatPensjon",
                      "beløp": 24950.0,
                      "utenlandskInntekt": null,
                      "tilhører": "EPS"
                    }
                  ]
                }
                """
        },
    url: String = "/saker/$sakId/revurderinger/$behandlingId/fradrag",
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
                status shouldBe HttpStatusCode.OK
                contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
            }
        }.bodyAsText()
    }
}
