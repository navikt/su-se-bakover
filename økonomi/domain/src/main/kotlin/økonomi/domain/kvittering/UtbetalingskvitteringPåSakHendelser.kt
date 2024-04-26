package økonomi.domain.kvittering

data class UtbetalingskvitteringPåSakHendelser(
    val hendelser: List<UtbetalingskvitteringPåSakHendelse>,
) : List<UtbetalingskvitteringPåSakHendelse> by hendelser
