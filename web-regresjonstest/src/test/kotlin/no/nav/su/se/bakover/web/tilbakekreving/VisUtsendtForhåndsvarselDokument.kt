package no.nav.su.se.bakover.web.tilbakekreving

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle

fun visUtsendtForhåndsvarselDokument(
    sakId: String,
    tilbakekrevingsbehandlingId: String,
    dokumentId: String,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.OK,
    client: HttpClient,
    verifiserRespons: Boolean = true,
): String {
    // Dette kallet muterer ikke.
    return runBlocking {
        no.nav.su.se.bakover.test.application.defaultRequest(
            HttpMethod.Get,
            "/saker/$sakId/tilbakekreving/$tilbakekrevingsbehandlingId/forhandsvarsel/$dokumentId",
            listOf(Brukerrolle.Attestant),
            client = client,
        ).apply {
            withClue("Kunne ikke vise utsendt forhåndsvarsel: ${this.bodyAsText()}") {
                status shouldBe expectedHttpStatusCode
            }
        }.bodyAsText().also {
            if (verifiserRespons) {
                verifiserUtsendtForhåndsvarselDokumentTilbakekrevingsbehandlingRespons(actual = it)
            }
        }
    }
}

fun verifiserUtsendtForhåndsvarselDokumentTilbakekrevingsbehandlingRespons(actual: String) {
    actual.shouldStartWith("%PDF-")
}
