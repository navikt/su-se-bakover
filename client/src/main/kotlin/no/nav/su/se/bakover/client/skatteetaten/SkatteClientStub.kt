package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.client.AccessToken
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.SamletSkattegrunnlag

class SkatteClientStub : Skatteoppslag {
    override fun hentSamletSkattegrunnlag(accessToken: AccessToken, fnr: Fnr): Either<SkatteoppslagFeil, SamletSkattegrunnlag> {
        return SamletSkattegrunnlag(
            personidentifikator = "",
            inntektsaar = "",
            skjermet = false,
            grunnlag = listOf(),
            skatteoppgjoersdato = null,
        ).right()
    }
}
