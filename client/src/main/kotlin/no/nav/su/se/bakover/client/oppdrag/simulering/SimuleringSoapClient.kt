package no.nav.su.se.bakover.client.oppdrag.simulering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ctc.wstx.exc.WstxEOFException
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.system.os.eksponering.simulerfpservicewsbinding.SimulerBeregningFeilUnderBehandling
import no.nav.system.os.eksponering.simulerfpservicewsbinding.SimulerFpService
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import org.slf4j.LoggerFactory
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
        utbetaling: Utbetaling,
    ): Either<SimuleringFeilet, Simulering> {
        val simulerRequest = SimuleringRequestBuilder(utbetaling).build()
        return try {
            simulerFpService.simulerBeregning(simulerRequest)?.response?.let {
                SimuleringResponseMapper(it, clock).simulering.right()
            } ?: SimuleringResponseMapper(utbetaling, simulerRequest.request.simuleringsPeriode, clock).simulering.right()
        } catch (e: SimulerBeregningFeilUnderBehandling) {
            log.warn("Funksjonell feil ved simulering, se sikkerlogg for detaljer", e)
            sikkerLogg.warn(
                "Simulering feilet med feiltype:${e.faultInfo.errorType}, feilmelding=${e.faultInfo.errorMessage} for request:${simulerRequest.print()}",
                e,
            )
            with(e.faultInfo.errorMessage) {
                when {
                    startsWith("Personen finnes ikke i TPS") -> SimuleringFeilet.PERSONEN_FINNES_IKKE_I_TPS.left()
                    startsWith("Finner ikke kjøreplansperiode for fom-dato") -> SimuleringFeilet.FINNER_IKKE_KJØREPLANSPERIODE_FOR_FOM.left()
                    startsWith("OPPDRAGET/FAGSYSTEM-ID finnes ikke") -> SimuleringFeilet.OPPDRAGET_FINNES_IKKE.left()
                    else -> SimuleringFeilet.FUNKSJONELL_FEIL.left()
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
            } else unknownTechnicalExceptionResponse(e)
        } catch (e: Throwable) {
            unknownTechnicalExceptionResponse(e)
        }
    }

    private fun SimulerBeregningRequest.print() = objectMapper.writeValueAsString(this)

    private fun unknownTechnicalExceptionResponse(exception: Throwable) = SimuleringFeilet.TEKNISK_FEIL.left().also {
        log.error("Ukjent teknisk feil ved simulering", exception)
    }

    private fun utenforÅpningstidResponse(exception: Throwable) = SimuleringFeilet.OPPDRAG_UR_ER_STENGT.left().also {
        log.error("Feil ved simulering, Oppdrag/UR er stengt", exception)
    }

    private val Throwable.rootCause: Throwable
        get() {
            var rootCause: Throwable = this
            while (rootCause.cause != null) rootCause = rootCause.cause!!
            return rootCause
        }
}
