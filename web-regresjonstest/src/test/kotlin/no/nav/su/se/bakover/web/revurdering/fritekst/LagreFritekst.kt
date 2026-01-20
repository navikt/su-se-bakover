package no.nav.su.se.bakover.web.revurdering.fritekst

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
import no.nav.su.se.bakover.domain.fritekst.FritekstType
import no.nav.su.se.bakover.test.application.defaultRequest
import no.nav.su.se.bakover.web.søknadsbehandling.SKIP_STEP

internal fun lagreFritekst(
    sakId: String,
    referanseId: String,
    type: FritekstType = FritekstType.VEDTAKSBREV_REVURDERING,
    fritekst: String = "Dette er en fritekst",
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    url: String = "fritekst/lagre",
    client: HttpClient,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            url,
            listOf(brukerrolle),
            client = client,
        ) {
            setBody(
                //language=JSON
                """
              {
                "referanseId": "$referanseId",
                "sakId": "$sakId",
                "type": "$type",
                "fritekst": "$fritekst"
              }
                """.trimIndent(),
            )
        }.apply {
            withClue("body=${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.OK
                contentType() shouldBe ContentType.parse("application/json")
            }
        }
        SKIP_STEP
    }
}
