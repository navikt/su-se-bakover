package no.nav.su.se.bakover.web.søknadsbehandling.bosituasjon

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
import no.nav.su.se.bakover.test.application.defaultRequest

/**
 * uri defaultes til søknadsbehandling
 * body defaultes til bor alene
 */
internal fun leggTilBosituasjon(
    sakId: String,
    behandlingId: String,
    fraOgMed: String = "2021-01-01",
    tilOgMed: String = "2021-12-31",
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    body: () -> String = {
        //language=json
        """
                  {
                      "bosituasjoner": [
                          {
                            "periode": {
                              "fraOgMed": "$fraOgMed",
                              "tilOgMed": "$tilOgMed"
                            },
                            "epsFnr": null,
                            "delerBolig": false,
                            "erEPSUførFlyktning": null,
                            "begrunnelse": "Lagt til automatisk av Bosituasjon.kt#leggTilBosituasjon"
                          }
                      ]
                  }
                """
    },
    url: String = "/saker/$sakId/behandlinger/$behandlingId/grunnlag/bosituasjon",
    client: HttpClient,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            url,
            listOf(brukerrolle),
            client = client,
        ) {
            setBody(body())
        }.apply {
            withClue("body=${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.OK
                contentType() shouldBe ContentType.parse("application/json")
            }
        }.bodyAsText()
    }
}

/**
 * hardkodet defaults med untakk av epsFnr
 */
fun bosituasjonEpsJson(epsFnr: String): String {
    //language=json
    return """
                  {
                      "bosituasjoner": [
                          {
                            "periode": {"fraOgMed": "2021-01-01", "tilOgMed": "2021-12-31"},
                            "epsFnr": "$epsFnr",
                            "delerBolig": null,
                            "erEPSUførFlyktning": true,
                            "begrunnelse": "Lagt til automatisk av Bosituasjon.kt#leggTilBosituasjon"
                          }
                      ]
                  }
                """
}
