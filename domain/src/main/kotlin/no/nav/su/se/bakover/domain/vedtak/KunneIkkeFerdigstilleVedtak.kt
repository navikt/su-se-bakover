package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.dokument.KunneIkkeLageDokument

sealed interface KunneIkkeFerdigstilleVedtak {
    data class FantIkkeVedtakForUtbetalingId(val utbetalingId: UUID30) : KunneIkkeFerdigstilleVedtak
    data class KunneIkkeGenerereBrev(val underliggende: KunneIkkeLageDokument) : KunneIkkeFerdigstilleVedtak
    object KunneIkkeLukkeOppgave : KunneIkkeFerdigstilleVedtak
}
