package no.nav.su.se.bakover.client.skatteetaten

import no.nav.su.se.bakover.common.tid.YearRange
import no.nav.su.se.bakover.common.tid.toRange
import vilkår.skatt.domain.KunneIkkeHenteSkattemelding
import java.time.Year

/**
 * Vi gjør ikke wrapping trikset med [KunneIkkeHenteSkattemelding] fordi vi ikke vil at exception skal blø ut
 */
internal sealed interface SkatteoppslagFeil {

    fun tilKunneIkkeHenteSkattemelding(): KunneIkkeHenteSkattemelding = when (this) {
        is FantIkkeSkattegrunnlagForPersonOgÅr -> KunneIkkeHenteSkattemelding.FinnesIkke
        ManglerRettigheter -> KunneIkkeHenteSkattemelding.ManglerRettigheter
        is Nettverksfeil -> KunneIkkeHenteSkattemelding.Nettverksfeil
        is UkjentFeil -> KunneIkkeHenteSkattemelding.UkjentFeil
        OppslagetInneholderUgyldigData -> KunneIkkeHenteSkattemelding.OppslagetInneholdtUgyldigData
    }

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
    data object ManglerRettigheter : SkatteoppslagFeil

    data object OppslagetInneholderUgyldigData : SkatteoppslagFeil
}
