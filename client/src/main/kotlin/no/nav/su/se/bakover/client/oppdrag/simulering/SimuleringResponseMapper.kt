package no.nav.su.se.bakover.client.oppdrag.simulering

import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.oppdrag.Fagområde
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseKode
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertDetaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertMåned
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertUtbetaling
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.system.os.entiteter.beregningskjema.BeregningStoppnivaa
import no.nav.system.os.entiteter.beregningskjema.BeregningStoppnivaaDetaljer
import no.nav.system.os.entiteter.beregningskjema.BeregningsPeriode
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningRequest
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningResponse
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate

private val log = LoggerFactory.getLogger("SimuleringResponseMapper")

/**
 * https://confluence.adeo.no/display/OKSY/Returdata+fra+Oppdragssystemet+til+fagrutinen
 */
internal class SimuleringResponseMapper private constructor(
    val simulering: Simulering,
    val clock: Clock,
) {

    constructor(
        rawResponse: String,
        oppdragResponse: SimulerBeregningResponse,
        clock: Clock,
        saksnummer: Saksnummer,
    ) : this(oppdragResponse.toSimulering(saksnummer, rawResponse), clock)

    constructor(
        utbetaling: Utbetaling,
        simuleringsperiode: SimulerBeregningRequest.SimuleringsPeriode,
        clock: Clock,
    ) : this(
        utbetaling.mapTomResponsFraOppdrag(
            simuleringsperiode = simuleringsperiode.toPeriode(),
            clock = clock,
        ),
        clock,
    )
}

private fun SimulerBeregningRequest.SimuleringsPeriode.toPeriode(): Periode {
    return Periode.create(LocalDate.parse(datoSimulerFom), LocalDate.parse(datoSimulerTom))
}

private fun SimulerBeregningResponse.toSimulering(
    saksnummer: Saksnummer,
    rawResponse: String,
): Simulering {
    return Simulering(
        gjelderId = Fnr(simulering.gjelderId),
        gjelderNavn = simulering.gjelderNavn.trim(),
        datoBeregnet = LocalDate.parse(simulering.datoBeregnet),
        nettoBeløp = simulering.belop.toInt(),
        måneder = simulering.beregningsPeriode.map { it.toSimulertPeriode(saksnummer, rawResponse) },
        rawResponse = rawResponse,
    )
}

private fun BeregningsPeriode.toSimulertPeriode(
    saksnummer: Saksnummer,
    rawResponse: String,
): SimulertMåned {
    return SimulertMåned(
        måned = Måned.Companion.fra(LocalDate.parse(periodeFom), LocalDate.parse(periodeTom)),
        utbetaling = beregningStoppnivaa
            .filter { utbetaling ->
                val fagsystemId = utbetaling.fagsystemId.trim()
                (fagsystemId == saksnummer.toString()).also {
                    if (!it) {
                        log.debug(
                            "Simuleringen filtrerte vekk uønsket fagsystemid for saksnummer {}. fagsystemId={}. Se sikkerlogg for mer informasjon.",
                            saksnummer,
                            fagsystemId,
                        )
                        sikkerLogg.debug(
                            "Simuleringen filtrerte vekk uønsket fagsystemid for saksnummer {}. fagsystemId={}. rawResponse: {}",
                            saksnummer,
                            fagsystemId,
                            rawResponse,
                        )
                    }
                }
            }.filter { utbetaling ->
                val kodeFagomraade = utbetaling.kodeFagomraade.trim()
                (Fagområde.valuesAsStrings().contains(kodeFagomraade)).also {
                    if (!it) {
                        log.debug(
                            "Simuleringen filtrerte vekk uønsket kodeFagomraade for saksnummer {}. kodeFagomraade={}. Se sikkerlogg for mer informasjon.",
                            saksnummer,
                            kodeFagomraade,
                        )
                        sikkerLogg.debug(
                            "Simuleringen filtrerte vekk uønsket kodeFagomraade for saksnummer {}. kodeFagomraade={}. rawResponse: {}",
                            saksnummer,
                            kodeFagomraade,
                            rawResponse,
                        )
                    }
                }
            }.map {
                it.toSimulertUtbetaling()
            }.let {
                when {
                    it.isEmpty() -> null
                    it.size > 1 -> throw IllegalStateException("Simulering inneholder flere utbetalinger for samme sak $saksnummer. Se sikkerlogg for flere detaljer og feilmelding.").also {
                        sikkerLogg.error("Simulering inneholder flere utbetalinger for samme sak $saksnummer. rawResponse: $rawResponse")
                    }

                    else -> it.first()
                }
            },
    )
}

private fun BeregningStoppnivaa.toSimulertUtbetaling() =
    SimulertUtbetaling(
        fagSystemId = fagsystemId.trim(),
        utbetalesTilNavn = utbetalesTilNavn.trim(),
        utbetalesTilId = Fnr(utbetalesTilId),
        forfall = LocalDate.parse(forfall),
        feilkonto = isFeilkonto,
        detaljer = beregningStoppnivaaDetaljer
            .filter { detajl ->
                val typeKlasse = detajl.typeKlasse.trim()
                if (!KlasseType.contains(typeKlasse)) {
                    log.debug("\"Simuleringen inneholder ukjent typeKlasse: $typeKlasse for sak $fagsystemId.")
                }

                KlasseType.skalIkkeFiltreres().contains(typeKlasse)
            }
            .filter { detajl ->
                val klassekode = detajl.klassekode.trim()
                if (!KlasseKode.contains(klassekode)) {
                    log.debug("\"Simuleringen inneholder ukjent klassekode: $klassekode for sak $fagsystemId.")
                }

                KlasseKode.skalIkkeFiltreres().contains(klassekode)
            }
            .map { it.toSimulertDetalj() },
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
private fun Utbetaling.mapTomResponsFraOppdrag(
    simuleringsperiode: Periode,
    clock: Clock,
): Simulering {
    return Simulering(
        gjelderId = fnr,
        gjelderNavn = fnr.toString(), // Usually returned by response, which in this case is empty.
        datoBeregnet = LocalDate.now(clock),
        nettoBeløp = 0,
        måneder = SimulertMåned.create(simuleringsperiode),
        rawResponse = "Tom respons fra oppdrag.",
    )
}
