package no.nav.su.se.bakover.client.oppdrag.simulering

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
    private val oppdrag: no.nav.su.se.bakover.domain.oppdrag.Oppdrag,
    private val oppdragGjelder: String
) {
    private companion object {
        private val tidsstempel = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }

    private val oppdragRequest = Oppdrag().apply {
        kodeFagomraade = "SU" // TODO: Finn korrekt
        kodeEndring = "NY"
        utbetFrekvens = "MND"
        fagsystemId = oppdrag.sakId.toString() // TODO: Finn korrekt
        oppdragGjelderId = oppdragGjelder
        saksbehId = "saksbehandler"
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
        oppdrag.oppdragslinjer.forEach { oppdragRequest.oppdragslinje.add(nyLinje(oppdrag, it, oppdragGjelder, "saksbehandler")) }
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

    private fun nyLinje(
        oppdrag: no.nav.su.se.bakover.domain.oppdrag.Oppdrag,
        oppdragslinje: no.nav.su.se.bakover.domain.oppdrag.Oppdragslinje,
        oppdragGjelder: String,
        saksbehandler: String
    ) = Oppdragslinje().apply {
        utbetalesTilId = oppdragGjelder
        delytelseId = oppdragslinje.id.toString()
        refDelytelseId = oppdragslinje.forrigeOppdragslinjeId?.toString()
        refFagsystemId = oppdrag.sakId.toString()
        kodeEndringLinje = "NY"
        kodeKlassifik = "KLASSE"
        datoVedtakFom = oppdragslinje.fom.format(tidsstempel)
        datoVedtakTom = oppdragslinje.tom.format(tidsstempel)
        sats = oppdragslinje.beløp.toBigDecimal()
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
