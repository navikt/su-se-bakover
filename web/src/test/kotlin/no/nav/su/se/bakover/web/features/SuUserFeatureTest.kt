package no.nav.su.se.bakover.web.features

import arrow.core.Either
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.client.person.MicrosoftGraphResponse
import no.nav.su.se.bakover.web.TestClientsBuilder.testClients
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SuUserFeatureTest {

    @Test
    fun `should run in the application pipeline`() {
        val response = MicrosoftGraphResponse(
            onPremisesSamAccountName = "heisann",
            displayName = "dp",
            givenName = "gn",
            mail = "mail",
            officeLocation = "ol",
            surname = "sn",
            userPrincipalName = "upn",
            id = "id",
            jobTitle = "jt"
        )

        withTestApplication({
            testSusebakover(
                clients = testClients.copy(
                    microsoftGraphApiClient = object : MicrosoftGraphApiOppslag {
                        override fun hentBrukerinformasjon(userToken: String): Either<String, MicrosoftGraphResponse> =
                            Either.right(response)
                    }
                )
            )
        }) {
            defaultRequest(HttpMethod.Get, "/saker/${UUID.randomUUID()}").apply {
                this.suUserContext.user shouldBe response
                this.suUserContext.getNAVIdent() shouldBe "heisann"
            }
        }
    }

    @Test
    fun `should not respond with 500 if fetching from Microsoft Graph API fails but endpoint does not access it`() {
        val response = Either.left(
            "Feil"
        )

        withTestApplication({
            testSusebakover(
                clients = testClients.copy(
                    microsoftGraphApiClient = object : MicrosoftGraphApiOppslag {
                        override fun hentBrukerinformasjon(userToken: String): Either<String, MicrosoftGraphResponse> = response
                    }
                )
            )
        }) {
            defaultRequest(HttpMethod.Get, "/saker/${UUID.randomUUID()}").apply {
                this.response.status() shouldBe HttpStatusCode.NotFound
            }
        }
    }

    @Test
    fun `should respond with 500 if fetching from Microsoft Graph API and endpoint needs that data`() {
        val response = Either.left(
            "Feil"
        )

        withTestApplication({
            testSusebakover(
                clients = testClients.copy(
                    microsoftGraphApiClient = object : MicrosoftGraphApiOppslag {
                        override fun hentBrukerinformasjon(userToken: String): Either<String, MicrosoftGraphResponse> = response
                    }
                )
            )
        }) {
            defaultRequest(HttpMethod.Get, "/me").apply {
                this.response.status() shouldBe HttpStatusCode.InternalServerError
                shouldThrow<KallMotMicrosoftGraphApiFeilet> { suUserContext.user }
                shouldThrow<KallMotMicrosoftGraphApiFeilet> { suUserContext.getNAVIdent() }
            }
        }
    }
}
