package no.nav.su.se.bakover.web.søknadsbehandling.virkningstidspunkt

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

/**
 * Legger til virkningstidspunkt (stønadsperiode); start (fra og med) og slutt (til og med) på en søknadsbehanding.
 * Kan kalles flere ganger. Nyeste data vil overskrive de gamle.
 */
internal fun leggTilVirkningstidspunkt(
    sakId: String,
    behandlingId: String,
    fraOgMed: String = "2021-01-01",
    tilOgMed: String = "2021-12-31",
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    client: HttpClient,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/behandlinger/$behandlingId/stønadsperiode",
            listOf(brukerrolle),
            client = client,
        ) {
            //language=JSON
            setBody(
                """{
                "periode":{
                  "fraOgMed":"$fraOgMed",
                  "tilOgMed":"$tilOgMed"
                  }
              }
                """.trimMargin(),
            )
        }.apply {
            withClue("body=${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.Created
                contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
            }
        }.bodyAsText()
    }
}
