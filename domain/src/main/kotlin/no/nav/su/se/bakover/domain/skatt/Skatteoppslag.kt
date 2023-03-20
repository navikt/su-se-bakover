package no.nav.su.se.bakover.domain.skatt

import arrow.core.Either
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.YearRange
import java.time.Year

interface Skatteoppslag {
    fun hentSamletSkattegrunnlag(
        fnr: Fnr,
        år: Year,
    ): Either<SkatteoppslagFeil, SamletSkattegrunnlagResponseMedYear>

    fun hentSamletSkattegrunnlagForÅrsperiode(
        fnr: Fnr,
        yearRange: YearRange
    ): Either<SkatteoppslagFeil, List<SamletSkattegrunnlagResponseMedYear>>


//    fun hentSamletSkattegrunnlag(
//        fnr: Fnr,
//        inntektsÅr: YearRange,
//    ): Either<SkatteoppslagFeil, Skattegrunnlag>
}
