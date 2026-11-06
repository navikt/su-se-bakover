package tilbakekreving.domain.forhåndsvarsel

import tilgangstyring.domain.IkkeTilgangTilSak

sealed interface KunneIkkeHenteUtsendtForhåndsvarsel {
    data class IkkeTilgang(val feil: IkkeTilgangTilSak) : KunneIkkeHenteUtsendtForhåndsvarsel
    data object FantIkkeDokument : KunneIkkeHenteUtsendtForhåndsvarsel
}
