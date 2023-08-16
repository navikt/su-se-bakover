package no.nav.su.se.bakover.domain.klage.brev

import no.nav.su.se.bakover.domain.klage.KunneIkkeLageBrevKommandoForKlage

sealed interface KunneIkkeLageBrevutkast {
    data object FantIkkeKlage : KunneIkkeLageBrevutkast
    data class FeilVedBrevRequest(val feil: KunneIkkeLageBrevKommandoForKlage) : KunneIkkeLageBrevutkast

    data class KunneIkkeGenererePdf(val feil: no.nav.su.se.bakover.domain.dokument.KunneIkkeLageDokument) : KunneIkkeLageBrevutkast
}
