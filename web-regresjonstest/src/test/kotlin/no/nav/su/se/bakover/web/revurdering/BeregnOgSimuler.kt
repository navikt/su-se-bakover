package no.nav.su.se.bakover.web.revurdering

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.test.application.defaultRequest

internal fun beregnOgSimuler(
    sakId: String,
    behandlingId: String,
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    url: String = "/saker/$sakId/revurderinger/$behandlingId/beregnOgSimuler",
    client: HttpClient,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            url,
            listOf(brukerrolle),
            client = client,
        ).apply {
            withClue("body=${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.Created
            }
            contentType() shouldBe ContentType.parse("application/json")
        }.bodyAsText()
    }
}
