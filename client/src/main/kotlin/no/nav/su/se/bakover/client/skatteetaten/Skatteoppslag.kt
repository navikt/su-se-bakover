package no.nav.su.se.bakover.client.skatteetaten

import no.nav.su.se.bakover.common.Fnr
import java.time.Year

interface Skatteoppslag {
    fun hentSamletSkattegrunnlag(
        fnr: Fnr,
        inntektsÅr: Year,
    ): List<SamletSkattegrunnlagResponseMedStadie>

//    fun hentSamletSkattegrunnlag(
//        fnr: Fnr,
//        inntektsÅr: YearRange,
//    ): Either<SkatteoppslagFeil, Skattegrunnlag>
}
