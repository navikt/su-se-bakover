package no.nav.su.se.bakover.service.skatt

import arrow.core.Either
import arrow.core.flatMap
import no.nav.su.se.bakover.client.maskinporten.MaskinportenClient
import no.nav.su.se.bakover.client.skatteetaten.Skatteoppslag
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Skattegrunnlag

class SkatteServiceImpl(
    private val skatteClient: Skatteoppslag,
    private val maskinportenClient: MaskinportenClient,
) : SkatteService {

    override fun hentSamletSkattegrunnlag(fnr: Fnr): Either<KunneIkkeHenteSkattemelding, Skattegrunnlag> {
        return maskinportenClient.hentNyttToken()
            .mapLeft { KunneIkkeHenteSkattemelding.KunneIkkeHenteAccessToken(it) }
            .flatMap { tokenResponse ->
                skatteClient.hentSamletSkattegrunnlag(tokenResponse.accessToken, fnr)
                    .mapLeft { feil -> KunneIkkeHenteSkattemelding.KallFeilet(feil) }
                    .map { hentInntektOgFradrag(it) }
            }
    }

    private fun hentInntektOgFradrag(skattegrunnlag: Skattegrunnlag): Skattegrunnlag =
        skattegrunnlag.copy(
            grunnlag = skattegrunnlag.grunnlag.filter {
                it.kategori.contains(Skattegrunnlag.Kategori.INNTEKT) ||
                    it.kategori.contains(Skattegrunnlag.Kategori.FORMUE)
            }
        )
}
