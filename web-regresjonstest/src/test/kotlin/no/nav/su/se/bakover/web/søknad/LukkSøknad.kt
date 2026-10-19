package no.nav.su.se.bakover.web.søknad

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.test.application.defaultRequest
import no.nav.su.se.bakover.web.komponenttest.AppComponents
import no.nav.su.se.bakover.web.routes.søknad.lukk.LukketJson

internal fun AppComponents.lukkSøknad(
    søknadId: String,
    client: HttpClient,
    expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.OK,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/soknad/$søknadId/lukk",
            listOf(Brukerrolle.Saksbehandler),
            client = client,
        ) {
            setBody(
                serialize(
                    LukketJson.AvvistJson(
                        type = LukketJson.LukketType.AVSLAG,
                        brevConfig = null,
                    ),
                ),
            )
        }.apply {
            withClue("Kunne lukke søknad: ${this.bodyAsText()}") {
                status shouldBe expectedHttpStatusCode
            }
        }.bodyAsText()
    }
}
