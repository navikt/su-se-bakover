package no.nav.su.se.bakover.domain.skatt

sealed interface SkatteoppslagFeil {
    data class Nettverksfeil(val throwable: Throwable) : SkatteoppslagFeil
    object FantIkkeSkattegrunnlagForPersonOgÅr : SkatteoppslagFeil
    data class UkjentFeil(val throwable: Throwable) : SkatteoppslagFeil
    object ManglerRettigheter : SkatteoppslagFeil
}


fun SkatteoppslagFeil.mapTilOmFeilKanSkippesEllerReturneres(): SkatteoppslagFeilMediator {
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
