package no.nav.su.se.bakover.domain.revurdering.brev

import no.nav.su.se.bakover.domain.revurdering.KunneIkkeLageAvsluttetRevurdering

sealed interface KunneIkkeLageBrevutkastForAvsluttingAvRevurdering {
    data object FantIkkeRevurdering : KunneIkkeLageBrevutkastForAvsluttingAvRevurdering
    data class KunneIkkeAvslutteRevurdering(
        val underliggende: KunneIkkeLageAvsluttetRevurdering,
    ) : KunneIkkeLageBrevutkastForAvsluttingAvRevurdering

    data class KunneIkkeLageDokument(
        val underliggende: no.nav.su.se.bakover.domain.dokument.KunneIkkeLageDokument,
    ) : KunneIkkeLageBrevutkastForAvsluttingAvRevurdering
}
