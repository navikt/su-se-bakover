package no.nav.su.se.bakover.service.skatt

import arrow.core.Either
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.YearRange
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import no.nav.su.se.bakover.domain.skatt.SkatteoppslagFeil

interface SkatteService {
    fun hentSamletSkattegrunnlag(
        fnr: Fnr,
    ): Either<KunneIkkeHenteSkattemelding, Skattegrunnlag>

    fun hentSamletSkattegrunnlagForÅr(
        fnr: Fnr,
        yearRange: YearRange
    ): Either<KunneIkkeHenteSkattemelding, Skattegrunnlag>
}

sealed interface KunneIkkeHenteSkattemelding {
    data class KallFeilet(val feil: SkatteoppslagFeil) : KunneIkkeHenteSkattemelding
}
