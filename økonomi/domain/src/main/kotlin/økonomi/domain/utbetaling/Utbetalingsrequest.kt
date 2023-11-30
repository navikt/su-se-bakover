package økonomi.domain.utbetaling

data class Utbetalingsrequest(
    val value: String,
) {
    override fun toString() = value
}
