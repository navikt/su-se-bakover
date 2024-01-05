package no.nav.su.se.bakover.web.søknadsbehandling.formue

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

internal fun leggTilFormue(
    sakId: String,
    behandlingId: String,
    begrunnelse: String = "Vurdering av formue er lagt til automatisk av LeggTilFormue.kt",
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    fraOgMed: String,
    tilOgMed: String,
    måInnhenteMerInformasjon: Boolean = false,
    client: HttpClient,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/behandlinger/$behandlingId/formuegrunnlag",
            listOf(brukerrolle),
            client = client,
        ) {
            setBody(
                //language=JSON
                """
                [
                  {
                        "periode": {
                          "fraOgMed": "$fraOgMed",
                          "tilOgMed": "$tilOgMed"
                        },
                        "epsFormue": null,
                        "søkersFormue": {
                          "verdiIkkePrimærbolig": 0,
                          "verdiEiendommer": 0,
                          "verdiKjøretøy": 0,
                          "innskudd": 0,
                          "verdipapir": 0,
                          "pengerSkyldt": 0,
                          "kontanter": 0,
                          "depositumskonto": 0
                        },
                        "begrunnelse": "$begrunnelse",
                        "måInnhenteMerInformasjon": $måInnhenteMerInformasjon
                  }
                ]
                """.trimIndent(),
            )
        }.apply {
            withClue("body=${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.OK
                contentType() shouldBe ContentType.parse("application/json")
            }
        }.bodyAsText()
    }
}
