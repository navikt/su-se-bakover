package no.nav.su.se.bakover.web.features

import arrow.core.Either
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod
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
    fun `should`() {
        val response = Either.right(
            MicrosoftGraphResponse(
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
                this.suUserContext.user shouldBe response
                this.suUserContext.getNAVIdent() shouldBe "heisann"
            }
        }
    }
}
