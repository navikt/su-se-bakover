package no.nav.su.se.bakover.domain.skatt

import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson

sealed interface SkatteoppslagFeil {
    data class Nettverksfeil(val throwable: Throwable) : SkatteoppslagFeil
    object FantIkkeSkattegrunnlagForPersonOgÅr : SkatteoppslagFeil
    data class UkjentFeil(val throwable: Throwable) : SkatteoppslagFeil
    object ManglerRettigheter : SkatteoppslagFeil
    data class PersonFeil(val feil: KunneIkkeHentePerson): SkatteoppslagFeil
}

/**
 * Siden vi gjør et kall for hver stadie, for et år, så er vi kun interessert i å sende tilbake en faktisk
 * feil med gang den oppdages. Dersom vi bare ikke finner skattegrunnlaget for en angitt år/stadie, så bare fortsetter
 * vi til neste år/stadie og sjekker
 */
fun SkatteoppslagFeil.mapTilOmFeilKanSkippesEllerReturneres(): SkatteoppslagFeilMediator {
    return when (this) {
        SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr -> SkatteoppslagFeilMediator.KanSkippes
        SkatteoppslagFeil.ManglerRettigheter -> SkatteoppslagFeilMediator.SkalReturneres
        is SkatteoppslagFeil.Nettverksfeil -> SkatteoppslagFeilMediator.SkalReturneres
        is SkatteoppslagFeil.UkjentFeil -> SkatteoppslagFeilMediator.SkalReturneres
        is SkatteoppslagFeil.PersonFeil -> SkatteoppslagFeilMediator.SkalReturneres
    }
}

sealed interface SkatteoppslagFeilMediator {
    object KanSkippes : SkatteoppslagFeilMediator
    object SkalReturneres : SkatteoppslagFeilMediator
}
