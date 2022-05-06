package no.nav.su.se.bakover.web.søknadsbehandling.uførhet

import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.TestApplicationEngine
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest

/**
- [fraOgMed] må stemme overens med stønadsperiodens fraOgMed
- [tilOgMed] må stemme overens med stønadsperiodens tilOgMed
- [resultat] se [UførevilkårStatus]
 */
internal fun TestApplicationEngine.leggTilUføregrunnlag(
    sakId: String,
    behandlingId: String,
    fraOgMed: String = "2021-01-01",
    tilOgMed: String = "2021-12-31",
    uføregrad: Int = 100,
    forventetInntekt: Int = 0,
    resultat: String = "VilkårOppfylt",
    begrunnelse: String = "Vurderinger rundt uføretrygd, grad og forventet inntekt etter uførhet per år er lagt til automatisk av LeggTilUførhet.kt",
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/behandlinger/$behandlingId/grunnlag/uføre",
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
                        "resultat":"$resultat",
                        "begrunnelse":"$begrunnelse"
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
