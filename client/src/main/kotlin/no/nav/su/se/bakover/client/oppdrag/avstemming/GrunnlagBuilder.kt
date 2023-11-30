package no.nav.su.se.bakover.client.oppdrag.avstemming

import økonomi.domain.kvittering.Kvittering
import økonomi.domain.utbetaling.Utbetaling
import java.math.BigDecimal

internal class GrunnlagBuilder(
    private val utbetalinger: List<Utbetaling.OversendtUtbetaling>,
) {
    fun build(): GrensesnittsavstemmingData.Grunnlagdata {
        val gruppertMedKvittering = utbetalinger.filterIsInstance(Utbetaling.OversendtUtbetaling.MedKvittering::class.java)
            .groupBy { it.kvittering.utbetalingsstatus }
        val kvittertOk = gruppertMedKvittering.sumForStatus(Kvittering.Utbetalingsstatus.OK)
        val kvittertMedVarsel = gruppertMedKvittering.sumForStatus(Kvittering.Utbetalingsstatus.OK_MED_VARSEL)
        val kvittertFeil = gruppertMedKvittering.sumForStatus(Kvittering.Utbetalingsstatus.FEIL)
        val oversendtOppdragUtenKvittering = utbetalinger.filterIsInstance(Utbetaling.OversendtUtbetaling.UtenKvittering::class.java)
        val kvitteringMangler = oversendtOppdragUtenKvittering.sum()

        return GrensesnittsavstemmingData.Grunnlagdata(
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
            manglerFortegn = kvitteringMangler.fortegn,
        )
    }

    private data class Sum(
        val antall: Int,
        val beløp: BigDecimal,
        val fortegn: Fortegn,
    )

    private fun Map<Kvittering.Utbetalingsstatus, List<Utbetaling>>.sumForStatus(utbetalingsstatus: Kvittering.Utbetalingsstatus): Sum =
        this.getOrDefault(utbetalingsstatus, emptyList()).sum()

    private fun List<Utbetaling>.sum() =
        this.flatMap { it.utbetalingslinjer }.sumOf { it.beløp }.toBigDecimal().let {
            Sum(antall = this.size, beløp = it, fortegn = it.fortegn())
        }

    private fun BigDecimal.fortegn() =
        if (this < BigDecimal.ZERO) Fortegn.FRADRAG else Fortegn.TILLEGG
}
