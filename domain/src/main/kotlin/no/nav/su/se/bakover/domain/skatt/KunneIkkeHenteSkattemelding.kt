package no.nav.su.se.bakover.domain.skatt

/**
 * Vi gjør ikke wrapping trikset med [SkatteoppslagFeil] fordi vi ikke vil at exception skal blø ut
 */
sealed interface KunneIkkeHenteSkattemelding {
    data object Nettverksfeil : KunneIkkeHenteSkattemelding

    data object FinnesIkke : KunneIkkeHenteSkattemelding

    data object UkjentFeil : KunneIkkeHenteSkattemelding
    data object ManglerRettigheter : KunneIkkeHenteSkattemelding
    data object PersonFeil : KunneIkkeHenteSkattemelding
}
