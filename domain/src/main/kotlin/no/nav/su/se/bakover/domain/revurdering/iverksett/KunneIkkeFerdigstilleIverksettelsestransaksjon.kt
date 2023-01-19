package no.nav.su.se.bakover.domain.revurdering.iverksett

import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet

sealed interface KunneIkkeFerdigstilleIverksettelsestransaksjon {
    data class KunneIkkeUtbetale(val utbetalingFeilet: UtbetalingFeilet) :
        KunneIkkeFerdigstilleIverksettelsestransaksjon
    data class UkjentFeil(val throwable: Throwable) : KunneIkkeFerdigstilleIverksettelsestransaksjon
}
