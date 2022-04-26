package no.nav.su.se.bakover.web.revurdering.opprett

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

internal fun TestApplicationEngine.opprettRevurdering(
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
    return defaultRequest(
        HttpMethod.Post,
        "/saker/$sakId/revurderinger",
        listOf(brukerrolle),
    ) {
        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
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
        response.status() shouldBe HttpStatusCode.Created
        response.contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
    }.response.content!!
}
