package no.nav.su.se.bakover.tilbakekreving.domain

sealed interface KunneIkkeOppretteTilbakekrevingsbehandling {
    data object FeilVedMappingAvKravgrunnalget : KunneIkkeOppretteTilbakekrevingsbehandling
    data object FinnesIngenFerdigBehandledeKravgrunnlag : KunneIkkeOppretteTilbakekrevingsbehandling
}
