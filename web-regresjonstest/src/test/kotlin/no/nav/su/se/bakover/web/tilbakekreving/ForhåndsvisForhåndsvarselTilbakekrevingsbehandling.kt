package no.nav.su.se.bakover.web.tilbakekreving

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData

fun forhåndsvisForhåndsvarselTilbakekreving(
    sakId: String,
    tilbakekrevingsbehandlingId: String,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.OK,
    client: HttpClient,
    verifiserRespons: Boolean = true,
    saksversjon: Long,
    fritekst: String? = "Regresjonstest: Fritekst til forhåndsvarsel under tilbakekrevingsbehandling.",
): String {
    return runBlocking {
        SharedRegressionTestData.defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/tilbakekreving/$tilbakekrevingsbehandlingId/forhandsvarsel/forhandsvis",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
        ) {
            setBody(
                """
            {
                "versjon": $saksversjon,
                "fritekst": ${fritekst?.let { "\"$fritekst\"" } ?: "null"}
            }
                """.trimIndent(),
            )
        }.apply {
            withClue("Kunne ikke forhåndsvise forhåndsvarsel under tilbakekrevingsbehandling: ${this.bodyAsText()}") {
                status shouldBe expectedHttpStatusCode
                headers["Content-Type"] shouldBe "application/pdf"
            }
        }.bodyAsText().also {
            if (verifiserRespons) {
                verifiserForhåndsvisForhåndsvarselTilbakekrevingsbehandlingRespons(
                    actual = it,
                )
            }
        }
    }
}

fun verifiserForhåndsvisForhåndsvarselTilbakekrevingsbehandlingRespons(
    actual: String,
) {
    actual.shouldStartWith("%PDF-1.0")
}
