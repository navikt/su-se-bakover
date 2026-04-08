package no.nav.su.se.bakover.web.regulering

import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.domain.regulering.ReguleringKjøring
import no.nav.su.se.bakover.test.application.defaultRequest

internal fun hentReguleringKjøringRequest(client: HttpClient): List<ReguleringKjøring> {
    return runBlocking {
        defaultRequest(
            HttpMethod.Get,
            "/reguleringer/test/kjøringer",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
        ).let {
            if (it.status != HttpStatusCode.OK) {
                throw IllegalStateException("Fant ikke reguleringskjøring: ${it.bodyAsText()}")
            }
            deserialize<List<ReguleringKjøring>>(it.bodyAsText())
        }
    }
}
