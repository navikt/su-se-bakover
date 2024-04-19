package økonomi.domain.utbetaling

sealed interface KunneIkkeKlaregjøreUtbetaling {

    data class KunneIkkeLagre(val feil: Throwable) : KunneIkkeKlaregjøreUtbetaling
    data class KunneIkkeLageUtbetalingslinjer(val feil: Throwable) : KunneIkkeKlaregjøreUtbetaling
}
