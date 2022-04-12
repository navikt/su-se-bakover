package no.nav.su.se.bakover.web.søknadsbehandling.opphold

import io.kotest.matchers.shouldBe
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.http.HttpHeaders
import io.ktor.server.http.HttpMethod
import io.ktor.server.server.testing.TestApplicationEngine
import io.ktor.server.server.testing.contentType
import io.ktor.server.server.testing.setBody
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest

/**
 * - [fraOgMed] må stemme overens med stønadsperiodens fraOgMed
 * - [tilOgMed] må stemme overens med stønadsperiodens tilOgMed
 * - [vurdering] se [no.nav.su.se.bakover.service.vilkår.UtenlandsoppholdStatus]
 */
internal fun TestApplicationEngine.leggTilUtenlandsopphold(
    sakId: String,
    behandlingId: String,
    fraOgMed: String = "2021-01-01",
    tilOgMed: String = "2021-12-31",
    vurdering: String = "SkalHoldeSegINorge",
    begrunnelse: String = "Vurdering av utenlandsopphold er lagt til automatisk av LeggTilUførhet.kt",
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
): String {
    return defaultRequest(
        HttpMethod.Post,
        "/saker/$sakId/behandlinger/$behandlingId/utenlandsopphold",
        listOf(brukerrolle),
    ) {
        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        setBody(
            //language=JSON
            """
                  {
                    "periode":{
                      "fraOgMed":"$fraOgMed",
                      "tilOgMed":"$tilOgMed"
                    },
                    "status":"$vurdering",
                    "begrunnelse":"$begrunnelse"
                  }
            """.trimIndent(),
        )
    }.apply {
        status shouldBe HttpStatusCode.Created
        response.contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
    }.response.content!!
}
