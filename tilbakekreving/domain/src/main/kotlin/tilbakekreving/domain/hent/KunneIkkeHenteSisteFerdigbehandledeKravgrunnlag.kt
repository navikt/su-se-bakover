package tilbakekreving.domain.hent

import tilbakekreving.domain.IkkeTilgangTilSak

sealed interface KunneIkkeHenteSisteFerdigbehandledeKravgrunnlag {
    data object FeilVedMappingAvKravgrunnalget : KunneIkkeHenteSisteFerdigbehandledeKravgrunnlag
    data object FinnesIngenFerdigBehandledeKravgrunnlag : KunneIkkeHenteSisteFerdigbehandledeKravgrunnlag
    data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeHenteSisteFerdigbehandledeKravgrunnlag
}
