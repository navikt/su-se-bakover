package no.nav.su.se.bakover.web.søknadsbehandling.uførhet

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

/**
- [fraOgMed] må stemme overens med stønadsperiodens fraOgMed
- [tilOgMed] må stemme overens med stønadsperiodens tilOgMed
- [resultat] se [UførevilkårStatus]
 */
internal fun ApplicationTestBuilder.leggTilUføregrunnlag(
    sakId: String,
    behandlingId: String,
    fraOgMed: String = "2021-01-01",
    tilOgMed: String = "2021-12-31",
    uføregrad: Int = 100,
    forventetInntekt: Int = 0,
    resultat: String = "VilkårOppfylt",
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    url: String = "/saker/$sakId/behandlinger/$behandlingId/grunnlag/uføre",
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
                  {
                    "vurderinger": [
                      {
                        "periode":{
                          "fraOgMed":"$fraOgMed",
                          "tilOgMed":"$tilOgMed"
                        },
                        "uføregrad":$uføregrad,
                        "forventetInntekt":$forventetInntekt,
                        "resultat":"$resultat"
                      }
                    ]
                  }
                """.trimIndent(),
            )
        }.apply {
            status shouldBe HttpStatusCode.Created
            contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
        }.bodyAsText()
    }
}
