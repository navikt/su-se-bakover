package no.nav.su.se.bakover.web.søknadsbehandling.ny

import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.TestApplicationEngine
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest

internal fun TestApplicationEngine.nySøknadsbehandling(
    sakId: String,
    søknadId: String,
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/behandlinger",
            listOf(brukerrolle),
        ) {
            setBody("""{"soknadId":"$søknadId"}""")
        }.apply {
            status shouldBe HttpStatusCode.Created
            contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
        }.bodyAsText()
    }
}
