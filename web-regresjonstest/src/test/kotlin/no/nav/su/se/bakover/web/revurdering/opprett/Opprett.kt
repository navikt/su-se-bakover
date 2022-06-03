package no.nav.su.se.bakover.web.revurdering.opprett

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
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest

internal fun ApplicationTestBuilder.opprettRevurdering(
    sakId: String,
    fraOgMed: String,
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
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/revurderinger",
            listOf(brukerrolle),
        ) {
            setBody(
                //language=JSON
                """
                  {
                    "fraOgMed": "$fraOgMed",
                    "årsak": "$årsak",
                    "begrunnelse": "$begrunnelse",
                    "informasjonSomRevurderes": $informasjonSomRevurderes 
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
