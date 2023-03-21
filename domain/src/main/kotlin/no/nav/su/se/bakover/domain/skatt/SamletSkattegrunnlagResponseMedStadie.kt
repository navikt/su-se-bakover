package no.nav.su.se.bakover.domain.skatt

import arrow.core.Either
import arrow.core.left
import arrow.core.right

data class SamletSkattegrunnlagResponseMedStadie(
    val oppslag: Either<SkatteoppslagFeil, Skattegrunnlag.Årsgrunnlag>,
    val stadie: Stadie,
) {
    companion object {
        fun List<SamletSkattegrunnlagResponseMedStadie>.hentMestGyldigeSkattegrunnlag(): Either<SkatteoppslagFeil, Skattegrunnlag.Årsgrunnlag> {
            this.single { it.stadie == Stadie.FASTSATT }.let {
                it.oppslag.mapLeft {
                    when (it.mapTilOmFeilKanSkippesEllerReturneres()) {
                        SkatteoppslagFeilMediator.KanSkippes -> Unit
                        SkatteoppslagFeilMediator.SkalReturneres -> return it.left()
                    }
                }.map {
                    return it.right()
                }
            }
            this.single { it.stadie == Stadie.OPPGJØR }.let {
                it.oppslag.mapLeft {
                    when (it.mapTilOmFeilKanSkippesEllerReturneres()) {
                        SkatteoppslagFeilMediator.KanSkippes -> Unit
                        SkatteoppslagFeilMediator.SkalReturneres -> return it.left()
                    }
                }.map {
                    return it.right()
                }
            }
            this.single { it.stadie == Stadie.UTKAST }.let {
                it.oppslag.mapLeft {
                    when (it.mapTilOmFeilKanSkippesEllerReturneres()) {
                        SkatteoppslagFeilMediator.KanSkippes -> Unit
                        SkatteoppslagFeilMediator.SkalReturneres -> return it.left()
                    }
                }.map {
                    return it.right()
                }
            }

            return SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left()
        }
    }
}
