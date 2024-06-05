package tilbakekreving.domain.forhåndsvarsel

import dokument.domain.KunneIkkeLageDokument
import tilgangstyring.domain.IkkeTilgangTilSak

sealed interface KunneIkkeForhåndsviseForhåndsvarsel {
    data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeForhåndsviseForhåndsvarsel
    data class FeilVedDokumentGenerering(
        val kunneIkkeLageDokument: KunneIkkeLageDokument,
    ) : KunneIkkeForhåndsviseForhåndsvarsel

    data object FantIkkeBehandling : KunneIkkeForhåndsviseForhåndsvarsel
    data object UlikVersjon : KunneIkkeForhåndsviseForhåndsvarsel
}
