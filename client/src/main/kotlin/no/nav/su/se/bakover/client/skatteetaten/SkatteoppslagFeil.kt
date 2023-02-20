package no.nav.su.se.bakover.client.skatteetaten

sealed interface SkatteoppslagFeil {
    data class Nettverksfeil(val throwable: Throwable) : SkatteoppslagFeil
    object FantIkkeSkattegrunnlagForPersonOgÅr : SkatteoppslagFeil
    data class UkjentFeil(val throwable: Throwable) : SkatteoppslagFeil
    object ManglerRettigheter : SkatteoppslagFeil
}
