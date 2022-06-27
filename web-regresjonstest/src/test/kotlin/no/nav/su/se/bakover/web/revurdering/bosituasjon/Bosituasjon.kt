package no.nav.su.se.bakover.web.revurdering.bosituasjon

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
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest

internal fun ApplicationTestBuilder.leggTilBosituasjon(
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
                            "epsFnr": "${Fnr.generer()}",
                            "delerBolig": null,
                            "erEPSUførFlyktning": true,
                            "begrunnelse": "Lagt til automatisk av Bosituasjon.kt#leggTilBosituasjon"
                          }
                      ]
                  }
                """
    },
    url: String = "/saker/$sakId/revurderinger/$behandlingId/bosituasjongrunnlag",
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            url,
            listOf(brukerrolle),
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
