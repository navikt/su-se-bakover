package no.nav.su.se.bakover.web.kontrollsamtale

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.test.application.defaultRequest

internal fun hentKontrollsamtalerForSakId(sakId: String, client: HttpClient): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Get,
            "/saker/$sakId/kontrollsamtaler",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
        ).apply {
            status shouldBe HttpStatusCode.OK
        }.bodyAsText()
    }
}

internal fun hentNestePlanlagteKontrollsamtalerForSakId(sakId: String, client: HttpClient): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Get,
            "/saker/$sakId/kontrollsamtaler/hent",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
        ).apply {
            status shouldBe HttpStatusCode.OK
        }.bodyAsText()
    }
}
