package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.client.skatteetaten.SamletSkattegrunnlagResponseMedStadie.Companion.hentMestGyldigeSkattegrunnlag
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import java.time.Year
import java.util.concurrent.ConcurrentLinkedQueue

data class SamletSkattegrunnlagResponseMedYear(
    val skatteResponser: List<SamletSkattegrunnlagResponseMedStadie>,
    val år: Year,
) {
    companion object {
        fun ConcurrentLinkedQueue<SamletSkattegrunnlagResponseMedYear>.hentMestGyldigeSkattegrunnlag(): Either<SkatteoppslagFeil, Skattegrunnlag> {
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
