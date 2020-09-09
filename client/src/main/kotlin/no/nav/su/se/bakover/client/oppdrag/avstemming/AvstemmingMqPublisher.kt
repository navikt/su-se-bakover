package no.nav.su.se.bakover.client.oppdrag.avstemming

import arrow.core.Either
import arrow.core.left
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator
import no.nav.su.se.bakover.client.oppdrag.MqPublisher
import no.nav.su.se.bakover.client.oppdrag.avstemming.Aksjonsdata.AksjonType
import no.nav.su.se.bakover.client.oppdrag.avstemming.Aksjonsdata.AksjonType.AVSLUTT
import no.nav.su.se.bakover.client.oppdrag.avstemming.Aksjonsdata.AksjonType.DATA
import no.nav.su.se.bakover.client.oppdrag.avstemming.Aksjonsdata.AksjonType.START
import no.nav.su.se.bakover.client.oppdrag.avstemming.Aksjonsdata.AvstemmingType
import no.nav.su.se.bakover.client.oppdrag.avstemming.Aksjonsdata.KildeType
import no.nav.su.se.bakover.client.oppdrag.avstemming.AvstemmingDataRequest.Fortegn.TILLEGG
import no.nav.su.se.bakover.client.oppdrag.avstemming.AvstemmingDataRequest.Grunnlagdata
import no.nav.su.se.bakover.client.oppdrag.avstemming.AvstemmingDataRequest.Periodedata
import no.nav.su.se.bakover.client.oppdrag.avstemming.AvstemmingDataRequest.Totaldata
import no.nav.su.se.bakover.domain.oppdrag.Kvittering.Utbetalingsstatus.FEIL
import no.nav.su.se.bakover.domain.oppdrag.Kvittering.Utbetalingsstatus.OK_MED_VARSEL
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher.KunneIkkeSendeAvstemming
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Denne skal kunne lage avstemmingsxml og sende på mq til oppdrag
// Den trenger Clock for å kunne sette tidspunkt i testene
// Den trenger mqPublisher for å kunne sende melding på mq
// Den trenger xmlMapper for å kunne mappe en klasse -> xml

class AvstemmingMqPublisher(
    private val clock: Clock = Clock.systemUTC(),
    private val mqPublisher: MqPublisher,
    private val xmlMapper: XmlMapper = XmlMapper(
        JacksonXmlModule().apply { setDefaultUseWrapper(false) }
    ).apply {
        configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true)
        enable(SerializationFeature.INDENT_OUTPUT)
        setSerializationInclusion(JsonInclude.Include.NON_NULL)
    }
) : AvstemmingPublisher {

    val yyyyMMddHH = DateTimeFormatter.ofPattern("yyyyMMddHH")

    override fun publish(
        utbetalinger: List<Utbetaling>
    ): Either<KunneIkkeSendeAvstemming, Unit> {

        val startRequest = AvstemmingStartRequest(lagAksjonsdata(START))
        val stoppRequest = AvstemmingStoppRequest(lagAksjonsdata(AVSLUTT))
        publishStart(startRequest).fold(
            { return it.left() },
            { }
        )

        val utbetalingslinjer = utbetalinger.flatMap {
            it.utbetalingslinjer
        }
        val dataRequest: AvstemmingDataRequest = AvstemmingDataRequest(
            aksjon = lagAksjonsdata(DATA),
            total = Totaldata(
                totalAntall = utbetalinger.size,
                totalBelop = utbetalingslinjer.sumBy {
                    it.beløp
                }.toBigDecimal(),
                fortegn = TILLEGG
            ),
            periode = Periodedata(
                datoAvstemtFom = utbetalinger.minByOrNull { it.opprettet }!!.opprettet.toAvstemmingsdatoFormat(),
                datoAvstemtTom = utbetalinger.maxByOrNull { it.opprettet }!!.opprettet.toAvstemmingsdatoFormat()
            ),
            grunnlag = Grunnlagdata(
                godkjentAntall = utbetalinger.count { it.erUtbetalt() },
                godkjentBelop = utbetalinger.filter { it.erUtbetalt() }.flatMap { it.utbetalingslinjer }
                    .sumBy { it.beløp }.toBigDecimal(),
                godkjenttFortegn = TILLEGG,
                varselAntall = utbetalinger.mapNotNull { it.getKvittering() }.map { it.utbetalingsstatus }
                    .filter { it == OK_MED_VARSEL }.size,
                varselBelop = utbetalinger.filter { it.getKvittering()?.utbetalingsstatus == OK_MED_VARSEL }
                    .flatMap { it.utbetalingslinjer }.sumBy { it.beløp }.toBigDecimal(),
                varselFortegn = TILLEGG,
                avvistAntall = utbetalinger.mapNotNull { it.getKvittering() }.map { it.utbetalingsstatus }
                    .filter { it == FEIL }.size,
                avvistBelop = utbetalinger.filter { it.getKvittering()?.utbetalingsstatus == FEIL }
                    .flatMap { it.utbetalingslinjer }.sumBy { it.beløp }.toBigDecimal(),
                avvistFortegn = TILLEGG,
                manglerAntall = 0,
                manglerBelop = BigDecimal.ZERO,
                manglerFortegn = TILLEGG
            ),
            detalj = listOf()
        )

        publishData(dataRequest).fold(
            { return it.left() },
            { }
        )

        return publishStopp(stoppRequest)
    }

    fun publishStart(
        startRequest: AvstemmingStartRequest
    ): Either<KunneIkkeSendeAvstemming, Unit> {
        val xml = xmlMapper.writeValueAsString(startRequest)
        return mqPublisher.publish(xml).mapLeft { KunneIkkeSendeAvstemming }
    }

    fun Instant.toAvstemmingsdatoFormat() = this.atZone(ZoneId.of("Europe/Oslo")).format(yyyyMMddHH)

    fun publishData(
        dataRequest: AvstemmingDataRequest
    ): Either<KunneIkkeSendeAvstemming, Unit> {
        val xml = xmlMapper.writeValueAsString(dataRequest)
        return mqPublisher.publish(xml).mapLeft { KunneIkkeSendeAvstemming }
    }

    fun publishStopp(
        stoppRequest: AvstemmingStoppRequest
    ): Either<KunneIkkeSendeAvstemming, Unit> {
        val xml = xmlMapper.writeValueAsString(stoppRequest)
        return mqPublisher.publish(xml).mapLeft { KunneIkkeSendeAvstemming }
    }

    fun lagAksjonsdata(aksjonType: AksjonType) = Aksjonsdata(
        aksjonType = aksjonType,
        kildeType = KildeType.AVLEVERT,
        avstemmingType = AvstemmingType.GRENSESNITTAVSTEMMING,
        mottakendeKomponentKode = "os",
        brukerId = "su"
    )
}
