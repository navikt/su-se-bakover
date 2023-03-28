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
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest

internal fun taStillingTilEps(
    sakId: String,
    behandlingId: String,
    epsFnr: String? = null,
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    client: HttpClient,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/behandlinger/$behandlingId/grunnlag/bosituasjon/eps",
            listOf(brukerrolle),
            client = client,
        ) {
            setBody(
                //language=JSON
                """
              {
                "epsFnr":${if (epsFnr == null) null else "$epsFnr"}
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

internal fun fullførBosituasjon(
    sakId: String,
    behandlingId: String,
    bosituasjon: String = "BOR_ALENE",
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    client: HttpClient,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/behandlinger/$behandlingId/grunnlag/bosituasjon/fullfør",
            listOf(brukerrolle),
            client = client,
        ) {
            setBody(
                //language=JSON
                """
                  {
                    "bosituasjon":"$bosituasjon"
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

/**
 * uri defaultes til søknadsbehandling
 * body defaultes til bor alene
 */
internal fun leggTilBosituasjon(
    sakId: String,
    behandlingId: String,
    fraOgMed: String,
    tilOgMed: String,
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    body: () -> String = {
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
                contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
            }
        }.bodyAsText()
    }
}
