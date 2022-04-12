package no.nav.su.se.bakover.web.søknadsbehandling.formue

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

internal fun TestApplicationEngine.leggTilFormue(
    sakId: String,
    behandlingId: String,
    vurdering: String = "VilkårOppfylt",
    begrunnelse: String = "Vurdering av formue er lagt til automatisk av LeggTilFormue.kt",
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
): String {
    return defaultRequest(
        HttpMethod.Patch,
        "/saker/$sakId/behandlinger/$behandlingId/informasjon",
        listOf(brukerrolle),
    ) {
        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        setBody(
            //language=JSON
            """
                  {
                    "flyktning":null,
                    "lovligOpphold":null,
                    "fastOppholdINorge":null,
                    "institusjonsopphold":null,
                    "formue":{
                      "status": "$vurdering",
                      "verdier": {
                        "verdiIkkePrimærbolig": 0,
                        "verdiEiendommer": 0,
                        "verdiKjøretøy": 0,
                        "innskudd": 0,
                        "verdipapir": 0,
                        "pengerSkyldt": 0,
                        "kontanter": 0,
                        "depositumskonto": 0
                      },
                      "epsVerdier": null,
                      "begrunnelse": "$begrunnelse"
                    },
                    "personligOppmøte":null
                  }
            """.trimIndent(),
        )
    }.apply {
        status shouldBe HttpStatusCode.OK
        response.contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
    }.response.content!!
}
