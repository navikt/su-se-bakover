package no.nav.su.se.bakover.web.søknadsbehandling.bosituasjon

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

internal fun ApplicationTestBuilder.taStillingTilEps(
    sakId: String,
    behandlingId: String,
    epsFnr: String? = null,
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/behandlinger/$behandlingId/grunnlag/bosituasjon/eps",
            listOf(brukerrolle),
        ) {
            setBody(
                //language=JSON
                """
              {
                "epsFnr":${if (epsFnr == null) null else "$epsFnr"}
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

internal fun ApplicationTestBuilder.fullførBosituasjon(
    sakId: String,
    behandlingId: String,
    bosituasjon: String = "BOR_ALENE",
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/behandlinger/$behandlingId/grunnlag/bosituasjon/fullfør",
            listOf(brukerrolle),
        ) {
            setBody(
                //language=JSON
                """
                  {
                    "bosituasjon":"$bosituasjon"
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
