package no.nav.su.se.bakover.client.oppdrag.avstemming

import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import java.math.BigDecimal

class GrunnlagBuilder(
    private val utbetalinger: List<Utbetaling.OversendtUtbetaling>
) {
    fun build(): AvstemmingDataRequest.Grunnlagdata {
        val gruppertMedKvittering = utbetalinger.filterIsInstance(Utbetaling.OversendtUtbetaling.MedKvittering::class.java)
            .groupBy { it.kvittering.utbetalingsstatus }
        val kvittertOk = gruppertMedKvittering.sumForStatus(Kvittering.Utbetalingsstatus.OK)
        val kvittertMedVarsel = gruppertMedKvittering.sumForStatus(Kvittering.Utbetalingsstatus.OK_MED_VARSEL)
        val kvittertFeil = gruppertMedKvittering.sumForStatus(Kvittering.Utbetalingsstatus.FEIL)
        val oversendtOppdragUtenKvittering = utbetalinger.filterIsInstance(Utbetaling.OversendtUtbetaling.UtenKvittering::class.java)
        val kvitteringMangler = oversendtOppdragUtenKvittering.sum()

        return AvstemmingDataRequest.Grunnlagdata(
            godkjentAntall = kvittertOk.antall,
            godkjentBelop = kvittertOk.beløp,
            godkjentFortegn = kvittertOk.fortegn,
            varselAntall = kvittertMedVarsel.antall,
            varselBelop = kvittertMedVarsel.beløp,
            varselFortegn = kvittertMedVarsel.fortegn,
            avvistAntall = kvittertFeil.antall,
            avvistBelop = kvittertFeil.beløp,
            avvistFortegn = kvittertFeil.fortegn,
            manglerAntall = kvitteringMangler.antall,
            manglerBelop = kvitteringMangler.beløp,
            manglerFortegn = kvitteringMangler.fortegn
        )
    }

    private data class Sum(
        val antall: Int,
        val beløp: BigDecimal,
        val fortegn: AvstemmingDataRequest.Fortegn
    )

    private fun Map<Kvittering.Utbetalingsstatus, List<Utbetaling>>.sumForStatus(utbetalingsstatus: Kvittering.Utbetalingsstatus): Sum =
        this.getOrDefault(utbetalingsstatus, emptyList()).sum()

    private fun List<Utbetaling>.sum() =
        this.flatMap { it.utbetalingslinjer }.sumByDouble { it.beløp }.toBigDecimal().let {
            Sum(antall = this.size, beløp = it, fortegn = it.fortegn())
        }

    private fun BigDecimal.fortegn() =
        if (this < BigDecimal.ZERO) AvstemmingDataRequest.Fortegn.FRADRAG else AvstemmingDataRequest.Fortegn.TILLEGG
}
