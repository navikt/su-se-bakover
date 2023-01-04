package no.nav.su.se.bakover.web

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
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest

internal fun leggTilOpplysningsplikt(
    behandlingId: String,
    type: String = "SØKNADSBEHANDLING",
    beskrivelse: String = "TilstrekkeligDokumentasjon",
    fraOgMed: String = "2021-01-01",
    tilOgMed: String = "2021-12-31",
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    url: String = "/vilkar/opplysningsplikt",
    client: HttpClient,
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
                  {
                    "id": "$behandlingId",
                    "type": "$type",
                    "data": [
                        {
                          "periode": {
                            "fraOgMed": "$fraOgMed",
                            "tilOgMed": "$tilOgMed"
                          },
                          "beskrivelse": "$beskrivelse"
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
