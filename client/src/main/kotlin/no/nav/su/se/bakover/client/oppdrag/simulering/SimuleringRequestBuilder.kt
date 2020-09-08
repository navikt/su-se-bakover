package no.nav.su.se.bakover.client.oppdrag.simulering

import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingMqPublisher.Companion.OppdragDefaults
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingMqPublisher.Companion.OppdragslinjeDefaults
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingMqPublisher.Companion.toOppdragDate
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.system.os.entiteter.oppdragskjema.Attestant
import no.nav.system.os.entiteter.oppdragskjema.Enhet
import no.nav.system.os.entiteter.typer.simpletypes.FradragTillegg
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdrag
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdragslinje
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningRequest
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest as SimulerBeregningGrensesnittRequest

internal class SimuleringRequestBuilder(
    private val utbetaling: Utbetaling,
    private val oppdragGjelder: String
) {
    private val oppdragRequest = Oppdrag().apply {
        kodeFagomraade = OppdragDefaults.KODE_FAGOMRÅDE
        kodeEndring = OppdragDefaults.oppdragKodeendring.value // TODO: Se Utbetalings TODO
        utbetFrekvens = OppdragDefaults.utbetalingsfrekvens.value
        fagsystemId = utbetaling.oppdragId.toString()
        oppdragGjelderId = oppdragGjelder
        saksbehId = OppdragDefaults.SAKSBEHANDLER_ID // TODO: Denne må utledes fra JWT eller hentes fra DB/system eller noe slikt.
        datoOppdragGjelderFom = OppdragDefaults.datoOppdragGjelderFom
        OppdragDefaults.oppdragsenheter.forEach {
            enhet.add(
                Enhet().apply {
                    enhet = it.enhet
                    typeEnhet = it.typeEnhet
                    datoEnhetFom = it.datoEnhetFom
                }
            )
        }
    }

    fun build(): SimulerBeregningGrensesnittRequest {
        utbetaling.utbetalingslinjer.forEach { oppdragRequest.oppdragslinje.add(nyLinje(it, oppdragGjelder, utbetaling.oppdragId.toString())) }
        val førsteDag = utbetaling.førsteDag()
        val sisteDag = utbetaling.sisteDag()
        return SimulerBeregningGrensesnittRequest().apply {
            request = SimulerBeregningRequest().apply {
                oppdrag = this@SimuleringRequestBuilder.oppdragRequest
                simuleringsPeriode = SimulerBeregningRequest.SimuleringsPeriode().apply {
                    datoSimulerFom = førsteDag.toOppdragDate()
                    datoSimulerTom = sisteDag.toOppdragDate()
                }
            }
        }
    }

    private fun nyLinje(
        utbetalingslinje: Utbetalingslinje,
        oppdragGjelder: String,
        fagsystemId: String
    ) = Oppdragslinje().apply {
        utbetalesTilId = oppdragGjelder
        delytelseId = utbetalingslinje.id.toString()
        refDelytelseId = utbetalingslinje.forrigeUtbetalingslinjeId?.toString()
        refFagsystemId = utbetalingslinje.forrigeUtbetalingslinjeId?.let {
            fagsystemId
        }
        kodeEndringLinje = "NY"
        kodeKlassifik = OppdragslinjeDefaults.KODE_KLASSIFIK
        datoVedtakFom = utbetalingslinje.fom.toOppdragDate()
        datoVedtakTom = utbetalingslinje.tom.toOppdragDate()
        sats = utbetalingslinje.beløp.toBigDecimal()
        fradragTillegg = FradragTillegg.T
        typeSats = OppdragslinjeDefaults.typeSats.value
        saksbehId = OppdragslinjeDefaults.SAKSBEHANDLER_ID
        brukKjoreplan = OppdragslinjeDefaults.BRUK_KJOREPLAN
        attestant.add(
            Attestant().apply {
                attestantId = OppdragslinjeDefaults.SAKSBEHANDLER_ID
            }
        )
    }
}
