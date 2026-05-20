package no.nav.su.se.bakover.web.regulering

import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.regulering.ReguleringStatus
import no.nav.su.se.bakover.test.application.defaultRequest
import no.nav.su.se.bakover.web.routes.regulering.ProduserReguleringStatusBody

internal fun hentReguleringStatusRequest(client: HttpClient, år: Int): ReguleringStatus {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/reguleringer/status-regulering-utestaende",
            listOf(Brukerrolle.Drift),
            client = client,
            body = serialize(
                ProduserReguleringStatusBody(
                    aar = år,
                    asynk = false,
                ),
            ),
        ).let {
            if (it.status != HttpStatusCode.OK) {
                throw IllegalStateException("Fant ikke reguleringsstatus: ${it.bodyAsText()}")
            }
            deserialize<ReguleringStatus>(it.bodyAsText())
        }
    }
}
