package no.nav.su.se.bakover.domain.skatt

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.YearRange
import java.time.Year

interface Skatteoppslag {
    fun hentSamletSkattegrunnlag(
        fnr: Fnr,
        år: Year,
    ): SamletSkattegrunnlagResponseMedYear

    fun hentSamletSkattegrunnlagForÅrsperiode(
        fnr: Fnr,
        yearRange: YearRange
    ): List<SamletSkattegrunnlagResponseMedYear>


//    fun hentSamletSkattegrunnlag(
//        fnr: Fnr,
//        inntektsÅr: YearRange,
//    ): Either<SkatteoppslagFeil, Skattegrunnlag>
}
