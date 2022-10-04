package no.nav.su.se.bakover.web.søknadsbehandling.opphold

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

internal fun ApplicationTestBuilder.leggTilInstitusjonsopphold(
    sakId: String,
    behandlingId: String,
    vurdering: String = "VilkårOppfylt",
    fraOgMed: String,
    tilOgMed: String,
    url: String = "/saker/$sakId/behandlinger/$behandlingId/institusjonsopphold",
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
): String {
    return runBlocking {
        defaultRequest(
            method = HttpMethod.Post,
            uri = url,
            roller = listOf(brukerrolle),
        ) {
            setBody(
                //language=JSON
                """
                  {
                    "vurderingsperioder":[
                      {
                        "periode":{
                          "fraOgMed": "$fraOgMed",
                          "tilOgMed": "$tilOgMed"
                        },
                        "vurdering": "$vurdering"
                      }
                    ]
                  }
                """.trimIndent(),
            )
        }.apply {
            withClue("body=${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.Created
                contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
            }
        }.bodyAsText()
    }
}
