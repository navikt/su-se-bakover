package no.nav.su.se.bakover.client.oppdrag.avstemming

import no.nav.su.se.bakover.client.oppdrag.avstemming.AvstemmingDataRequest.Detaljdata
import no.nav.su.se.bakover.client.oppdrag.toOppdragTimestamp
import no.nav.su.se.bakover.domain.oppdrag.Kvittering.Utbetalingsstatus
import no.nav.su.se.bakover.domain.oppdrag.Kvittering.Utbetalingsstatus.FEIL
import no.nav.su.se.bakover.domain.oppdrag.Kvittering.Utbetalingsstatus.OK_MED_VARSEL
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling

class DetaljBuilder(
    private val utbetalinger: List<Utbetaling>
) {
    fun build(): List<Detaljdata> =
        utbetalinger.filter { it.oversendtUtenKvittering() || it.kvittertMedFeilEllerVarsel() }
            .map {
                Detaljdata(
                    detaljType = mapStatus(it),
                    offnr = it.fnr.toString(),
                    avleverendeTransaksjonNokkel = it.id.toString(),
                    tidspunkt = it.opprettet.toOppdragTimestamp()
                )
            }

    private fun mapStatus(utbetaling: Utbetaling): Detaljdata.Detaljtype = when (utbetaling) {
        is Utbetaling.KvittertUtbetaling -> mapUtbetalingsstatus(utbetaling.kvittering.utbetalingsstatus)
        is Utbetaling.OversendtUtbetaling -> Detaljdata.Detaljtype.MANGLENDE_KVITTERING
        else -> throw IllegalArgumentException("Funksjonell feil - skal ikke lage detajl for utbetalinger med type:${utbetaling::class.simpleName}")
    }

    private fun mapUtbetalingsstatus(utbetalingsstatus: Utbetalingsstatus) = when (utbetalingsstatus) {
        OK_MED_VARSEL -> Detaljdata.Detaljtype.GODKJENT_MED_VARSEL
        FEIL -> Detaljdata.Detaljtype.AVVIST
        else -> throw IllegalArgumentException("Funksjonell feil - skal ikke lage detajl for utbetalinger med status:$utbetalingsstatus")
    }

    private fun Utbetaling.oversendtUtenKvittering() =
        this is Utbetaling.OversendtUtbetaling && oppdragsmelding.erSendt()

    private fun Utbetaling.kvittertMedFeilEllerVarsel() =
        this is Utbetaling.KvittertUtbetaling && listOf(OK_MED_VARSEL, FEIL).contains(kvittering.utbetalingsstatus)
}
