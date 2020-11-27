package no.nav.su.se.bakover.client.stubs.person

import arrow.core.Either
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.client.person.MicrosoftGraphResponse

class MicrosoftGraphApiClientStub :
    MicrosoftGraphApiOppslag {
    override fun hentBrukerinformasjon(userToken: String): Either<String, MicrosoftGraphResponse> = Either.Right(
        MicrosoftGraphResponse(
            onPremisesSamAccountName = "navident",
            displayName = "dn",
            givenName = "gn",
            mail = "m",
            officeLocation = "ol",
            surname = "sn",
            userPrincipalName = "upn",
            id = "id",
            jobTitle = "jt"
        )
    )

    override fun hentBrukerinformasjonForNavIdent(navIdent: String): Either<String, MicrosoftGraphResponse> = Either.Right(
        MicrosoftGraphResponse(
            onPremisesSamAccountName = "navident",
            displayName = "dn",
            givenName = "gn",
            mail = "m",
            officeLocation = "ol",
            surname = "sn",
            userPrincipalName = "upn",
            id = "id",
            jobTitle = "jt"
        )
    )
}
