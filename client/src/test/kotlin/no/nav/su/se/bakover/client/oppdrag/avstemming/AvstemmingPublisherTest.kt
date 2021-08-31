package no.nav.su.se.bakover.client.oppdrag.avstemming

import arrow.core.Either
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.oppdrag.MqPublisher
import no.nav.su.se.bakover.client.oppdrag.XmlMapper
import no.nav.su.se.bakover.client.oppdrag.toAvstemmingsdatoFormat
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class AvstemmingPublisherTest {

    @Test
    fun `publish avstemming faktisk sender melding på mq`() {
        val client = MqPublisherMock(Unit.right())
        val res = AvstemmingMqPublisher(mqPublisher = client)
            .publish(avstemming)

        client.count shouldBe 1
        client.publishedMessages.size shouldBe 3
        client.publishedMessages[0] shouldBe XmlMapper.writeValueAsString(
            AvstemmingStartRequest(
                aksjon = Aksjonsdata.Grensesnittsavstemming(
                    aksjonType = Aksjonsdata.AksjonType.START,
                    avleverendeAvstemmingId = avstemming.id.toString(),
                    nokkelFom = Avstemmingsnøkkel(avstemming.fraOgMed).toString(),
                    nokkelTom = Avstemmingsnøkkel(avstemming.tilOgMed).toString(),
                ),
            ),
        )
        client.publishedMessages[1] shouldBe XmlMapper.writeValueAsString(
            GrensesnittsavstemmingRequest(
                aksjon = Aksjonsdata.Grensesnittsavstemming(
                    aksjonType = Aksjonsdata.AksjonType.DATA,
                    avleverendeAvstemmingId = avstemming.id.toString(),
                    nokkelFom = Avstemmingsnøkkel(avstemming.fraOgMed).toString(),
                    nokkelTom = Avstemmingsnøkkel(avstemming.tilOgMed).toString(),
                ),
                total = Totaldata(
                    totalAntall = 1,
                    totalBelop = BigDecimal.valueOf(5000),
                    fortegn = Fortegn.TILLEGG,
                ),
                periode = Periodedata(
                    datoAvstemtFom = avstemming.fraOgMed.toAvstemmingsdatoFormat(),
                    datoAvstemtTom = avstemming.tilOgMed.toAvstemmingsdatoFormat(),
                ),
                grunnlag = GrensesnittsavstemmingRequest.Grunnlagdata(
                    godkjentAntall = 1,
                    godkjentBelop = BigDecimal.valueOf(5000),
                    godkjentFortegn = Fortegn.TILLEGG,
                    varselAntall = 0,
                    varselBelop = BigDecimal.ZERO,
                    varselFortegn = Fortegn.TILLEGG,
                    avvistAntall = 0,
                    avvistBelop = BigDecimal.ZERO,
                    avvistFortegn = Fortegn.TILLEGG,
                    manglerAntall = 0,
                    manglerBelop = BigDecimal.ZERO,
                    manglerFortegn = Fortegn.TILLEGG,
                ),
                detalj = listOf(),
            ),
        )
        client.publishedMessages[2] shouldBe XmlMapper.writeValueAsString(
            AvstemmingStoppRequest(
                aksjon = Aksjonsdata.Grensesnittsavstemming(
                    aksjonType = Aksjonsdata.AksjonType.AVSLUTT,
                    avleverendeAvstemmingId = avstemming.id.toString(),
                    nokkelFom = Avstemmingsnøkkel(avstemming.fraOgMed).toString(),
                    nokkelTom = Avstemmingsnøkkel(avstemming.tilOgMed).toString(),
                ),
            ),
        )

        res.isRight() shouldBe true
    }

    @Test
    fun `publish avstemming feiler`() {
        val client = MqPublisherMock(MqPublisher.CouldNotPublish.left())
        val res = AvstemmingMqPublisher(mqPublisher = client)
            .publish(avstemming)

        res shouldBe AvstemmingPublisher.KunneIkkeSendeAvstemming.left()
    }

    private val avstemming = Avstemming.Grensesnittavstemming(
        fraOgMed = 1.januar(2020).startOfDay(),
        tilOgMed = 2.januar(2020).startOfDay(),
        utbetalinger = listOf(
            Utbetaling.OversendtUtbetaling.MedKvittering(
                saksnummer = saksnummer,
                sakId = sakId,
                utbetalingslinjer = nonEmptyListOf(
                    Utbetalingslinje.Ny(
                        id = UUID30.randomUUID(),
                        opprettet = Tidspunkt.EPOCH,
                        fraOgMed = 1.januar(2021),
                        tilOgMed = 31.januar(2021),
                        forrigeUtbetalingslinjeId = null,
                        beløp = 5000,
                    ),
                ),
                fnr = Fnr("12345678910"),
                simulering = Simulering(
                    gjelderId = Fnr("12345678910"),
                    gjelderNavn = "",
                    datoBeregnet = idag(),
                    nettoBeløp = 5000,
                    periodeList = listOf(),
                ),
                utbetalingsrequest = Utbetalingsrequest(
                    value = "",
                ),
                kvittering = Kvittering(
                    utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
                    originalKvittering = "hallo",
                    mottattTidspunkt = Tidspunkt.now(),
                ),
                type = Utbetaling.UtbetalingsType.NY,
                behandler = NavIdentBruker.Saksbehandler("Z123"),
            ),
        ),
    )

    class MqPublisherMock(val response: Either<MqPublisher.CouldNotPublish, Unit>) : MqPublisher {
        var count = 0
        var publishedMessages = mutableListOf<String>()

        override fun publish(vararg messages: String): Either<MqPublisher.CouldNotPublish, Unit> {
            ++count
            this.publishedMessages.addAll(messages)
            return response
        }
    }
}
