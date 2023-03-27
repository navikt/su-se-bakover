package no.nav.su.se.bakover.client.oppdrag.simulering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import com.ctc.wstx.exc.WstxEOFException
import io.getunleash.Unleash
import no.nav.su.se.bakover.client.oppdrag.XmlMapper
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.system.os.eksponering.simulerfpservicewsbinding.SimulerBeregningFeilUnderBehandling
import no.nav.system.os.eksponering.simulerfpservicewsbinding.SimulerFpService
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import org.slf4j.LoggerFactory
import java.net.SocketException
import java.time.Clock
import javax.net.ssl.SSLException
import javax.xml.ws.WebServiceException
import javax.xml.ws.soap.SOAPFaultException

internal class SimuleringSoapClient(
    private val simulerFpService: SimulerFpService,
    private val clock: Clock,
    private val unleash: Unleash,
) : SimuleringClient {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun simulerUtbetaling(
        request: SimulerUtbetalingRequest,
    ): Either<SimuleringFeilet, Simulering> {
        val simulerRequest = SimuleringRequestBuilder(request).build()
        return try {
            simulerFpService.simulerBeregning(simulerRequest)?.response?.let {
                unleash.isEnabled("supstonad.logg.simulering").ifTrue {
                    sikkerLogg.debug(
                        """
                            Request: ${objectMapper.writeValueAsString(simulerRequest)},
                            Response: ${objectMapper.writeValueAsString(it)}
                        """.trimIndent(),
                    )
                }
                // TODO jah: Ideelt sett burde vi fått tak i den rå XMLen, men CXF gjør det ikke så lett for oss (OutInterceptor).
                val rawResponse: String = Either.catch {
                    XmlMapper.writeValueAsString(it)
                }.getOrElse {
                    log.error("Kunne ikke simulere SimulerBeregningResponse til xml, se sikkerlogg for stacktrace.")
                    sikkerLogg.error("Kunne ikke simulere SimulerBeregningResponse til xml.", it)
                    "Kunne ikke simulere SimulerBeregningResponse til xml, se sikkerlogg for stacktrace."
                }
                SimuleringResponseMapper(
                    rawResponse = rawResponse,
                    oppdragResponse = it,
                    clock = clock,
                    saksnummer = request.utbetaling.saksnummer,
                ).simulering.right()
            } ?: SimuleringResponseMapper(
                utbetaling = request.utbetaling,
                simuleringsperiode = simulerRequest.request.simuleringsPeriode,
                clock = clock,
            ).simulering.right()
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

    private fun SimulerBeregningRequest.print() = objectMapper.writeValueAsString(this)

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
