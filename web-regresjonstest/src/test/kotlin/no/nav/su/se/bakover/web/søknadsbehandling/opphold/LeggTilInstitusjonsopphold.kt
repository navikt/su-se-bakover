package no.nav.su.se.bakover.web.søknadsbehandling.opphold

import io.kotest.matchers.shouldBe
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.contentType
import io.ktor.server.testing.setBody
import no.nav.su.se.bakover.domain.bruker.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest

/**
- [vurdering] se [no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon.Institusjonsopphold.Status]
 */
internal fun TestApplicationEngine.leggTilInstitusjonsopphold(
    sakId: String,
    behandlingId: String,
    vurdering: String = "VilkårOppfylt",
    begrunnelse: String = "Vurdering av institusjonsopphold er lagt til automatisk av LeggTilInstitusjonsopphold.kt",
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
                    "institusjonsopphold":{
                      "status":"$vurdering",
                      "begrunnelse":"$begrunnelse"
                    },
                    "formue":null,
                    "personligOppmøte":null
                  }
            """.trimIndent(),
        )
    }.apply {
        response.status() shouldBe HttpStatusCode.OK
        response.contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
    }.response.content!!
}
