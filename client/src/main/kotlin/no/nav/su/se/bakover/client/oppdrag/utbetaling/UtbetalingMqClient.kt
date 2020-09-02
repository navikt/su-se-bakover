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
        object OppdragDefaults {
            const val KODE_FAGOMRÅDE = "SUUFORE"
            const val SAKSBEHANDLER_ID = "SU"
            val utbetalingsfrekvens = Utbetalingsfrekvens.MND
            val oppdragKodeendring = UtbetalingRequest.KodeEndring.NY // TODO: Denne må endres til å være dynamisk etter vi har lest/lagret kvitteringsresponsen
            val datoOppdragGjelderFom = LocalDate.EPOCH.toOppdragDate()
            const val AVSTEMMING_KODE_KOMPONENT = "SUUFORE"
            val oppdragsenheter = listOf(
                OppdragsEnhet(
                    enhet = "8020",
                    typeEnhet = "BOS",
                    datoEnhetFom = LocalDate.EPOCH.toOppdragDate()
                )
            )
        }

        object OppdragslinjeDefaults {
            val kodeEndring = UtbetalingRequest.Oppdragslinje.KodeEndringLinje.NY
            const val KODE_KLASSIFIK = "SUUFORE"
            val fradragEllerTillegg = FradragTillegg.TILLEGG
            const val SAKSBEHANDLER_ID = "SU"
            val typeSats = TypeSats.MND
            const val BRUK_KJOREPLAN = "N"
        }

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

    private fun Utbetaling.toExternal(oppdragGjelder: String): UtbetalingRequest {

        return UtbetalingRequest(
            oppdrag = UtbetalingRequest.Oppdrag(
                kodeAksjon = UtbetalingRequest.KodeAksjon.EN, // Kodeaksjon brukes ikke av simulering
                kodeEndring = OppdragDefaults.oppdragKodeendring,
                kodeFagomraade = OppdragDefaults.KODE_FAGOMRÅDE,
                fagsystemId = oppdragId.toString(),
                utbetFrekvens = OppdragDefaults.utbetalingsfrekvens,
                oppdragGjelderId = oppdragGjelder,
                saksbehId = OppdragDefaults.SAKSBEHANDLER_ID,
                datoOppdragGjelderFom = OppdragDefaults.datoOppdragGjelderFom,
                oppdragsEnheter = OppdragDefaults.oppdragsenheter,
                avstemming = Avstemming( // Avstemming brukes ikke av simulering
                    nokkelAvstemming = this.id.toString(),
                    tidspktMelding = now(clock).toOppdragTimestamp(),
                    kodeKomponent = OppdragDefaults.AVSTEMMING_KODE_KOMPONENT
                ),
                oppdragslinjer = utbetalingslinjer.map {
                    UtbetalingRequest.Oppdragslinje(
                        kodeEndringLinje = OppdragslinjeDefaults.kodeEndring,
                        delytelseId = it.id.toString(),
                        kodeKlassifik = OppdragslinjeDefaults.KODE_KLASSIFIK,
                        datoVedtakFom = it.fom.toOppdragDate(),
                        datoVedtakTom = it.tom.toOppdragDate(),
                        sats = it.beløp.toString(),
                        fradragTillegg = OppdragslinjeDefaults.fradragEllerTillegg,
                        typeSats = OppdragslinjeDefaults.typeSats,
                        brukKjoreplan = OppdragslinjeDefaults.BRUK_KJOREPLAN,
                        saksbehId = OppdragslinjeDefaults.SAKSBEHANDLER_ID,
                        utbetalesTilId = oppdragGjelder,
                        refDelytelseId = it.forrigeUtbetalingslinjeId?.toString()
                    )
                }
            )
        )
    }
}
