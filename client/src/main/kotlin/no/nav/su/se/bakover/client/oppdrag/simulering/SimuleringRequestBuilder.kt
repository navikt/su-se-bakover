package no.nav.su.se.bakover.client.oppdrag.simulering

import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest
import no.nav.su.se.bakover.client.oppdrag.utbetaling.toUtbetalingRequest
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerUtbetalingRequest
import no.nav.system.os.entiteter.oppdragskjema.Attestant
import no.nav.system.os.entiteter.oppdragskjema.Enhet
import no.nav.system.os.entiteter.oppdragskjema.Grad
import no.nav.system.os.entiteter.typer.simpletypes.FradragTillegg
import no.nav.system.os.entiteter.typer.simpletypes.KodeStatusLinje
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdrag
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.Oppdragslinje
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningRequest
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest as SimulerBeregningGrensesnittRequest

internal class SimuleringRequestBuilder(
    private val simuleringsperiode: Periode,
    private val mappedRequest: UtbetalingRequest.OppdragRequest,
) {
    constructor(
        request: SimulerUtbetalingRequest,
    ) : this(
        simuleringsperiode = request.simuleringsperiode,
        mappedRequest = toUtbetalingRequest(request.utbetaling).oppdragRequest,
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
                },
            )
        }
    }

    fun build(): SimulerBeregningGrensesnittRequest {
        mappedRequest.oppdragslinjer.forEach { oppdragRequest.oppdragslinje.add(nyLinje(it)) }
        return SimulerBeregningGrensesnittRequest().apply {
            request = SimulerBeregningRequest().apply {
                oppdrag = this@SimuleringRequestBuilder.oppdragRequest
                /**
                 * Effekt av å sette
                 * [SimulerBeregningRequest.SimuleringsPeriode.datoSimulerFom]
                 * [SimulerBeregningRequest.SimuleringsPeriode.datoSimulerTom]
                 *
                 * Ingen satt:
                 *  For nye (ikke utbetalt) perioder:
                 *      Kun simulering for første berørte måned returneres.
                 *  For eksisterende (utbetalt) perioder:
                 *      Simulering for alle berørte måneder returneres (opp til og med siste ikke utbetalte måned)
                 *
                 * Begge satt:
                 *  For nye (ikke utetalt) perioder:
                 *      Simulering for alle måneder i perioden returneres.
                 *  For eksisterende (utbetalt) perioder:
                 *      Simulering for alle måneder i perioden returneres. OBS: Berørte måneder kan ligge utenfor valgt periode!
                 *
                 * Kun fra og med satt:
                 *  Samme som "Ingen satt", med nedre begrensning. Til og med bestemmes av utbetalingslinje.
                 *  OBS: Berørte måneder kan være tidligere enn valgt dato!
                 *
                 * Kun til og med satt:
                 *  Samme som "Begge satt", men øvre begrensning. Fra og med bestemmes av utbetalingslinje.
                 *  OBS: Berørte måneder kan være senere enn valgt dato!
                 *
                 */

                simuleringsPeriode = SimulerBeregningRequest.SimuleringsPeriode().apply {
                    datoSimulerFom = this@SimuleringRequestBuilder.simuleringsperiode.fraOgMed.toString()
                    datoSimulerTom = this@SimuleringRequestBuilder.simuleringsperiode.tilOgMed.toString()
                }
            }
        }
    }

    private fun nyLinje(
        oppdragslinje: UtbetalingRequest.Oppdragslinje,
    ) = Oppdragslinje().apply {
        kodeStatusLinje = oppdragslinje.kodeStatusLinje?.let { KodeStatusLinje.fromValue(it.value) }
        datoStatusFom = oppdragslinje.datoStatusFom
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
        brukKjoreplan = oppdragslinje.brukKjoreplan.value
        attestant.add(
            Attestant().apply {
                attestantId = oppdragslinje.saksbehId
            },
        )
        oppdragslinje.grad?.let {
            grad.add(
                Grad().apply {
                    grad = it.grad.toBigInteger()
                    typeGrad = it.typeGrad.value
                },
            )
        }
        henvisning = oppdragslinje.utbetalingId
    }
}
