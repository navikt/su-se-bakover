package no.nav.su.se.bakover.web

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

internal fun TestApplicationEngine.leggTilOpplysningsplikt(
    behandlingId: String,
    type: String = "SØKNADSBEHANDLING",
    beskrivelse: String = "TilstrekkeligDokumentasjon",
    fraOgMed: String = "2021-01-01",
    tilOgMed: String = "2021-12-31",
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    url: String = "/vilkar/opplysningsplikt",
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
        response.status() shouldBe HttpStatusCode.Created
        response.contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
    }.response.content!!
}
