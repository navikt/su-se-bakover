package no.nav.su.se.bakover.client.oppdrag.avstemming

import no.nav.su.se.bakover.client.oppdrag.toAvstemmingsdatoFormat
import no.nav.su.se.bakover.client.oppdrag.toOppdragTimestamp
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import java.math.BigDecimal

class AvstemmingDataBuilder(
    val avstemming: Avstemming
) {
    val utbetalinger = avstemming.utbetalinger
    fun build(): AvstemmingDataRequest {
        return AvstemmingDataRequest(
            aksjon = Aksjonsdata(
                aksjonType = Aksjonsdata.AksjonType.DATA,
                kildeType = Aksjonsdata.KildeType.AVLEVERT,
                avstemmingType = Aksjonsdata.AvstemmingType.GRENSESNITTAVSTEMMING,
                nokkelFom = avstemming.fom.toOppdragTimestamp(),
                nokkelTom = avstemming.tom.toOppdragTimestamp(),
                avleverendeAvstemmingId = avstemming.id.toString()
            ),
            total = AvstemmingDataRequest.Totaldata(
                totalAntall = utbetalinger.size,
                totalBelop = utbetalinger.flatMap { it.utbetalingslinjer }.sumBy { it.beløp }.toBigDecimal(),
                fortegn = AvstemmingDataRequest.Fortegn.TILLEGG
            ),
            periode = AvstemmingDataRequest.Periodedata(
                datoAvstemtFom = avstemming.fom.toAvstemmingsdatoFormat(),
                datoAvstemtTom = avstemming.tom.toAvstemmingsdatoFormat()
            ),
            grunnlag = AvstemmingDataRequest.Grunnlagdata(
                godkjentAntall = utbetalinger.mapNotNull { it.getKvittering() }.map { it.utbetalingsstatus }
                    .filter { it == Kvittering.Utbetalingsstatus.OK }.size,
                godkjentBelop = utbetalinger.filter { it.getKvittering()?.utbetalingsstatus == Kvittering.Utbetalingsstatus.OK }
                    .flatMap { it.utbetalingslinjer }.sumBy { it.beløp }.toBigDecimal(),
                godkjenttFortegn = AvstemmingDataRequest.Fortegn.TILLEGG,
                varselAntall = utbetalinger.mapNotNull { it.getKvittering() }.map { it.utbetalingsstatus }
                    .filter { it == Kvittering.Utbetalingsstatus.OK_MED_VARSEL }.size,
                varselBelop = utbetalinger.filter { it.getKvittering()?.utbetalingsstatus == Kvittering.Utbetalingsstatus.OK_MED_VARSEL }
                    .flatMap { it.utbetalingslinjer }.sumBy { it.beløp }.toBigDecimal(),
                varselFortegn = AvstemmingDataRequest.Fortegn.TILLEGG,
                avvistAntall = utbetalinger.mapNotNull { it.getKvittering() }.map { it.utbetalingsstatus }
                    .filter { it == Kvittering.Utbetalingsstatus.FEIL }.size,
                avvistBelop = utbetalinger.filter { it.getKvittering()?.utbetalingsstatus == Kvittering.Utbetalingsstatus.FEIL }
                    .flatMap { it.utbetalingslinjer }.sumBy { it.beløp }.toBigDecimal(),
                avvistFortegn = AvstemmingDataRequest.Fortegn.TILLEGG,
                manglerAntall = 0,
                manglerBelop = BigDecimal.ZERO,
                manglerFortegn = AvstemmingDataRequest.Fortegn.TILLEGG
            ),
            detalj = listOf()
        )
    }
}
