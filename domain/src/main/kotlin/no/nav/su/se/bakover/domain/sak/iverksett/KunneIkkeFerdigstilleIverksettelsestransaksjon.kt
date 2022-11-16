package no.nav.su.se.bakover.domain.sak.iverksett

import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet

sealed interface KunneIkkeFerdigstilleIverksettelsestransaksjon {
    data class KunneIkkeUtbetale(val utbetalingFeilet: UtbetalingFeilet) : KunneIkkeFerdigstilleIverksettelsestransaksjon
    object LagringFeilet : KunneIkkeFerdigstilleIverksettelsestransaksjon

    sealed interface Opphør : KunneIkkeFerdigstilleIverksettelsestransaksjon {
        object KunneIkkeAnnullereKontrollsamtale : Opphør
    }
}
