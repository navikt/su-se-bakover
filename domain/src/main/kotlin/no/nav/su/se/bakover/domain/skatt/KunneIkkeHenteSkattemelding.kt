package no.nav.su.se.bakover.domain.skatt

/**
 * Vi gjør ikke wrapping trikset med [SkatteoppslagFeil] fordi vi ikke vil at exception skal blø ut
 */
sealed interface KunneIkkeHenteSkattemelding {
    object Nettverksfeil : KunneIkkeHenteSkattemelding

    object FinnesIkke : KunneIkkeHenteSkattemelding

    object UkjentFeil : KunneIkkeHenteSkattemelding
    object ManglerRettigheter : KunneIkkeHenteSkattemelding
    object PersonFeil : KunneIkkeHenteSkattemelding
}
