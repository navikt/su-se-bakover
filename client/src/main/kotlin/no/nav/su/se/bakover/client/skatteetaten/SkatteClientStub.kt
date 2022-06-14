package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.client.AccessToken
import no.nav.su.se.bakover.domain.Fnr

class SkatteClientStub : Skatteoppslag {
    override fun hentSamletSkattegrunnlag(accessToken: AccessToken, fnr: Fnr): Either<SkatteoppslagFeil, no.nav.su.se.bakover.domain.Skattegrunnlag> {
        return no.nav.su.se.bakover.domain.Skattegrunnlag(
            fnr = Fnr(fnr = "04900148157"),
            inntektsår = 2021,
            grunnlag = listOf(),
            skatteoppgjoersdato = null,
        ).right()
    }
}
