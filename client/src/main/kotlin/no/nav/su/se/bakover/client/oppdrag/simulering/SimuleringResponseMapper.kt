package no.nav.su.se.bakover.client.oppdrag.simulering

import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseKode
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertDetaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertUtbetaling
import no.nav.system.os.entiteter.beregningskjema.BeregningStoppnivaa
import no.nav.system.os.entiteter.beregningskjema.BeregningStoppnivaaDetaljer
import no.nav.system.os.entiteter.beregningskjema.BeregningsPeriode
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningResponse
import java.time.LocalDate

internal class SimuleringResponseMapper private constructor(
    val simulering: Simulering,
) {
    constructor(oppdragResponse: SimulerBeregningResponse) : this(oppdragResponse.toSimulering())
    constructor(utbetaling: Utbetaling) : this(utbetaling.mapTomResponsFraOppdrag())
}

private fun SimulerBeregningResponse.toSimulering() =
    Simulering(
        gjelderId = Fnr(simulering.gjelderId),
        gjelderNavn = simulering.gjelderNavn.trim(),
        datoBeregnet = LocalDate.parse(simulering.datoBeregnet),
        nettoBeløp = simulering.belop.toInt(),
        periodeList = simulering.beregningsPeriode.map { it.toSimulertPeriode() },
    )

private fun BeregningsPeriode.toSimulertPeriode() =
    SimulertPeriode(
        fraOgMed = LocalDate.parse(periodeFom),
        tilOgMed = LocalDate.parse(periodeTom),
        utbetaling = beregningStoppnivaa.map { it.toSimulertUtbetaling() }
            .filter { utbetaling -> utbetaling.detaljer.any { it.klassekode == KlasseKode.SUUFORE } },
    )

private fun BeregningStoppnivaa.toSimulertUtbetaling() =
    SimulertUtbetaling(
        fagSystemId = fagsystemId.trim(),
        utbetalesTilNavn = utbetalesTilNavn.trim(),
        utbetalesTilId = Fnr(utbetalesTilId),
        forfall = LocalDate.parse(forfall),
        feilkonto = isFeilkonto,
        detaljer = beregningStoppnivaaDetaljer.map { it.toSimulertDetalj() }
            .filter { detalj -> detalj.klasseType == KlasseType.YTEL },
    )

private fun BeregningStoppnivaaDetaljer.toSimulertDetalj() =
    SimulertDetaljer(
        faktiskFraOgMed = LocalDate.parse(faktiskFom),
        faktiskTilOgMed = LocalDate.parse(faktiskTom),
        konto = kontoStreng.trim(),
        belop = belop.toInt(),
        tilbakeforing = isTilbakeforing,
        sats = sats.toInt(),
        typeSats = typeSats.trim(),
        antallSats = antallSats.intValueExact(),
        uforegrad = uforeGrad.intValueExact(),
        klassekode = KlasseKode.valueOf(klassekode.trim()),
        klassekodeBeskrivelse = klasseKodeBeskrivelse.trim(),
        klasseType = KlasseType.valueOf(typeKlasse.trim()),
    )

/**
 * Return something with meaning for our domain for cases where simulering returns an empty response.
 * In functional terms, an empty response means that OS/UR won't perform any payments for the period in question.
 */
private fun Utbetaling.mapTomResponsFraOppdrag(): Simulering {
    return Simulering(
        gjelderId = fnr,
        gjelderNavn = fnr.toString(), // Usually returned by response, which in this case is empty.
        datoBeregnet = LocalDate.now(),
        nettoBeløp = 0,
        periodeList = listOf(
            SimulertPeriode(
                fraOgMed = tidligsteDato(),
                tilOgMed = senesteDato(),
                utbetaling = emptyList(),
            ),
        ),
    )
}
