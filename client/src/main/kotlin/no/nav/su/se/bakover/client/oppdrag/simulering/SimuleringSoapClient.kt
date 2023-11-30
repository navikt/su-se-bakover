package no.nav.su.se.bakover.client.oppdrag.simulering

import arrow.core.Either
import arrow.core.left
import com.ctc.wstx.exc.WstxEOFException
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.system.os.eksponering.simulerfpservicewsbinding.SimulerBeregningFeilUnderBehandling
import no.nav.system.os.eksponering.simulerfpservicewsbinding.SimulerFpService
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningResponse
import org.slf4j.LoggerFactory
import økonomi.domain.simulering.Simulering
import økonomi.domain.simulering.SimuleringClient
import økonomi.domain.simulering.SimuleringFeilet
import økonomi.domain.utbetaling.Utbetaling
import java.net.SocketException
import java.time.Clock
import javax.net.ssl.SSLException
import javax.xml.ws.WebServiceException
import javax.xml.ws.soap.SOAPFaultException

internal class SimuleringSoapClient(
    private val simulerFpService: SimulerFpService,
    private val clock: Clock,
) : SimuleringClient {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun simulerUtbetaling(
        utbetalingForSimulering: Utbetaling.UtbetalingForSimulering,
    ): Either<SimuleringFeilet, Simulering> {
        val simulerRequest = SimuleringRequestBuilder(utbetalingForSimulering).build()
        return try {
            simulerFpService.simulerBeregning(simulerRequest)?.response.let { response: SimulerBeregningResponse? ->
                response.toSimulering(
                    request = utbetalingForSimulering,
                    soapRequest = simulerRequest,
                    clock = clock,
                )
            }
        } catch (e: SimulerBeregningFeilUnderBehandling) {
            log.warn("Funksjonell feil ved simulering, se sikkerlogg for detaljer", e)
            sikkerLogg.warn(
                "Simulering feilet med feiltype:${e.faultInfo.errorType}, feilmelding=${e.faultInfo.errorMessage} for request:${simulerRequest.print()}",
                e,
            )
            with(e.faultInfo.errorMessage) {
                when {
                    startsWith("Personen finnes ikke i TPS") -> SimuleringFeilet.PersonFinnesIkkeITPS.left()
                    startsWith("Finner ikke kjøreplansperiode for fom-dato") -> SimuleringFeilet.FinnerIkkeKjøreplanForFraOgMed.left()
                    startsWith("OPPDRAGET/FAGSYSTEM-ID finnes ikke") -> SimuleringFeilet.OppdragEksistererIkke.left()
                    else -> SimuleringFeilet.FunksjonellFeil.left()
                }
            }
        } catch (e: SOAPFaultException) {
            when (e.cause) {
                is WstxEOFException -> utenforÅpningstidResponse(e)
                else -> unknownTechnicalExceptionResponse(e)
            }
        } catch (e: WebServiceException) {
            if (e.cause is SSLException || e.rootCause is SocketException) {
                utenforÅpningstidResponse(e)
            } else {
                unknownTechnicalExceptionResponse(e)
            }
        } catch (e: Throwable) {
            unknownTechnicalExceptionResponse(e)
        }
    }

    private fun SimulerBeregningRequest.print() = serialize(this)

    private fun unknownTechnicalExceptionResponse(exception: Throwable) = SimuleringFeilet.TekniskFeil.left().also {
        log.error("Ukjent teknisk feil ved simulering", exception)
    }

    private fun utenforÅpningstidResponse(exception: Throwable) = SimuleringFeilet.UtenforÅpningstid.left().also {
        log.error("Feil ved simulering, Oppdrag/UR er stengt", exception)
    }

    private val Throwable.rootCause: Throwable
        get() {
            var rootCause: Throwable = this
            while (rootCause.cause != null) rootCause = rootCause.cause!!
            return rootCause
        }
}
