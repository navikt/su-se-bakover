package no.nav.su.se.bakover.web.kontrollsamtale

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.test.application.defaultRequest

internal fun annullerKontrollsamtale(sakId: String, kontrollsamtaleId: String, client: HttpClient): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Delete,
            "/saker/$sakId/kontrollsamtaler/$kontrollsamtaleId",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
        ).apply {
            status shouldBe HttpStatusCode.OK
        }.bodyAsText()
    }
}
