package no.nav.su.se.bakover.service.skatt

import arrow.core.Either
import no.nav.su.se.bakover.client.maskinporten.KunneIkkeHenteToken
import no.nav.su.se.bakover.client.skatteetaten.SkatteoppslagFeil
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import java.time.Clock
import java.time.Year

interface SkatteService {
    fun hentSamletSkattegrunnlag(
        fnr: Fnr,
    ): Either<KunneIkkeHenteSkattemelding, Skattegrunnlag>

}

sealed interface KunneIkkeHenteSkattemelding {
    data class KallFeilet(val feil: SkatteoppslagFeil) : KunneIkkeHenteSkattemelding
}
