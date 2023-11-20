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

fun forhåndsvisAvbrytTilbakekreving(
    sakId: String,
    tilbakekrevingsbehandlingId: String,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.OK,
    client: HttpClient,
    verifiserRespons: Boolean = true,
    saksversjon: Long,
): String {
    return runBlocking {
        SharedRegressionTestData.defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/tilbakekreving/$tilbakekrevingsbehandlingId/avbryt/forhandsvis",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
        ) {
            setBody("""{"versjon": $saksversjon, "fritekst": "friteksten"}""")
        }.apply {
            withClue("Kunne ikke forhåndsvise avbryt tilbakekrevingsbehandling: ${this.bodyAsText()}") {
                status shouldBe expectedHttpStatusCode
            }
        }.bodyAsText().also {
            if (verifiserRespons) {
                verifiserForhåndsvisAvbrytTilbakekrevingRespons(actual = it)
            }
        }
    }
}

fun verifiserForhåndsvisAvbrytTilbakekrevingRespons(actual: String) {
    actual.shouldStartWith("%PDF-1.0")
}
