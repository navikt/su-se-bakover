package no.nav.su.se.bakover.client.oppdrag.simulering

import arrow.core.Either
import arrow.core.getOrElse
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.infrastructure.xml.xmlMapper
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.oppdrag.Fagområde
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.system.os.entiteter.beregningskjema.BeregningStoppnivaa
import no.nav.system.os.entiteter.beregningskjema.BeregningStoppnivaaDetaljer
import no.nav.system.os.entiteter.beregningskjema.BeregningsPeriode
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import økonomi.domain.KlasseKode
import økonomi.domain.KlasseType
import økonomi.domain.simulering.Simulering
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
fun SimulerBeregningResponse?.toSimulering(
    request: Utbetaling.UtbetalingForSimulering,
    clock: Clock,
    soapRequest: SimulerBeregningRequest,
    log: Logger = defaultLog,
    sikkerLogg: Logger = no.nav.su.se.bakover.common.sikkerLogg,
): Either<SimuleringFeilet.TekniskFeil, Simulering> {
    val saksnummer = request.saksnummer
    // TODO jah: Ideelt sett burde vi fått tak i den rå XMLen, men CXF gjør det ikke så lett for oss (OutInterceptor).
    // Siden dette er javaklasser uten toString() så serialiserer vi de før vi lagrer/logger.
    val rawResponse: String? = this?.let {
        serializeSoapMessage(log = log, sikkerLogg = sikkerLogg, message = this, request = request)
    }
    val rawRequest: String = serializeSoapMessage(log = log, sikkerLogg = sikkerLogg, message = soapRequest, request = request)
    return Either.catch {
        if (this == null) {
            request.mapTomResponsFraOppdrag(
                simuleringsperiode = request.periode,
                clock = clock,
            )
        } else {
            Simulering(
                gjelderId = Fnr(simulering.gjelderId),
                gjelderNavn = simulering.gjelderNavn.trim(),
                datoBeregnet = LocalDate.parse(simulering.datoBeregnet),
                nettoBeløp = simulering.belop.toInt(),
                måneder = simulering.beregningsPeriode.map {
                    it.toSimulertPeriode(
                        saksnummer = saksnummer,
                        request = request,
                        rawRequest = rawRequest,
                        rawResponse = rawResponse!!,
                        log = log,
                    )
                },
                rawResponse = rawResponse!!,
            )
        }
    }.mapLeft { throwable ->
        log.error("Kunne ikke mappe SimulerBeregningResponse til Simulering for saksnummer $saksnummer. Se sikkerlogg for stacktrace og context.")
        sikkerLogg.error(
            "Kunne ikke mappe SimulerBeregningResponse til Simulering for saksnummer $saksnummer. Response: $rawResponse, SoapRequest: $rawRequest, Request: $request",
            throwable,
        )
        SimuleringFeilet.TekniskFeil
    }
}

private fun serializeSoapMessage(
    log: Logger,
    sikkerLogg: Logger,
    message: Any,
    request: Utbetaling.UtbetalingForSimulering,
): String {
    return Either.catch {
        xmlMapper.writeValueAsString(message)
    }.getOrElse { throwable ->
        "Kunne ikke serialisere ${message.javaClass.simpleName} til XML, se sikkerlogg for stacktrace og context. Lagrer denne strengen istedenfor.".also { errorMessage ->
            log.error(errorMessage)
            sikkerLogg.error(
                "Kunne ikke serialisere ${message.javaClass.simpleName} til XML. Request: $request",
                throwable,
            )
        }
    }
}

/**
 * @param rawResponse Brukes kun for logging.
 * @param request Brukes kun for logging.
 * @param rawRequest Brukes kun for logging.
 */
private fun BeregningsPeriode.toSimulertPeriode(
    saksnummer: Saksnummer,
    rawResponse: String,
    request: Utbetaling.UtbetalingForSimulering,
    rawRequest: String,
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
                            "Simuleringen filtrerte vekk uønsket fagsystemid for saksnummer {}. fagsystemId={}. soapResponse: {}, soapRequest: {}, request: {}",
                            saksnummer,
                            fagsystemId,
                            rawResponse,
                            rawRequest,
                            request,
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
                            "Simuleringen filtrerte vekk uønsket kodeFagomraade for saksnummer {}. kodeFagomraade={}. soapResponse: {}, soapRequest: {}, request: {}",
                            saksnummer,
                            kodeFagomraade,
                            rawResponse,
                            rawRequest,
                            request,
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

private fun BeregningStoppnivaa.toSimulertUtbetaling(
    log: Logger,
): SimulertUtbetaling {
    return SimulertUtbetaling(
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
}

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
        // Usually returned by response, which in this case is empty.
        gjelderNavn = fnr.toString(),
        datoBeregnet = LocalDate.now(clock),
        nettoBeløp = 0,
        måneder = SimulertMåned.create(simuleringsperiode),
        rawResponse = "Tom respons fra oppdrag.",
    )
}
