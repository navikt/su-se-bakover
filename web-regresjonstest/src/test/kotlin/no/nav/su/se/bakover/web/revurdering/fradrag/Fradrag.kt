package no.nav.su.se.bakover.web.revurdering.fradrag

import io.kotest.matchers.shouldBe
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.contentType
import io.ktor.server.testing.setBody
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest

internal fun TestApplicationEngine.leggTilFradrag(
    sakId: String,
    behandlingId: String,
    fraOgMed: String,
    tilOgMed: String,
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    url: String = "/saker/$sakId/revurderinger/$behandlingId/fradrag",
): String {
    return defaultRequest(
        HttpMethod.Post,
        url,
        listOf(brukerrolle),
    ) {
        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        setBody(
            //language=JSON
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
            """.trimIndent(),
        )
    }.apply {
        response.status() shouldBe HttpStatusCode.OK
        response.contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
    }.response.content!!
}
