package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.Either
import arrow.core.Nel
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import java.time.Year

interface Skatteoppslag {
    fun hentSamletSkattegrunnlag(
        fnr: Fnr,
        inntektsÅr: Year,
        stadie: Nel<Stadie>
    ): Either<SkatteoppslagFeil, Skattegrunnlag>

//    fun hentSamletSkattegrunnlag(
//        fnr: Fnr,
//        inntektsÅr: YearRange,
//    ): Either<SkatteoppslagFeil, Skattegrunnlag>
}
