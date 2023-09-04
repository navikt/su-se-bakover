package no.nav.su.se.bakover.web.søknadsbehandling.opphold

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
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest

internal fun leggTilInstitusjonsopphold(
    sakId: String,
    behandlingId: String,
    vurdering: String = "VilkårOppfylt",
    fraOgMed: String,
    tilOgMed: String,
    url: String = "/saker/$sakId/behandlinger/$behandlingId/institusjonsopphold",
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    client: HttpClient,
): String {
    return runBlocking {
        defaultRequest(
            method = HttpMethod.Post,
            uri = url,
            roller = listOf(brukerrolle),
            client = client,
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
                contentType() shouldBe ContentType.parse("application/json")
            }
        }.bodyAsText()
    }
}
