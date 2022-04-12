package no.nav.su.se.bakover.web.søknadsbehandling.uførhet

import io.kotest.matchers.shouldBe
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.http.HttpHeaders
import io.ktor.server.http.HttpMethod
import io.ktor.server.server.testing.TestApplicationEngine
import io.ktor.server.server.testing.contentType
import io.ktor.server.server.testing.setBody
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.vilkår.UførevilkårStatus
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
    return defaultRequest(
        HttpMethod.Post,
        "/saker/$sakId/behandlinger/$behandlingId/grunnlag/uføre",
        listOf(brukerrolle),
    ) {
        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
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
        response.contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
    }.response.content!!
}
