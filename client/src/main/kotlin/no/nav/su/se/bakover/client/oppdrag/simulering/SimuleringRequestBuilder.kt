package no.nav.su.se.bakover.client.oppdrag.simulering

import no.nav.su.se.bakover.client.oppdrag.Utbetalingslinjer
import no.nav.system.os.entiteter.oppdragskjema.Attestant
import no.nav.system.os.entiteter.oppdragskjema.Enhet
import no.nav.system.os.entiteter.oppdragskjema.Grad
import no.nav.system.os.entiteter.typer.simpletypes.FradragTillegg
import no.nav.system.os.entiteter.typer.simpletypes.KodeStatusLinje
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdrag
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdragslinje
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningRequest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest as SimulerBeregningGrensesnittRequest

internal class SimuleringRequestBuilder(private val utbetalingslinjer: Utbetalingslinjer) {
    private companion object {
        private val tidsstempel = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }

    private val oppdrag = Oppdrag().apply {
        kodeFagomraade = utbetalingslinjer.fagområde
        kodeEndring = utbetalingslinjer.endringskode
        utbetFrekvens = "MND"
        fagsystemId = utbetalingslinjer.fagsystemId
        oppdragGjelderId = utbetalingslinjer.fødselsnummer
        saksbehId = utbetalingslinjer.saksbehandler
        datoOppdragGjelderFom = LocalDate.EPOCH.format(tidsstempel)
        enhet.add(Enhet().apply {
            enhet = "8020"
            typeEnhet = "BOS"
            datoEnhetFom = LocalDate.EPOCH.format(tidsstempel)
        })
    }

    fun build(): SimulerBeregningGrensesnittRequest {
        utbetalingslinjer.forEach { oppdrag.oppdragslinje.add(nyLinje(it)) }
        return SimulerBeregningGrensesnittRequest().apply {
            request = SimulerBeregningRequest().apply {
                oppdrag = this@SimuleringRequestBuilder.oppdrag
                simuleringsPeriode = SimulerBeregningRequest.SimuleringsPeriode().apply {
                    datoSimulerFom = utbetalingslinjer.førsteDag().format(tidsstempel)
                    datoSimulerTom = utbetalingslinjer.sisteDag().format(tidsstempel)
                }
            }
        }
    }

    private fun nyLinje(utbetalingslinje: Utbetalingslinjer.Utbetalingslinje) = Oppdragslinje().apply {
        utbetalesTilId = utbetalingslinjer.mottaker
        delytelseId = "${utbetalingslinje.delytelseId}"
        refDelytelseId = utbetalingslinje.refDelytelseId?.let { "$it" }
        refFagsystemId = utbetalingslinje.refFagsystemId?.let { it }
        kodeEndringLinje = utbetalingslinje.endringskode
        kodeKlassifik = utbetalingslinje.klassekode
        kodeStatusLinje = utbetalingslinje.statuskode?.let { KodeStatusLinje.valueOf(it) }
        datoStatusFom = utbetalingslinje.datoStatusFom?.format(tidsstempel)
        datoVedtakFom = utbetalingslinje.fom.format(tidsstempel)
        datoVedtakTom = utbetalingslinje.tom.format(tidsstempel)
        sats = utbetalingslinje.dagsats.toBigDecimal()
        fradragTillegg = FradragTillegg.T
        typeSats = "DAG"
        saksbehId = utbetalingslinjer.saksbehandler
        brukKjoreplan = "N"
        grad.add(Grad().apply {
            typeGrad = "UFOR"
            grad = utbetalingslinje.grad.toBigInteger()
        })
        attestant.add(Attestant().apply {
            attestantId = utbetalingslinjer.saksbehandler
        })
    }
}
