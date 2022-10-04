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

/**
 * - [fraOgMed] må stemme overens med stønadsperiodens fraOgMed
 * - [tilOgMed] må stemme overens med stønadsperiodens tilOgMed
 * - [vurdering] se [no.nav.su.se.bakover.service.vilkår.UtenlandsoppholdStatus]
 */
internal fun ApplicationTestBuilder.leggTilUtenlandsopphold(
    sakId: String,
    behandlingId: String,
    fraOgMed: String = "2021-01-01",
    tilOgMed: String = "2021-12-31",
    vurdering: String = "SkalHoldeSegINorge",
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    url: String = "/saker/$sakId/behandlinger/$behandlingId/utenlandsopphold",
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            url,
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
                      "status":"$vurdering"
                      }
                    ]
                  }
                """.trimIndent(),
            )
        }.apply {
            withClue("body=${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.OK
                contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
            }
        }.bodyAsText()
    }
}
