package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.Either
import no.nav.su.se.bakover.client.AccessToken
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.SamletSkattegrunnlag

interface Skatteoppslag {
    fun hentSamletSkattegrunnlag(accessToken: AccessToken, fnr: Fnr): Either<SkatteoppslagFeil, SamletSkattegrunnlag>
}

sealed class SkatteoppslagFeil {
    data class KunneIkkeHenteSkattedata(val statusCode: Int, val feilmelding: String) : SkatteoppslagFeil()
    data class Nettverksfeil(val throwable: Throwable) : SkatteoppslagFeil()
}
