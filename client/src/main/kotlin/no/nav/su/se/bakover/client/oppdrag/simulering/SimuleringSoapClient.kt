package no.nav.su.se.bakover.client.oppdrag.simulering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ctc.wstx.exc.WstxEOFException
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertDetaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertUtbetaling
import no.nav.system.os.eksponering.simulerfpservicewsbinding.SimulerBeregningFeilUnderBehandling
import no.nav.system.os.eksponering.simulerfpservicewsbinding.SimulerFpService
import no.nav.system.os.entiteter.beregningskjema.BeregningStoppnivaa
import no.nav.system.os.entiteter.beregningskjema.BeregningStoppnivaaDetaljer
import no.nav.system.os.entiteter.beregningskjema.BeregningsPeriode
import no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningResponse
import org.slf4j.LoggerFactory
import java.net.SocketException
import java.time.LocalDate
import javax.net.ssl.SSLException
import javax.xml.ws.WebServiceException
import javax.xml.ws.soap.SOAPFaultException

internal class SimuleringSoapClient(
    private val simulerFpService: SimulerFpService
) : SimuleringClient {
    private companion object {
        private val sikkerLogg = LoggerFactory.getLogger("sikkerLogg")
        private val log = LoggerFactory.getLogger(this::class.java)
    }

    override fun simulerUtbetaling(
        nyUtbetaling: NyUtbetaling
    ): Either<SimuleringFeilet, Simulering> {
        val simulerRequest = SimuleringRequestBuilder(nyUtbetaling).build()
        return try {
            simulerFpService.simulerBeregning(simulerRequest)?.response?.let {
                mapResponseToResultat(it)
            } ?: SimuleringFeilet.FUNKSJONELL_FEIL.left()
        } catch (e: SimulerBeregningFeilUnderBehandling) {
            log.error("Funksjonell feil ved simulering, se sikkerlogg for detaljer", e)
            sikkerLogg.error(
                "Simulering feilet med feilmelding=${e.faultInfo.errorMessage}, for request:${simulerRequest.print()}",
                e
            )
            SimuleringFeilet.FUNKSJONELL_FEIL.left()
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
        log.warn("Feil ved simulering, Oppdrag/UR er stengt", exception)
    }

    private val Throwable.rootCause: Throwable
        get() {
            var rootCause: Throwable = this
            while (rootCause.cause != null) rootCause = rootCause.cause!!
            return rootCause
        }

    private fun mapResponseToResultat(response: SimulerBeregningResponse) =
        Simulering(
            gjelderId = Fnr(response.simulering.gjelderId),
            gjelderNavn = response.simulering.gjelderNavn.trim(),
            datoBeregnet = LocalDate.parse(response.simulering.datoBeregnet),
            nettoBeløp = response.simulering.belop.intValueExact(),
            periodeList = response.simulering.beregningsPeriode.map { mapBeregningsPeriode(it) }
        ).right()

    private fun mapBeregningsPeriode(periode: BeregningsPeriode) =
        SimulertPeriode(
            fom = LocalDate.parse(periode.periodeFom),
            tom = LocalDate.parse(periode.periodeTom),
            utbetaling = periode.beregningStoppnivaa.map { mapBeregningStoppNivaa(it) }
        )

    private fun mapBeregningStoppNivaa(stoppNivaa: BeregningStoppnivaa) =
        SimulertUtbetaling(
            fagSystemId = stoppNivaa.fagsystemId.trim(),
            utbetalesTilNavn = stoppNivaa.utbetalesTilNavn.trim(),
            utbetalesTilId = Fnr(stoppNivaa.utbetalesTilId),
            forfall = LocalDate.parse(stoppNivaa.forfall),
            feilkonto = stoppNivaa.isFeilkonto,
            detaljer = stoppNivaa.beregningStoppnivaaDetaljer.map { mapDetaljer(it) }
        )

    private fun mapDetaljer(detaljer: BeregningStoppnivaaDetaljer) =
        SimulertDetaljer(
            faktiskFom = LocalDate.parse(detaljer.faktiskFom),
            faktiskTom = LocalDate.parse(detaljer.faktiskTom),
            konto = detaljer.kontoStreng.trim(),
            belop = detaljer.belop.intValueExact(),
            tilbakeforing = detaljer.isTilbakeforing,
            sats = detaljer.sats.intValueExact(),
            typeSats = detaljer.typeSats.trim(),
            antallSats = detaljer.antallSats.intValueExact(),
            uforegrad = detaljer.uforeGrad.intValueExact(),
            klassekode = detaljer.klassekode.trim(),
            klassekodeBeskrivelse = detaljer.klasseKodeBeskrivelse.trim(),
            klasseType = KlasseType.valueOf(detaljer.typeKlasse)
        )
}
