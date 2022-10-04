package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.Either
import no.nav.su.se.bakover.client.AccessToken
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.domain.Skattegrunnlag

interface Skatteoppslag {
    fun hentSamletSkattegrunnlag(accessToken: AccessToken, fnr: Fnr): Either<SkatteoppslagFeil, Skattegrunnlag>
}

sealed class SkatteoppslagFeil {
    data class Nettverksfeil(val throwable: Throwable) : SkatteoppslagFeil()
    object FantIkkePerson : SkatteoppslagFeil()
    object FantIkkeSkattegrunnlagForGittÅr : SkatteoppslagFeil()
    object SkattegrunnlagFinnesIkkeLenger : SkatteoppslagFeil()
    object ApiFeil : SkatteoppslagFeil()
    object MappingFeil : SkatteoppslagFeil()
    object DeserializeringFeil : SkatteoppslagFeil()
}
