package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.UUID30

sealed interface KunneIkkeFerdigstilleVedtak {
    data class FantIkkeVedtakForUtbetalingId(val utbetalingId: UUID30) : KunneIkkeFerdigstilleVedtak
    object KunneIkkeGenerereBrev : KunneIkkeFerdigstilleVedtak
    object KunneIkkeLukkeOppgave : KunneIkkeFerdigstilleVedtak
}
