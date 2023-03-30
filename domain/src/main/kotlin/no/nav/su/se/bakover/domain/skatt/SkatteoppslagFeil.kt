package no.nav.su.se.bakover.domain.skatt

import no.nav.su.se.bakover.common.YearRange
import no.nav.su.se.bakover.common.toRange
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import java.time.Year

sealed interface SkatteoppslagFeil {
    data class Nettverksfeil(val throwable: Throwable) : SkatteoppslagFeil {
        override fun equals(other: Any?): Boolean {
            return other != null &&
                other is Nettverksfeil &&
                this.throwable::class == other.throwable::class &&
                this.throwable.message == other.throwable.message
        }
    }

    data class FantIkkeSkattegrunnlagForPersonOgÅr(val år: YearRange) : SkatteoppslagFeil {
        constructor(år: Year) : this(år.toRange())
    }
    data class UkjentFeil(val throwable: Throwable) : SkatteoppslagFeil
    object ManglerRettigheter : SkatteoppslagFeil
    data class PersonFeil(val feil: KunneIkkeHentePerson) : SkatteoppslagFeil
}

/**
 * Siden vi gjør et kall for hver stadie, for et år, så er vi kun interessert i å sende tilbake en faktisk
 * feil med gang den oppdages. Dersom vi bare ikke finner skattegrunnlaget for en angitt år/stadie, så bare fortsetter
 * vi til neste år/stadie og sjekker
 */
fun SkatteoppslagFeil.mapTilOmFeilKanSkippesEllerReturneres(): SkatteoppslagFeilMediator {
    return when (this) {
        is SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr -> SkatteoppslagFeilMediator.KanSkippes
        is SkatteoppslagFeil.ManglerRettigheter -> SkatteoppslagFeilMediator.SkalReturneres
        is SkatteoppslagFeil.Nettverksfeil -> SkatteoppslagFeilMediator.SkalReturneres
        is SkatteoppslagFeil.UkjentFeil -> SkatteoppslagFeilMediator.SkalReturneres
        is SkatteoppslagFeil.PersonFeil -> SkatteoppslagFeilMediator.SkalReturneres
    }
}

sealed interface SkatteoppslagFeilMediator {
    object KanSkippes : SkatteoppslagFeilMediator
    object SkalReturneres : SkatteoppslagFeilMediator
}