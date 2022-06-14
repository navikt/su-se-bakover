package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.client.AccessToken
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Skattegrunnlag

class SkatteClientStub : Skatteoppslag {
    override fun hentSamletSkattegrunnlag(accessToken: AccessToken, fnr: Fnr): Either<SkatteoppslagFeil, Skattegrunnlag> {
        return Skattegrunnlag(
            fnr = Fnr(fnr = "04900148157"),
            inntektsår = 2021,
            grunnlag = listOf(
                Skattegrunnlag.Grunnlag(navn = "a", beløp = 1000, kategori = listOf(Skattegrunnlag.Kategori.INNTEKT)),
                Skattegrunnlag.Grunnlag(navn = "b", beløp = 1238, kategori = listOf(Skattegrunnlag.Kategori.FORMUE)),
                Skattegrunnlag.Grunnlag(navn = "c", beløp = 1238, kategori = listOf(Skattegrunnlag.Kategori.FORMUESFRADRAG)),
            ),
            skatteoppgjoersdato = null,
        ).right()
    }
}
