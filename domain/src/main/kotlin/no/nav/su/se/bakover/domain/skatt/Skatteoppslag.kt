package no.nav.su.se.bakover.domain.skatt

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.YearRange
import java.time.Year

interface Skatteoppslag {
    fun hentSamletSkattegrunnlag(
        fnr: Fnr,
        år: Year,
    ): SamletSkattegrunnlagForÅr

    fun hentSamletSkattegrunnlagForÅrsperiode(
        fnr: Fnr,
        yearRange: YearRange,
    ): NonEmptyList<SamletSkattegrunnlagForÅr>
}
