package no.nav.su.se.bakover.client.oppdrag.avstemming

import no.nav.su.se.bakover.client.oppdrag.avstemming.GrensesnittsavstemmingData.Detaljdata
import no.nav.su.se.bakover.client.oppdrag.toOppdragTimestamp
import økonomi.domain.kvittering.Kvittering.Utbetalingsstatus
import økonomi.domain.kvittering.Kvittering.Utbetalingsstatus.FEIL
import økonomi.domain.kvittering.Kvittering.Utbetalingsstatus.OK_MED_VARSEL
import økonomi.domain.utbetaling.Utbetaling

internal class DetaljBuilder(
    internal val utbetalinger: List<Utbetaling.OversendtUtbetaling>,
) {
    fun build(): List<Detaljdata> =
        utbetalinger.filter { it is Utbetaling.OversendtUtbetaling.UtenKvittering || it.kvittertMedFeilEllerVarsel() }
            .map {
                Detaljdata(
                    detaljType = mapStatus(it),
                    offnr = it.fnr.toString(),
                    avleverendeTransaksjonNokkel = it.id.toString(),
                    tidspunkt = it.opprettet.toOppdragTimestamp(),
                )
            }

    private fun mapStatus(utbetaling: Utbetaling.OversendtUtbetaling): Detaljdata.Detaljtype = when (utbetaling) {
        is Utbetaling.OversendtUtbetaling.MedKvittering -> mapUtbetalingsstatus(utbetaling.kvittering.utbetalingsstatus)
        is Utbetaling.OversendtUtbetaling.UtenKvittering -> Detaljdata.Detaljtype.MANGLENDE_KVITTERING
    }

    private fun mapUtbetalingsstatus(utbetalingsstatus: Utbetalingsstatus) = when (utbetalingsstatus) {
        OK_MED_VARSEL -> Detaljdata.Detaljtype.GODKJENT_MED_VARSEL
        FEIL -> Detaljdata.Detaljtype.AVVIST
        else -> throw IllegalArgumentException("Funksjonell feil - skal ikke lage detajl for utbetalinger med status:$utbetalingsstatus")
    }

    private fun Utbetaling.kvittertMedFeilEllerVarsel() =
        this is Utbetaling.OversendtUtbetaling.MedKvittering && kvittertMedFeilEllerVarsel()
}
