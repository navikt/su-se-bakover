package no.nav.su.se.bakover.client.oppdrag.simulering

import no.nav.su.se.bakover.client.oppdrag.toOppdragDate
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest
import no.nav.su.se.bakover.client.oppdrag.utbetaling.opphørsRequest
import no.nav.su.se.bakover.client.oppdrag.utbetaling.toUtbetalingRequest
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.domain.oppdrag.Opphør
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.system.os.entiteter.oppdragskjema.Attestant
import no.nav.system.os.entiteter.oppdragskjema.Enhet
import no.nav.system.os.entiteter.typer.simpletypes.FradragTillegg
import no.nav.system.os.entiteter.typer.simpletypes.KodeStatus
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdrag
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdragslinje
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningRequest
import java.time.LocalDate
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest as SimulerBeregningGrensesnittRequest

internal class SimuleringRequestBuilder(
    private val mappedRequest: UtbetalingRequest.OppdragRequest,
    private val simuleringsPeriode: SimuleringsPeriode
) {
    constructor(
        utbetaling: Utbetaling
    ) : this(
        toUtbetalingRequest(utbetaling).oppdragRequest,
        SimuleringsPeriode(
            fraOgMed = utbetaling.tidligsteDato().toOppdragDate(),
            tilOgMed = utbetaling.senesteDato().toOppdragDate()
        )
    )

    constructor(
        opphør: Opphør
    ) : this(
        opphørsRequest(opphør).oppdragRequest,
        SimuleringsPeriode(
            fraOgMed = opphør.fraOgMed.toOppdragDate(),
            tilOgMed = SimuleringsPeriode.farInTheFuture.toOppdragDate()
        )
    )

    private val oppdragRequest = Oppdrag().apply {
        kodeStatus = mappedRequest.kodeStatus?.let { KodeStatus.fromValue(it.value) }
        datoStatusFom = mappedRequest.datoStatusFom
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
                    datoSimulerFom = this@SimuleringRequestBuilder.simuleringsPeriode.fraOgMed
                    datoSimulerTom = this@SimuleringRequestBuilder.simuleringsPeriode.tilOgMed
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

data class SimuleringsPeriode(
    val fraOgMed: String,
    val tilOgMed: String
) {
    companion object {
        /**
         * Used to include simulering for all probable future periods.
         */
        val farInTheFuture: LocalDate = 31.desember(2999)
    }
}
