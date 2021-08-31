package no.nav.su.se.bakover.client.oppdrag.avstemming

import no.nav.su.se.bakover.client.oppdrag.toOppdragTimestamp
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming

class KonsistensavstemmingDataBuilder(
    private val avstemming: Avstemming.Konsistensavstemming,
) {
    private val utbetalinger = avstemming.utbetalinger

    fun build(): KonsistensavstemmingDataRequest {
        return KonsistensavstemmingDataRequest(
            aksjon = Aksjonsdata.Konsistensavstemming(
                avleverendeAvstemmingId = avstemming.id.toString(),
                tidspunktAvstemmingTom = avstemming.tilOgMed.toOppdragTimestamp(),
            ),
            total = Totaldata(
                totalAntall = utbetalinger.size,
                totalBelop = utbetalinger.flatMap { it.utbetalingslinjer }.sumOf { it.beløp }.toBigDecimal(),
                fortegn = Fortegn.TILLEGG,
            ),
        )
    }
}
