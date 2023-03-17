package no.nav.su.se.bakover.domain.skatt

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.domain.skatt.SamletSkattegrunnlagResponseMedStadie.Companion.hentMestGyldigeSkattegrunnlag
import java.time.Year

data class SamletSkattegrunnlagResponseMedYear(
    val skatteResponser: List<SamletSkattegrunnlagResponseMedStadie>,
    val år: Year,
) {

    fun hentMestGyldigeSkattegrunnlag(): Either<SkatteoppslagFeil, Skattegrunnlag.Årsgrunnlag> =
        this.skatteResponser.hentMestGyldigeSkattegrunnlag()

    companion object {
        fun List<SamletSkattegrunnlagResponseMedYear>.hentMestGyldigeSkattegrunnlag(): Either<SkatteoppslagFeil, Skattegrunnlag.Årsgrunnlag> {
            val sortert = this.sortedByDescending { it.år }

            for (i in sortert) {
                when (val res = i.skatteResponser.hentMestGyldigeSkattegrunnlag()) {
                    is Either.Left -> when (res.value.mapTilOmFeilKanSkippesEllerReturneres()) {
                        SkatteoppslagFeilMediator.KanSkippes -> Unit
                        SkatteoppslagFeilMediator.SkalReturneres -> return res.value.left()
                    }

                    is Either.Right -> return res
                }
            }

            return SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left()
        }
    }
}
