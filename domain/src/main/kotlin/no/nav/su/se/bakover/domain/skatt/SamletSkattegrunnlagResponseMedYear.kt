package no.nav.su.se.bakover.domain.skatt

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.common.YearRange
import no.nav.su.se.bakover.domain.skatt.SamletSkattegrunnlagResponseMedStadie.Companion.hentMestGyldigeSkattegrunnlag
import java.time.Year

data class SamletSkattegrunnlagResponseMedYear(
    val skatteResponser: List<SamletSkattegrunnlagResponseMedStadie>,
    val år: Year,
) {

    fun hentMestGyldigeSkattegrunnlag(): Either<SkatteoppslagFeil, Skattegrunnlag.Årsgrunnlag> {
        return this.skatteResponser.hentMestGyldigeSkattegrunnlag(år)
    }

    companion object {
        fun List<SamletSkattegrunnlagResponseMedYear>.hentMestGyldigeSkattegrunnlag(): Either<SkatteoppslagFeil, Skattegrunnlag.Årsgrunnlag> {
            this.sortedByDescending { it.år }.forEach {
                when (val res = it.skatteResponser.hentMestGyldigeSkattegrunnlag(it.år)) {
                    is Either.Left -> when (res.value.mapTilOmFeilKanSkippesEllerReturneres()) {
                        SkatteoppslagFeilMediator.KanSkippes -> Unit
                        SkatteoppslagFeilMediator.SkalReturneres -> return res.value.left()
                    }

                    is Either.Right -> return res
                }
            }

            return SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr(this.toYearRange()).left()
        }
    }
}

fun List<SamletSkattegrunnlagResponseMedYear>.toYearRange(): YearRange {
    return YearRange(this.minOf { it.år }, this.maxOf { it.år })
}
