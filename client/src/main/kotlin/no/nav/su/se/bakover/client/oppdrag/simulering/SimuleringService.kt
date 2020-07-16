package no.nav.su.se.bakover.client.oppdrag.simulering

import com.ctc.wstx.exc.WstxEOFException
import no.nav.su.se.bakover.client.oppdrag.Oppdrag
import no.nav.su.se.bakover.client.oppdrag.Utbetalingslinjer
import no.nav.system.os.eksponering.simulerfpservicewsbinding.SimulerBeregningFeilUnderBehandling
import no.nav.system.os.eksponering.simulerfpservicewsbinding.SimulerFpService
import no.nav.system.os.entiteter.beregningskjema.BeregningStoppnivaa
import no.nav.system.os.entiteter.beregningskjema.BeregningStoppnivaaDetaljer
import no.nav.system.os.entiteter.beregningskjema.BeregningsPeriode
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningResponse
import org.slf4j.LoggerFactory
import java.net.SocketException
import java.time.LocalDate
import javax.net.ssl.SSLException
import javax.xml.ws.WebServiceException
import javax.xml.ws.soap.SOAPFaultException

internal class SimuleringService(
    private val simulerFpService: SimulerFpService
) : Oppdrag {
    private companion object {
        private val sikkerLogg = LoggerFactory.getLogger("sikkerLogg")
        private val log = LoggerFactory.getLogger(this::class.java)
    }

    override fun simulerOppdrag(utbetalingslinjer: Utbetalingslinjer): SimuleringResult {
        val simulerRequest = SimuleringRequestBuilder(
            utbetalingslinjer
        ).build()
        return try {
            simulerFpService.simulerBeregning(simulerRequest)?.response?.let {
                mapResponseToResultat(it)
            } ?: SimuleringResult(
                status = SimuleringStatus.FUNKSJONELL_FEIL,
                feilmelding = "Fikk ingen respons",
                simulering = null
            )
        } catch (e: SimulerBeregningFeilUnderBehandling) {
            log.error("Funksjonell feil ved simulering, se sikkerlogg for detaljer", e)
            sikkerLogg.error("Simulering feilet med feilmelding=${e.faultInfo.errorMessage}", e)
            SimuleringResult(
                status = SimuleringStatus.FUNKSJONELL_FEIL,
                feilmelding = e.faultInfo.errorMessage,
                simulering = null
            )
        } catch (e: SOAPFaultException) {
            when (e.cause) {
                is WstxEOFException -> utenforÅpningstidResponse(e)
                else -> unknownTechnicalExceptionResponse(e)
            }
        } catch (e: WebServiceException) {
            when (e.cause) {
                is SSLException, is SocketException -> utenforÅpningstidResponse(e)
                else -> unknownTechnicalExceptionResponse(e)
            }
        } catch (e: Throwable) {
            unknownTechnicalExceptionResponse(e)
        }
    }

    private fun unknownTechnicalExceptionResponse(exception: Throwable) = SimuleringResult(
        status = SimuleringStatus.TEKNISK_FEIL,
        feilmelding = "Fikk teknisk feil ved simulering",
        simulering = null
    ).also {
        log.error("Ukjent teknisk feil ved simulering", exception)
    }

    private fun utenforÅpningstidResponse(exception: Throwable) = SimuleringResult(
        status = SimuleringStatus.OPPDRAG_UR_ER_STENGT,
        feilmelding = "Oppdrag/UR er stengt",
        simulering = null
    ).also {
        log.warn("Feil ved simulering, Oppdrag/UR er stengt", exception)
    }

    private val Throwable.rootCause: Throwable
        get() {
            var rootCause: Throwable = this
            while (rootCause.cause != null) rootCause = rootCause.cause!!
            return rootCause
        }

    private fun mapResponseToResultat(response: SimulerBeregningResponse) =
        SimuleringResult(
            status = SimuleringStatus.OK,
            simulering = Simulering(
                gjelderId = response.simulering.gjelderId,
                gjelderNavn = response.simulering.gjelderNavn.trim(),
                datoBeregnet = LocalDate.parse(response.simulering.datoBeregnet),
                totalBelop = response.simulering.belop.intValueExact(),
                periodeList = response.simulering.beregningsPeriode.map { mapBeregningsPeriode(it) }
            )
        )

    private fun mapBeregningsPeriode(periode: BeregningsPeriode) =
        SimulertPeriode(fom = LocalDate.parse(periode.periodeFom),
            tom = LocalDate.parse(periode.periodeTom),
            utbetaling = periode.beregningStoppnivaa.map { mapBeregningStoppNivaa(it) })

    private fun mapBeregningStoppNivaa(stoppNivaa: BeregningStoppnivaa) =
        Utbetaling(fagSystemId = stoppNivaa.fagsystemId.trim(),
            utbetalesTilNavn = stoppNivaa.utbetalesTilNavn.trim(),
            utbetalesTilId = stoppNivaa.utbetalesTilId.removePrefix("00"),
            forfall = LocalDate.parse(stoppNivaa.forfall),
            feilkonto = stoppNivaa.isFeilkonto,
            detaljer = stoppNivaa.beregningStoppnivaaDetaljer.map { mapDetaljer(it) })

    private fun mapDetaljer(detaljer: BeregningStoppnivaaDetaljer) =
        Detaljer(
            faktiskFom = LocalDate.parse(detaljer.faktiskFom),
            faktiskTom = LocalDate.parse(detaljer.faktiskTom),
            uforegrad = detaljer.uforeGrad.intValueExact(),
            antallSats = detaljer.antallSats.intValueExact(),
            typeSats = detaljer.typeSats.trim(),
            sats = detaljer.sats.intValueExact(),
            belop = detaljer.belop.intValueExact(),
            konto = detaljer.kontoStreng.trim(),
            tilbakeforing = detaljer.isTilbakeforing,
            klassekode = detaljer.klassekode.trim(),
            klassekodeBeskrivelse = detaljer.klasseKodeBeskrivelse.trim(),
            utbetalingsType = detaljer.typeKlasse,
            refunderesOrgNr = detaljer.refunderesOrgNr.removePrefix("00")
        )
}
