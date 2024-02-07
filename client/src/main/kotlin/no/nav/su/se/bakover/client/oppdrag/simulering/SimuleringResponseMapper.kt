package no.nav.su.se.bakover.client.oppdrag.simulering

import arrow.core.Either
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import økonomi.domain.Fagområde
import økonomi.domain.KlasseKode
import økonomi.domain.KlasseType
import økonomi.domain.simulering.Simulering
import økonomi.domain.simulering.SimuleringFeilet
import økonomi.domain.simulering.SimulertDetaljer
import økonomi.domain.simulering.SimulertMåned
import økonomi.domain.simulering.SimulertUtbetaling
import java.time.Clock
import java.time.LocalDate

private val defaultLog = LoggerFactory.getLogger("SimuleringResponseMapper")

/**
 * https://confluence.adeo.no/display/OKSY/Returdata+fra+Oppdragssystemet+til+fagrutinen
 *
 * @param log For testing.
 * @param sikkerLogg For testing.
 */
fun mapSimuleringResponse(
    saksnummer: Saksnummer,
    fnr: Fnr,
    simuleringsperiode: Periode,
    soapRequest: String,
    soapResponse: String,
    clock: Clock,
    log: Logger = defaultLog,
    sikkerLogg: Logger = no.nav.su.se.bakover.common.sikkerLogg,
): Either<SimuleringFeilet.TekniskFeil, Simulering> {
    return Either.catch {
        val response = soapResponse.deserializeSimulerBeregningResponse()
        if (response == null) {
            mapTomResponsFraOppdrag(
                simuleringsperiode = simuleringsperiode,
                clock = clock,
                fnr = fnr,
                rawResponse = soapResponse,
            )
        } else {
            Simulering(
                gjelderId = Fnr(response.gjelderId),
                gjelderNavn = response.gjelderNavn.trim(),
                datoBeregnet = LocalDate.parse(response.datoBeregnet),
                nettoBeløp = response.belop.toBigDecimal().intValueExact(),
                måneder =
                response.beregningsPeriode.map {
                    it.toSimulertPeriode(
                        saksnummer = saksnummer,
                        soapRequest = soapRequest,
                        soapResponse = soapResponse,
                        log = log,
                    )
                }.ifEmpty {
                    // Merknad jah: Usikker på om dette faktisk kan inntreffe. Tror denne alltid kommer med dersom vi først har et simuleringsobjekt.
                    SimulertMåned.create(simuleringsperiode)
                },
                rawResponse = soapResponse,
            )
        }
    }.mapLeft { throwable ->
        log.error("Kunne ikke mappe SimulerBeregningResponse til Simulering for saksnummer $saksnummer. Se sikkerlogg for stacktrace og context.")
        sikkerLogg.error(
            "Kunne ikke mappe SimulerBeregningResponse til Simulering for saksnummer $saksnummer. Response: $soapResponse, SoapRequest: $soapRequest",
            throwable,
        )
        SimuleringFeilet.TekniskFeil
    }
}

/**
 * @param soapResponse Brukes kun for logging.
 * @param soapRequest Brukes kun for logging.
 */
private fun SimulerBeregningResponse.BeregningsPeriode.toSimulertPeriode(
    saksnummer: Saksnummer,
    soapResponse: String,
    soapRequest: String,
    log: Logger,
): SimulertMåned {
    return SimulertMåned(
        måned = Måned.fra(LocalDate.parse(periodeFom), LocalDate.parse(periodeTom)),
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
                            "Simuleringen filtrerte vekk uønsket fagsystemid for saksnummer {}. fagsystemId={}. soapResponse: {}, soapRequest: {}",
                            saksnummer,
                            fagsystemId,
                            soapResponse,
                            soapRequest,
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
                            "Simuleringen filtrerte vekk uønsket kodeFagomraade for saksnummer {}. kodeFagomraade={}. soapResponse: {}, soapRequest: {}",
                            saksnummer,
                            kodeFagomraade,
                            soapResponse,
                            soapRequest,
                        )
                    }
                }
            }.map {
                it.toSimulertUtbetaling(log = log)
            }.let {
                when {
                    it.isEmpty() -> null
                    // Vi fanger og logger exceptions ytterst.
                    it.size > 1 -> throw IllegalStateException("Simulering inneholder flere utbetalinger for samme sak $saksnummer.")
                    else -> it.first()
                }
            },
    )
}

private fun SimulerBeregningResponse.BeregningStoppnivaa.toSimulertUtbetaling(
    log: Logger,
): SimulertUtbetaling {
    return SimulertUtbetaling(
        fagSystemId = fagsystemId.trim(),
        utbetalesTilNavn = utbetalesTilNavn.trim(),
        utbetalesTilId = Fnr(utbetalesTilId),
        forfall = LocalDate.parse(forfall),
        feilkonto = feilkonto.toBooleanStrict(),
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
}

private fun SimulerBeregningResponse.BeregningStoppnivaaDetaljer.toSimulertDetalj() =
    SimulertDetaljer(
        faktiskFraOgMed = LocalDate.parse(faktiskFom),
        faktiskTilOgMed = LocalDate.parse(faktiskTom),
        konto = kontoStreng.trim(),
        belop = belop.toBigDecimal().intValueExact(),
        tilbakeforing = tilbakeforing.toBooleanStrict(),
        sats = sats.toBigDecimal().intValueExact(),
        typeSats = typeSats.trim(),
        antallSats = antallSats.toBigDecimal().intValueExact(),
        uforegrad = uforeGrad.toBigDecimal().intValueExact(),
        klassekode = KlasseKode.valueOf(klassekode.trim()),
        klassekodeBeskrivelse = klasseKodeBeskrivelse.trim(),
        klasseType = KlasseType.valueOf(typeKlasse.trim()),
    )

/**
 * Return something with meaning for our domain for cases where simulering returns an empty response.
 * In functional terms, an empty response means that OS/UR won't perform any payments for the period in question.
 */
private fun mapTomResponsFraOppdrag(
    simuleringsperiode: Periode,
    clock: Clock,
    fnr: Fnr,
    rawResponse: String,
): Simulering {
    return Simulering(
        gjelderId = fnr,
        // Usually returned by response, which in this case is empty.
        gjelderNavn = fnr.toString(),
        datoBeregnet = LocalDate.now(clock),
        nettoBeløp = 0,
        måneder = SimulertMåned.create(simuleringsperiode),
        rawResponse = rawResponse,
    )
}
