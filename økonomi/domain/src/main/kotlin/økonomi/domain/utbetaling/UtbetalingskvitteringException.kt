package økonomi.domain.utbetaling

class UtbetalingskvitteringException(
    val feil: Utbetalinger.Kvitteringsfeil,
    message: String,
) : IllegalStateException(message)
