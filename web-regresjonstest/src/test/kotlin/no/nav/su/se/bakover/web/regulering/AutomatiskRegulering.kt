package no.nav.su.se.bakover.web.regulering

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.test.application.defaultRequest
import no.nav.su.se.bakover.web.routes.regulering.AutomatiskReguleringBody

internal fun regulerAutomatisk(
    fraOgMed: Måned,
    client: HttpClient,
    body: String = serialize(AutomatiskReguleringBody(fraOgMedMåned = fraOgMed.toString())),
) {
    return runBlocking {
        val correlationId = CorrelationId.generate()
        defaultRequest(
            HttpMethod.Post,
            "/reguleringer/automatisk",
            listOf(Brukerrolle.Drift),
            client = client,
            correlationId = correlationId.toString(),
        ) { setBody(body) }.apply {
            withClue("automatiskReguler feilet: ${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.OK
            }
        }
    }
}
