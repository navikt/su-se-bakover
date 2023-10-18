package tilbakekreving.domain.forhåndsvarsel

import dokument.domain.KunneIkkeLageDokument
import tilbakekreving.domain.IkkeTilgangTilSak

sealed interface KunneIkkeForhåndsviseForhåndsvarsel {
    data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeForhåndsviseForhåndsvarsel
    data class FeilVedDokumentGenerering(
        val kunneIkkeLageDokument: KunneIkkeLageDokument,
    ) : KunneIkkeForhåndsviseForhåndsvarsel
    data object UlikVersjon : KunneIkkeForhåndsviseForhåndsvarsel
}
