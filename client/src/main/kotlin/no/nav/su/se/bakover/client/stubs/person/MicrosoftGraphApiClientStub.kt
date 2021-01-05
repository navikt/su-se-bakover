package no.nav.su.se.bakover.client.stubs.person

import arrow.core.Either
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslagFeil
import no.nav.su.se.bakover.client.person.MicrosoftGraphResponse
import java.util.UUID

object MicrosoftGraphApiClientStub : MicrosoftGraphApiOppslag {
    private val response = MicrosoftGraphResponse(
        onPremisesSamAccountName = "Z990Lokal",
        displayName = "Testbruker, Lokal",
        givenName = "Lokal",
        mail = "lokal.testbruker@nav.no",
        officeLocation = "2990 IT-AVDELINGEN",
        surname = "Testbruker",
        userPrincipalName = "lokal.testbruker@nav.no",
        id = UUID.randomUUID().toString(),
        jobTitle = "Z9902990"
    )

    override fun hentBrukerinformasjon(userToken: String): Either<MicrosoftGraphApiOppslagFeil, MicrosoftGraphResponse> = Either.Right(response)
    override fun hentBrukerinformasjonForNavIdent(navIdent: String): Either<MicrosoftGraphApiOppslagFeil, MicrosoftGraphResponse> = Either.Right(response)
}
