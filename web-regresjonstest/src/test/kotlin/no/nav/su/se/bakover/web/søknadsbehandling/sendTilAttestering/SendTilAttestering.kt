package no.nav.su.se.bakover.web.søknadsbehandling.sendTilAttestering

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

internal fun ApplicationTestBuilder.sendTilAttestering(
    sakId: String,
    behandlingId: String,
    fritekst: String = "Send til attestering er kjørt automatisk av SendTilAttestering.kt",
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/behandlinger/$behandlingId/tilAttestering",
            listOf(brukerrolle),
        ) {
            setBody(
                //language=JSON
                """
              {
                "fritekst": "$fritekst"
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
