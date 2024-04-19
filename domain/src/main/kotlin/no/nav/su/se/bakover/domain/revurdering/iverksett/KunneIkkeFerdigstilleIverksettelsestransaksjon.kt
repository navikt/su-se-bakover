package no.nav.su.se.bakover.domain.revurdering.iverksett

import økonomi.domain.utbetaling.UtbetalingFeilet

sealed interface KunneIkkeFerdigstilleIverksettelsestransaksjon {
    data class KunneIkkeLeggeUtbetalingPåKø(
        val utbetalingFeilet: UtbetalingFeilet,
    ) : KunneIkkeFerdigstilleIverksettelsestransaksjon {
        override fun toString() = "KunneIkkeLeggeUtbetalingPåKø"
    }

    data class KunneIkkeKlargjøreUtbetaling(
        val underliggende: økonomi.domain.utbetaling.KunneIkkeKlaregjøreUtbetaling,
    ) : KunneIkkeFerdigstilleIverksettelsestransaksjon

    data class UkjentFeil(val throwable: Throwable) : KunneIkkeFerdigstilleIverksettelsestransaksjon {
        override fun toString() = "UkjentFeil(throwable=${throwable::class.simpleName})"
    }
}
