package no.nav.su.se.bakover.client.oppdrag.simulering

import no.nav.system.os.entiteter.oppdragskjema.Attestant
import no.nav.system.os.entiteter.oppdragskjema.Enhet
import no.nav.system.os.entiteter.typer.simpletypes.FradragTillegg
import no.nav.system.os.entiteter.typer.simpletypes.KodeStatusLinje
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdrag
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdragslinje
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningRequest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest as SimulerBeregningGrensesnittRequest

internal class SimuleringRequestBuilder(private val oppdrag: no.nav.su.se.bakover.domain.oppdrag.Oppdrag) {
    private companion object {
        private val tidsstempel = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }

    private val oppdragRequest = Oppdrag().apply {
        kodeFagomraade = "SU" // TODO: Finn korrekt
        kodeEndring = oppdrag.endringskode.name
        utbetFrekvens = oppdrag.utbetalingsfrekvens.name
        fagsystemId = oppdrag.sakId.toString() // TODO: Finn korrekt
        oppdragGjelderId = oppdrag.oppdragGjelder
        saksbehId = oppdrag.saksbehandler
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
        oppdrag.oppdragslinjer.forEach { oppdragRequest.oppdragslinje.add(nyLinje(it)) }
        val førsteDag = oppdrag.førsteDag()
        val sisteDag = oppdrag.sisteDag()
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

    private fun nyLinje(oppdragslinje: no.nav.su.se.bakover.domain.oppdrag.Oppdragslinje) = Oppdragslinje().apply {
        utbetalesTilId = oppdrag.oppdragGjelder
        delytelseId = oppdragslinje.id.toString()
        refDelytelseId = oppdragslinje.refOppdragslinjeId?.toString()
        refFagsystemId = oppdragslinje.refSakId.toString()
        kodeEndringLinje = oppdragslinje.endringskode.name
        kodeKlassifik = oppdragslinje.klassekode.name
        kodeStatusLinje = oppdragslinje.status?.let { KodeStatusLinje.valueOf(it.name) }
        datoStatusFom = oppdragslinje.statusFom?.format(tidsstempel)
        datoVedtakFom = oppdragslinje.fom.format(tidsstempel)
        datoVedtakTom = oppdragslinje.tom.format(tidsstempel)
        sats = oppdragslinje.beløp.toBigDecimal()
        fradragTillegg = FradragTillegg.T
        typeSats = oppdragslinje.beregningsfrekvens.name
        saksbehId = oppdrag.saksbehandler
        brukKjoreplan = "N"
        attestant.add(
            Attestant().apply {
                attestantId = oppdrag.saksbehandler // det finnes ikke en attestant på dette tidspunktet
            }
        )
    }
}
