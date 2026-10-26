package no.nav.su.se.bakover.domain.revurdering.brev

import dokument.domain.KunneIkkeLageDokument
import dokument.domain.forsteside.KunneIkkeGenerereForsteside

sealed interface KunneIkkeLageBrevutkastForRevurdering {
    data object FantIkkeRevurdering : KunneIkkeLageBrevutkastForRevurdering
    data object UgyldigTilstand : KunneIkkeLageBrevutkastForRevurdering
    data class KunneIkkeGenererePdf(val underliggende: KunneIkkeLageDokument) : KunneIkkeLageBrevutkastForRevurdering
    data class FeilVedGenereringAvForsteside(val underliggende: KunneIkkeGenerereForsteside) : KunneIkkeLageBrevutkastForRevurdering
    data class FeilVedSammenslåingAvPdf(val underliggende: Throwable) : KunneIkkeLageBrevutkastForRevurdering
}
