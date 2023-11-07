package tilbakekreving.domain.forhåndsvarsel

sealed interface KunneIkkeHenteUtsendtForhåndsvarsel {
    data object FantIkkeDokument : KunneIkkeHenteUtsendtForhåndsvarsel
}
