package no.nav.su.se.bakover.client.oppdrag.simulering

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.oppdrag.Fagområde
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseKode
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseKode.Companion.skalIkkeFiltreres
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertDetaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertUtbetaling
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.system.os.entiteter.beregningskjema.BeregningStoppnivaa
import no.nav.system.os.entiteter.beregningskjema.BeregningStoppnivaaDetaljer
import no.nav.system.os.entiteter.beregningskjema.BeregningsPeriode
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningRequest
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningResponse
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
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
        rawXml: String,
        oppdragResponse: SimulerBeregningResponse,
        clock: Clock,
        saksnummer: Saksnummer,
    ) : this(oppdragResponse.toSimulering(saksnummer, rawXml), clock)

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

private fun SimulerBeregningRequest.SimuleringsPeriode.toPeriode() =
    Periode.create(LocalDate.parse(datoSimulerFom), LocalDate.parse(datoSimulerTom))

private fun SimulerBeregningResponse.toSimulering(
    saksnummer: Saksnummer,
    rawXml: String,
): Simulering {
    return Simulering(
        gjelderId = Fnr(simulering.gjelderId),
        gjelderNavn = simulering.gjelderNavn.trim(),
        datoBeregnet = LocalDate.parse(simulering.datoBeregnet),
        nettoBeløp = simulering.belop.toInt(),
        periodeList = simulering.beregningsPeriode.map { it.toSimulertPeriode(saksnummer, rawXml) },
        rawXml = rawXml,
    )
}

private fun BeregningsPeriode.toSimulertPeriode(
    saksnummer: Saksnummer,
    rawXml: String,
): SimulertPeriode {
    return SimulertPeriode(
        fraOgMed = LocalDate.parse(periodeFom),
        tilOgMed = LocalDate.parse(periodeTom),
        utbetaling = beregningStoppnivaa
            .filter { utbetaling ->
                utbetaling.fagsystemId.trim() == saksnummer.toString() &&
                    Fagområde.valuesAsStrings().contains(utbetaling.kodeFagomraade.trim())
            }.map {
                it.toSimulertUtbetaling()
            }.let {
                if (beregningStoppnivaa.size != it.size) {
                    log.debug("Simuleringen filtrerte vekk uønskede fagsystemid'er for saksnummer $saksnummer. Simuleringen sin beregningsperiode: $this. Se sikkerlogg for mer informasjon")
                    sikkerLogg.debug("Simuleringen filtrerte vekk uønskede fagsystemid'er for saksnummer $saksnummer. Simuleringen sin beregningsperiode: $this. Før filtrering: $beregningStoppnivaa, etter filtrering: $it. rawXml: $rawXml")
                }
                when {
                    it.isEmpty() -> null
                    it.size > 1 -> throw IllegalStateException("Simulering inneholder flere utbetalinger for samme saksnummer. Se sikkerlogg for flere detaljer og feilmelding.").also {
                        sikkerLogg.error("Simulering inneholder flere utbetalinger for samme sak $saksnummer. Simuleringen sin beregningsperiode: $this. rawXml: $rawXml")
                    }

                    else -> it.first().also {
                        it.detaljer.map { it.klassekode.name }.filterNot {
                            skalIkkeFiltreres().contains(it)
                        }.ifNotEmpty {
                            sikkerLogg.error("Simuleringen sin beregningsperiode inneholder uønskede klassekoder: $this for sak $saksnummer. Beregningperiode: $this. ")
                            throw IllegalStateException("Simuleringen sin beregningsperiode inneholder uønskede klassekoder: $this for sak $saksnummer. Se sikkerlogg for mer informasjon. ")
                        }
                    }
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
            .filter { detajl -> KlasseType.skalIkkeFiltreres().map { it.name }.contains(detajl.typeKlasse.trim()) }
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
        periodeList = listOf(
            SimulertPeriode(
                fraOgMed = simuleringsperiode.fraOgMed,
                tilOgMed = simuleringsperiode.tilOgMed,
                utbetaling = null,
            ),
        ),
        rawXml = "Tom respons fra oppdrag.",
    )
}
