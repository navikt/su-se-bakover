package no.nav.su.se.bakover.web.kontrollsamtale

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.kontrollsamtale.infrastructure.web.OppdaterInnkallingsmånedPåKontrollsamtaleBody
import no.nav.su.se.bakover.kontrollsamtale.infrastructure.web.OppdaterStatusPåKontrollsamtaleBody
import no.nav.su.se.bakover.test.application.defaultRequest

internal fun oppdaterInnkallingsmånedPåKontrollsamtale(
    sakId: String,
    kontrollsamtaleId: String,
    innkallingsmåned: String,
    client: HttpClient,
    expectedStatus: HttpStatusCode = HttpStatusCode.OK,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Patch,
            "/saker/$sakId/kontrollsamtaler/$kontrollsamtaleId/innkallingsmåned",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
        ) {
            setBody(serialize(OppdaterInnkallingsmånedPåKontrollsamtaleBody(innkallingsmåned = innkallingsmåned)))
        }.apply {
            status shouldBe expectedStatus
        }.bodyAsText()
    }
}

internal fun oppdaterStatusPåKontrollsamtale(
    sakId: String,
    kontrollsamtaleId: String,
    status: String,
    journalpostId: String? = null,
    client: HttpClient,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Patch,
            "/saker/$sakId/kontrollsamtaler/$kontrollsamtaleId/status",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
        ) {
            setBody(serialize(OppdaterStatusPåKontrollsamtaleBody(status = status, journalpostId = journalpostId)))
        }.apply {
            status shouldBe HttpStatusCode.OK
        }.bodyAsText()
    }
}
