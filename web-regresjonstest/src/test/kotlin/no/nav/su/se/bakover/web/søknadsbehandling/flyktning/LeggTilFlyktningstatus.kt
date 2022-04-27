package no.nav.su.se.bakover.web.søknadsbehandling.flyktning

import io.ktor.http.HttpMethod
import io.ktor.server.testing.ApplicationTestBuilder
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest

/**
- [resultat] se [no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon.Flyktning.Status]
 */
internal suspend fun ApplicationTestBuilder.leggTilFlyktningstatus(
    sakId: String,
    behandlingId: String,
    resultat: String = "VilkårOppfylt",
    begrunnelse: String = "Vurdering av flyktningstatus er lagt til automatisk av LeggTilFlyktningstatus.kt",
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
): String {

    return this.defaultRequest(
        method = HttpMethod.Patch,
        uri = "/saker/$sakId/behandlinger/$behandlingId/informasjon",
        roller = listOf(brukerrolle),
    ) {
       /* addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        setBody(
            //language=JSON
            """
                  {
                    "flyktning":{
                      "status":"$resultat",
                      "begrunnelse":"$begrunnelse"
                    },
                    "lovligOpphold":null,
                    "fastOppholdINorge":null,
                    "institusjonsopphold":null,
                    "formue":null,
                    "personligOppmøte":null
                  }
            """.trimIndent(),
        )
    }.apply {
        status shouldBe HttpStatusCode.OK
        response.contentType shouldBe ContentType.parse("application/json; charset=UTF-8")*/
    }
}
