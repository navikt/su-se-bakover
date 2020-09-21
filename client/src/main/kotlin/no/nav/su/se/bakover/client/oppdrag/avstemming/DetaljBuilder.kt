package no.nav.su.se.bakover.client.oppdrag.avstemming

import no.nav.su.se.bakover.client.oppdrag.avstemming.AvstemmingDataRequest.Detaljdata
import no.nav.su.se.bakover.client.oppdrag.toOppdragTimestamp
import no.nav.su.se.bakover.domain.oppdrag.Kvittering.Utbetalingsstatus
import no.nav.su.se.bakover.domain.oppdrag.Kvittering.Utbetalingsstatus.FEIL
import no.nav.su.se.bakover.domain.oppdrag.Kvittering.Utbetalingsstatus.OK
import no.nav.su.se.bakover.domain.oppdrag.Kvittering.Utbetalingsstatus.OK_MED_VARSEL
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling

class DetaljBuilder(
    private val utbetalinger: List<Utbetaling>
) {
    fun build(): List<Detaljdata> = utbetalinger.filter { shouldCreate(it) }
        .map {
            Detaljdata(
                detaljType = mapStatus(it),
                offnr = "123456789010", // TODO
                avleverendeTransaksjonNokkel = it.id.toString(),
                tidspunkt = it.opprettet.toOppdragTimestamp()
            )
        }

    private fun mapStatus(utbetaling: Utbetaling): Detaljdata.Detaljtype = when (utbetaling.kvittertMedFeilEllerVarsel()) {
        true -> mapUtbetalingsstatus(utbetaling.getKvittering()!!.utbetalingsstatus)
        false -> Detaljdata.Detaljtype.MANGLENDE_KVITTERING
    }

    private fun mapUtbetalingsstatus(utbetalingsstatus: Utbetalingsstatus) = when (utbetalingsstatus) {
        OK_MED_VARSEL -> Detaljdata.Detaljtype.GODKJENT_MED_VARSEL
        FEIL -> Detaljdata.Detaljtype.AVVIST
        else -> throw IllegalArgumentException("Funksjonell feil - skal ikke lage detajl for utbetalinger med status:$OK")
    }

    private fun shouldCreate(utbetaling: Utbetaling) =
        utbetaling.oversendtUtenKvittering() || utbetaling.kvittertMedFeilEllerVarsel()

    private fun Utbetaling.oversendtUtenKvittering() = erOversendtOppdrag() && !erKvittert()
    private fun Utbetaling.kvittertMedFeilEllerVarsel() =
        erKvittert() && listOf(OK_MED_VARSEL, FEIL).contains(getKvittering()!!.utbetalingsstatus)
}
