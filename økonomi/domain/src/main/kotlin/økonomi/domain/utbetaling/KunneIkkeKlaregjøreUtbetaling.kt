package økonomi.domain.utbetaling

sealed interface KunneIkkeKlaregjøreUtbetaling {

    data class KunneIkkeLagre(val feil: Throwable) : KunneIkkeKlaregjøreUtbetaling {
        override fun toString() = "KunneIkkeKlaregjøreUtbetaling.KunneIkkeLagre(feil=${feil::class.simpleName})"
    }
    data class KunneIkkeLageUtbetalingslinjer(val feil: Throwable) : KunneIkkeKlaregjøreUtbetaling {
        override fun toString() = "KunneIkkeKlaregjøreUtbetaling.KunneIkkeLageUtbetalingslinjer(feil=${feil::class.simpleName})"
    }
}
