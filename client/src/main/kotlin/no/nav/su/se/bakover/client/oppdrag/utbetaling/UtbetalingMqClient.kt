package no.nav.su.se.bakover.client.oppdrag.utbetaling

import arrow.core.Either
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator
import no.nav.su.se.bakover.client.oppdrag.MqClient
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest.Avstemming
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest.OppdragsEnhet
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest.Oppdragslinje.FradragTillegg
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest.Oppdragslinje.KodeEndringLinje
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest.Oppdragslinje.TypeSats
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest.Utbetalingsfrekvens
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingClient
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingClient.KunneIkkeSendeUtbetaling
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class UtbetalingMqClient(
    private val clock: Clock = Clock.systemUTC(),
    private val mqClient: MqClient
) : UtbetalingClient {

    companion object {
        const val FAGOMRÅDE = "SUUFORE"
        const val KLASSEKODE = "SUUFORE"
        const val SAKSBEHANDLER = "SU"

        fun LocalDate.toOppdragDate() = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.systemDefault()).format(this)

        fun Instant.toOppdragTimestamp() = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")
            .withZone(ZoneId.systemDefault()).format(this)
    }

    val xmlMapper = XmlMapper(
        JacksonXmlModule().apply { setDefaultUseWrapper(false) }
    ).apply {
        configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true)
        enable(SerializationFeature.INDENT_OUTPUT)
        setSerializationInclusion(JsonInclude.Include.NON_NULL)
    }

    override fun sendUtbetaling(
        utbetaling: Utbetaling,
        oppdragGjelder: String
    ): Either<KunneIkkeSendeUtbetaling, Unit> {
        val xml = xmlMapper.writeValueAsString(utbetaling.toExternal(oppdragGjelder))
        return mqClient.publish(xml).mapLeft { KunneIkkeSendeUtbetaling }
    }

    private fun Utbetaling.toExternal(oppdragGjelder: String) = UtbetalingRequest(
        oppdrag = UtbetalingRequest.Oppdrag(
            kodeAksjon = UtbetalingRequest.KodeAksjon.EN,
            kodeEndring = UtbetalingRequest.KodeEndring.NY,
            kodeFagomraade = FAGOMRÅDE,
            fagsystemId = this.oppdragId.toString(),
            utbetFrekvens = Utbetalingsfrekvens.MND,
            oppdragGjelderId = oppdragGjelder,
            saksbehId = SAKSBEHANDLER,
            datoOppdragGjelderFom = LocalDate.EPOCH.toOppdragDate(),
            oppdragsEnheter = listOf(
                OppdragsEnhet(
                    enhet = "8020",
                    typeEnhet = "BOS",
                    datoEnhetFom = LocalDate.EPOCH.toOppdragDate()
                )
            ),
            avstemming = Avstemming(
                nokkelAvstemming = "TODO", // TODO hent verdi
                tidspktMelding = now(clock).toOppdragTimestamp(),
                kodeKomponent = KLASSEKODE // TODO. Verifiser hva denne skal være
            ),
            oppdragslinjer = utbetalingslinjer.map {
                UtbetalingRequest.Oppdragslinje(
                    kodeEndringLinje = KodeEndringLinje.NY,
                    delytelseId = it.id.toString(),
                    kodeKlassifik = KLASSEKODE,
                    datoVedtakFom = it.fom.toOppdragDate(),
                    datoVedtakTom = it.tom.toOppdragDate(),
                    sats = it.beløp.toString(),
                    fradragTillegg = FradragTillegg.TILLEGG,
                    typeSats = TypeSats.MND,
                    brukKjoreplan = "N",
                    saksbehId = SAKSBEHANDLER,
                    utbetalesTilId = oppdragGjelder,
                    refDelytelseId = it.forrigeUtbetalingslinjeId?.toString()
                )
            }
        )
    )
}
