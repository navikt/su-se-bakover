package no.nav.su.se.bakover.web.søknadsbehandling.brevvalg

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.revurdering.brev.LeggTilBrevvalgRequest
import no.nav.su.se.bakover.test.application.defaultRequest
import no.nav.su.se.bakover.web.routes.søknadsbehandling.LeggTilBrevvalgSøknadsbehandlingBody

internal fun leggTilBrevvalg(
    sakId: String,
    behandlingId: String,
    valg: LeggTilBrevvalgRequest.Valg = LeggTilBrevvalgRequest.Valg.SEND,
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    client: HttpClient,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/behandlinger/$behandlingId/brevvalg",
            listOf(brukerrolle),
            client = client,
        ) {
            setBody(serialize(LeggTilBrevvalgSøknadsbehandlingBody(valg = valg)))
        }.apply {
            withClue("body=${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.Created
                contentType() shouldBe ContentType.parse("application/json")
            }
        }.bodyAsText()
    }
}
