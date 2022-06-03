package no.nav.su.se.bakover.web.søknadsbehandling.flyktning

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

/**
- [resultat] se [no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon.Flyktning.Status]
 */
internal fun ApplicationTestBuilder.leggTilFlyktningstatus(
    sakId: String,
    behandlingId: String,
    resultat: String = "VilkårOppfylt",
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
): String {

    return runBlocking {
        defaultRequest(
            method = HttpMethod.Patch,
            uri = "/saker/$sakId/behandlinger/$behandlingId/informasjon",
            roller = listOf(brukerrolle),
        ) {
            setBody(
                //language=JSON
                """
                  {
                    "flyktning":{
                      "status":"$resultat"
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
            withClue("body=${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.OK
                contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
            }
        }.bodyAsText()
    }
}
