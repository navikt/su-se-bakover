package no.nav.su.se.bakover.web.revurdering.opprett

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

internal fun opprettRevurdering(
    sakId: String,
    fraOgMed: String,
    tilOgMed: String,
    årsak: String = "MELDING_FRA_BRUKER",
    begrunnelse: String = "Behov for å vurdere ny informasjon mottatt pr telefon.",
    informasjonSomRevurderes: String = """
            [
                "Uførhet",
                "Bosituasjon",
                "Formue",
                "Inntekt"
            ]
    """.trimIndent(),
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    client: HttpClient,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/revurderinger",
            listOf(brukerrolle),
            client = client,
        ) {
            setBody(
                //language=JSON
                """
                  {
                    "fraOgMed": "$fraOgMed",
                    "tilOgMed": "$tilOgMed",
                    "årsak": "$årsak",
                    "begrunnelse": "$begrunnelse",
                    "informasjonSomRevurderes": $informasjonSomRevurderes
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
