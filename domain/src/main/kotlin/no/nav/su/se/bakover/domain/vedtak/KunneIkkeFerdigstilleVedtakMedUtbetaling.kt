package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.dokument.KunneIkkeLageDokument

sealed interface KunneIkkeFerdigstilleVedtakMedUtbetaling {
    data class FantIkkeVedtakForUtbetalingId(val utbetalingId: UUID30) : KunneIkkeFerdigstilleVedtakMedUtbetaling
}

sealed interface KunneIkkeFerdigstilleVedtak : KunneIkkeFerdigstilleVedtakMedUtbetaling {
    data class KunneIkkeGenerereBrev(val underliggende: KunneIkkeLageDokument) : KunneIkkeFerdigstilleVedtak, KunneIkkeFerdigstilleVedtakMedUtbetaling
    object KunneIkkeLukkeOppgave : KunneIkkeFerdigstilleVedtak, KunneIkkeFerdigstilleVedtakMedUtbetaling
}
