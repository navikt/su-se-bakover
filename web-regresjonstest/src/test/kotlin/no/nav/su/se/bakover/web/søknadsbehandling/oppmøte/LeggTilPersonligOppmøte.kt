package no.nav.su.se.bakover.web.søknadsbehandling.oppmøte

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

internal fun ApplicationTestBuilder.leggTilPersonligOppmøte(
    sakId: String,
    behandlingId: String,
    status: String = "MøttPersonlig",
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Patch,
            "/saker/$sakId/behandlinger/$behandlingId/informasjon",
            listOf(brukerrolle),
        ) {
            setBody(
                //language=JSON
                """
                  {
                    "flyktning":null,
                    "lovligOpphold":null,
                    "fastOppholdINorge":null,
                    "institusjonsopphold":null,
                    "personligOppmøte":{
                      "status": "$status"
                    }
                  }
                """.trimIndent(),
            )
        }.apply {
            withClue("body=${this.bodyAsText()}") {
                this.status shouldBe HttpStatusCode.OK
                contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
            }
        }.bodyAsText()
    }
}
