package no.nav.su.se.bakover.client.oppdrag.simulering

import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest
import no.nav.su.se.bakover.client.oppdrag.utbetaling.toUtbetalingRequest
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.system.os.entiteter.oppdragskjema.Attestant
import no.nav.system.os.entiteter.oppdragskjema.Enhet
import no.nav.system.os.entiteter.typer.simpletypes.FradragTillegg
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdrag
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdragslinje
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningRequest
import java.time.Clock
import java.time.LocalDate
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest as SimulerBeregningGrensesnittRequest

internal class SimuleringRequestBuilder(
    private val mappedRequest: UtbetalingRequest.OppdragRequest
) {
    constructor(
        oppdrag: no.nav.su.se.bakover.domain.oppdrag.Oppdrag,
        utbetaling: Utbetaling,
        simuleringGjelder: Fnr
    ) : this(
        toUtbetalingRequest(oppdrag, utbetaling, simuleringGjelder, Clock.systemUTC()).oppdragRequest
    )

    private val oppdragRequest = Oppdrag().apply {
        kodeFagomraade = mappedRequest.kodeFagomraade
        kodeEndring = mappedRequest.kodeEndring.value
        utbetFrekvens = mappedRequest.utbetFrekvens.value
        fagsystemId = mappedRequest.fagsystemId
        oppdragGjelderId = mappedRequest.oppdragGjelderId
        saksbehId = mappedRequest.saksbehId
        datoOppdragGjelderFom = mappedRequest.datoOppdragGjelderFom
        mappedRequest.oppdragsEnheter.forEach {
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
        mappedRequest.oppdragslinjer.forEach { oppdragRequest.oppdragslinje.add(nyLinje(it)) }
        return SimulerBeregningGrensesnittRequest().apply {
            request = SimulerBeregningRequest().apply {
                oppdrag = this@SimuleringRequestBuilder.oppdragRequest
                simuleringsPeriode = SimulerBeregningRequest.SimuleringsPeriode().apply {
                    datoSimulerFom =
                        mappedRequest.oppdragslinjer.map { LocalDate.parse(it.datoVedtakFom) }.minByOrNull { it }!!
                            .toString()
                    datoSimulerTom =
                        mappedRequest.oppdragslinjer.map { LocalDate.parse(it.datoVedtakTom) }.maxByOrNull { it }!!
                            .toString()
                }
            }
        }
    }

    private fun nyLinje(
        oppdragslinje: UtbetalingRequest.Oppdragslinje
    ) = Oppdragslinje().apply {
        utbetalesTilId = oppdragslinje.utbetalesTilId
        delytelseId = oppdragslinje.delytelseId
        refDelytelseId = oppdragslinje.refDelytelseId
        refFagsystemId = oppdragslinje.refFagsystemId
        kodeEndringLinje = oppdragslinje.kodeEndringLinje.value
        kodeKlassifik = oppdragslinje.kodeKlassifik
        datoVedtakFom = oppdragslinje.datoVedtakFom
        datoVedtakTom = oppdragslinje.datoVedtakTom
        sats = oppdragslinje.sats.toBigDecimal()
        fradragTillegg = FradragTillegg.fromValue(oppdragslinje.fradragTillegg.value)
        typeSats = oppdragslinje.typeSats.value
        saksbehId = oppdragslinje.saksbehId
        brukKjoreplan = oppdragslinje.brukKjoreplan
        attestant.add(
            Attestant().apply {
                attestantId = oppdragslinje.saksbehId
            }
        )
    }
}
