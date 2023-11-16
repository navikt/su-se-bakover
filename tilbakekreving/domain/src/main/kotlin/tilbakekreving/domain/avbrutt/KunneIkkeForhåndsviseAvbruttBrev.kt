package tilbakekreving.domain.avbrutt

import dokument.domain.KunneIkkeLageDokument
import tilbakekreving.domain.IkkeTilgangTilSak

sealed interface KunneIkkeForhåndsviseAvbruttBrev {
    data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeForhåndsviseAvbruttBrev
    data object UlikVersjon : KunneIkkeForhåndsviseAvbruttBrev
    data class FeilVedDokumentGenerering(
        val kunneIkkeLageDokument: KunneIkkeLageDokument,
    ) : KunneIkkeForhåndsviseAvbruttBrev
}
