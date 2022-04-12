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
- [vurdering] se [no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon.FastOppholdINorge.Status]
 */
internal fun TestApplicationEngine.leggTilFastOppholdINorge(
    sakId: String,
    behandlingId: String,
    vurdering: String = "VilkårOppfylt",
    begrunnelse: String = "Vurdering av fastOppholdINorge er lagt til automatisk av LeggTilFastOppholdINorge.kt",
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
                    "fastOppholdINorge":{
                      "status":"$vurdering",
                      "begrunnelse":"$begrunnelse"
                    },
                    "institusjonsopphold":null,
                    "formue":null,
                    "personligOppmøte":null
                  }
            """.trimIndent(),
        )
    }.apply {
        status shouldBe HttpStatusCode.OK
        response.contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
    }.response.content!!
}
