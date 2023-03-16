package no.nav.su.se.bakover.client.skatteetaten

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import no.nav.su.se.bakover.domain.skatt.Stadie

data class SamletSkattegrunnlagResponseMedStadie(
    val oppslag: Either<SkatteoppslagFeil, Skattegrunnlag>,
    val stadie: Stadie,
) {
    companion object {
        fun List<SamletSkattegrunnlagResponseMedStadie>.hentMestGyldigeSkattegrunnlag(): Either<SkatteoppslagFeil, Skattegrunnlag> {
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

        private fun SkatteoppslagFeil.mapTilOmFeilKanSkippesEllerReturneres(): SkatteoppslagFeilMediator {
            return when (this) {
                SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr -> SkatteoppslagFeilMediator.KanSkippes
                SkatteoppslagFeil.ManglerRettigheter -> SkatteoppslagFeilMediator.SkalReturneres
                is SkatteoppslagFeil.Nettverksfeil -> SkatteoppslagFeilMediator.SkalReturneres
                is SkatteoppslagFeil.UkjentFeil -> SkatteoppslagFeilMediator.SkalReturneres
            }
        }

        sealed interface SkatteoppslagFeilMediator {
            object KanSkippes : SkatteoppslagFeilMediator
            object SkalReturneres : SkatteoppslagFeilMediator
        }
    }
}
