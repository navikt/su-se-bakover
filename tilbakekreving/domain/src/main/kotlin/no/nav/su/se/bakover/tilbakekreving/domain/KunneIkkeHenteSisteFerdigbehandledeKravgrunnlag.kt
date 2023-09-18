package no.nav.su.se.bakover.tilbakekreving.domain

sealed interface KunneIkkeHenteSisteFerdigbehandledeKravgrunnlag {
    data object FeilVedMappingAvKravgrunnalget : KunneIkkeHenteSisteFerdigbehandledeKravgrunnlag
    data object FinnesIngenFerdigBehandledeKravgrunnlag : KunneIkkeHenteSisteFerdigbehandledeKravgrunnlag
}
