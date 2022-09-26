package no.nav.su.se.bakover.client.oppdrag.avstemming

import no.nav.su.se.bakover.client.oppdrag.avstemming.GrensesnittsavstemmingData.Detaljdata
import no.nav.su.se.bakover.client.oppdrag.toOppdragTimestamp
import no.nav.su.se.bakover.domain.oppdrag.Kvittering.Utbetalingsstatus
import no.nav.su.se.bakover.domain.oppdrag.Kvittering.Utbetalingsstatus.FEIL
import no.nav.su.se.bakover.domain.oppdrag.Kvittering.Utbetalingsstatus.OK_MED_VARSEL
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling

internal class DetaljBuilder(
    internal val utbetalinger: List<Utbetaling.UtbetalingKlargjortForOversendelse>,
) {
    fun build(): List<Detaljdata> =
        utbetalinger.filter { it is Utbetaling.UtbetalingKlargjortForOversendelse.UtenKvittering || it.kvittertMedFeilEllerVarsel() }
            .map {
                Detaljdata(
                    detaljType = mapStatus(it),
                    offnr = it.fnr.toString(),
                    avleverendeTransaksjonNokkel = it.id.toString(),
                    tidspunkt = it.opprettet.toOppdragTimestamp(),
                )
            }

    private fun mapStatus(utbetaling: Utbetaling.UtbetalingKlargjortForOversendelse): Detaljdata.Detaljtype = when (utbetaling) {
        is Utbetaling.UtbetalingKlargjortForOversendelse.MedKvittering -> mapUtbetalingsstatus(utbetaling.kvittering.utbetalingsstatus)
        is Utbetaling.UtbetalingKlargjortForOversendelse.UtenKvittering -> Detaljdata.Detaljtype.MANGLENDE_KVITTERING
    }

    private fun mapUtbetalingsstatus(utbetalingsstatus: Utbetalingsstatus) = when (utbetalingsstatus) {
        OK_MED_VARSEL -> Detaljdata.Detaljtype.GODKJENT_MED_VARSEL
        FEIL -> Detaljdata.Detaljtype.AVVIST
        else -> throw IllegalArgumentException("Funksjonell feil - skal ikke lage detajl for utbetalinger med status:$utbetalingsstatus")
    }

    private fun Utbetaling.kvittertMedFeilEllerVarsel() =
        this is Utbetaling.UtbetalingKlargjortForOversendelse.MedKvittering && kvittertMedFeilEllerVarsel()
}
