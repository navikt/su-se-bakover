package no.nav.su.se.bakover.client.oppdrag.simulering

import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.system.os.entiteter.oppdragskjema.Attestant
import no.nav.system.os.entiteter.oppdragskjema.Enhet
import no.nav.system.os.entiteter.typer.simpletypes.FradragTillegg
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdrag
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdragslinje
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningRequest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest as SimulerBeregningGrensesnittRequest

internal class SimuleringRequestBuilder(
    private val utbetaling: Utbetaling,
    private val oppdragGjelder: String
) {
    private companion object {
        private val tidsstempel = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }

    private val oppdragRequest = Oppdrag().apply {
        kodeFagomraade = "SU" // TODO: Finn korrekt
        kodeEndring = "NY" // TODO: Så lenge vi ikke gjør en faktisk utbetaling, vil denne være NY uavhengig hvor mange simuleringer vi gjør.
        utbetFrekvens = "MND"
        fagsystemId = utbetaling.oppdragId.toString()
        oppdragGjelderId = oppdragGjelder
        saksbehId = "abc1234"
        datoOppdragGjelderFom = LocalDate.EPOCH.format(tidsstempel)
        enhet.add(
            Enhet().apply {
                enhet = "8020"
                typeEnhet = "BOS"
                datoEnhetFom = LocalDate.EPOCH.format(tidsstempel)
            }
        )
    }

    fun build(): SimulerBeregningGrensesnittRequest {
        utbetaling.utbetalingslinjer.forEach { oppdragRequest.oppdragslinje.add(nyLinje(it, oppdragGjelder, "abc1234")) }
        val førsteDag = utbetaling.førsteDag()
        val sisteDag = utbetaling.sisteDag()
        return SimulerBeregningGrensesnittRequest().apply {
            request = SimulerBeregningRequest().apply {
                oppdrag = this@SimuleringRequestBuilder.oppdragRequest
                simuleringsPeriode = SimulerBeregningRequest.SimuleringsPeriode().apply {
                    datoSimulerFom = førsteDag.format(tidsstempel)
                    datoSimulerTom = sisteDag.format(tidsstempel)
                }
            }
        }
    }

    private fun nyLinje(
        utbetalingslinje: no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje,
        oppdragGjelder: String,
        saksbehandler: String
    ) = Oppdragslinje().apply {
        utbetalesTilId = oppdragGjelder
        delytelseId = utbetalingslinje.id.toString()
        refDelytelseId = utbetalingslinje.forrigeUtbetalingslinjeId?.toString()
        kodeEndringLinje = "NY"
        kodeKlassifik = "KLASSE"
        datoVedtakFom = utbetalingslinje.fom.format(tidsstempel)
        datoVedtakTom = utbetalingslinje.tom.format(tidsstempel)
        sats = utbetalingslinje.beløp.toBigDecimal()
        fradragTillegg = FradragTillegg.T
        typeSats = "MND"
        saksbehId = saksbehandler
        brukKjoreplan = "N"
        attestant.add(
            Attestant().apply {
                attestantId = saksbehandler // det finnes ikke en attestant på dette tidspunktet
            }
        )
    }
}
