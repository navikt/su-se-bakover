package no.nav.su.se.bakover.client.oppdrag.avstemming

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.client.oppdrag.MqPublisher
import no.nav.su.se.bakover.client.oppdrag.toAvstemmingsdatoFormat
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.endOfDay
import no.nav.su.se.bakover.common.extensions.idag
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.startOfDay
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.xml.xmlMapper
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.AvstemmingPublisher
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.utbetaling.oversendtUtbetalingUtenKvittering
import no.nav.su.se.bakover.test.utbetaling.utbetalingslinjeNy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import økonomi.domain.Fagområde
import økonomi.domain.avstemming.Avstemmingsnøkkel
import økonomi.domain.kvittering.Kvittering
import økonomi.domain.simulering.Simulering
import økonomi.domain.simulering.SimulertMåned
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.Utbetalingsrequest
import java.math.BigDecimal

class AvstemmingPublisherTest {

    @Test
    fun `publish avstemming faktisk sender melding på mq`() {
        val client = MqPublisherMock(Unit.right())
        val res = AvstemmingMqPublisher(mqPublisher = client)
            .publish(grensesnittavstemming)

        client.count shouldBe 1
        client.publishedMessages.size shouldBe 3
        client.publishedMessages[0] shouldBe xmlMapper.writeValueAsString(
            AvstemmingStartRequest(
                aksjon = Grensesnittsavstemming(
                    aksjonType = AksjonType.START,
                    avleverendeAvstemmingId = grensesnittavstemming.id.toString(),
                    nokkelFom = Avstemmingsnøkkel(grensesnittavstemming.fraOgMed).toString(),
                    nokkelTom = Avstemmingsnøkkel(grensesnittavstemming.tilOgMed).toString(),
                    underkomponentKode = "SUUFORE",
                ),
            ),
        )
        client.publishedMessages[1] shouldBe xmlMapper.writeValueAsString(
            GrensesnittsavstemmingData(
                aksjon = Grensesnittsavstemming(
                    aksjonType = AksjonType.DATA,
                    avleverendeAvstemmingId = grensesnittavstemming.id.toString(),
                    nokkelFom = Avstemmingsnøkkel(grensesnittavstemming.fraOgMed).toString(),
                    nokkelTom = Avstemmingsnøkkel(grensesnittavstemming.tilOgMed).toString(),
                    underkomponentKode = "SUUFORE",
                ),
                total = Totaldata(
                    totalAntall = 1,
                    totalBelop = BigDecimal.valueOf(5000),
                    fortegn = Fortegn.TILLEGG,
                ),
                periode = Periodedata(
                    datoAvstemtFom = grensesnittavstemming.fraOgMed.toAvstemmingsdatoFormat(),
                    datoAvstemtTom = grensesnittavstemming.tilOgMed.toAvstemmingsdatoFormat(),
                ),
                grunnlag = GrensesnittsavstemmingData.Grunnlagdata(
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
        client.publishedMessages[2] shouldBe xmlMapper.writeValueAsString(
            AvstemmingStoppRequest(
                aksjon = Grensesnittsavstemming(
                    aksjonType = AksjonType.AVSLUTT,
                    avleverendeAvstemmingId = grensesnittavstemming.id.toString(),
                    nokkelFom = Avstemmingsnøkkel(grensesnittavstemming.fraOgMed).toString(),
                    nokkelTom = Avstemmingsnøkkel(grensesnittavstemming.tilOgMed).toString(),
                    underkomponentKode = "SUUFORE",
                ),
            ),
        )

        res.shouldBeRight()
    }

    @Test
    fun `publish avstemming feiler`() {
        val client = MqPublisherMock(MqPublisher.CouldNotPublish.left())
        val res = AvstemmingMqPublisher(mqPublisher = client)
            .publish(grensesnittavstemming)

        res shouldBe AvstemmingPublisher.KunneIkkeSendeAvstemming.left()
    }

    @Test
    fun `publisering av konsistensavstemming`() {
        val client = MqPublisherMock(Unit.right())

        val res = AvstemmingMqPublisher(mqPublisher = client)
            .publish(konsistensavstemming)
            .getOrElse { fail { "Burde gått fint" } }

        res.avstemmingXmlRequest shouldNotBe null

        client.publishedMessages.count() shouldBe 5

        KonsistensavstemmingRequestBuilder(konsistensavstemming).let {
            client.publishedMessages[0] shouldBe it.startXml()
            client.publishedMessages[1] shouldBe it.dataXml()[0]
            client.publishedMessages[2] shouldBe it.dataXml()[1]
            client.publishedMessages[3] shouldBe it.totaldataXml()
            client.publishedMessages[4] shouldBe it.avsluttXml()
        }
    }

    private val grensesnittavstemming = Avstemming.Grensesnittavstemming(
        opprettet = fixedTidspunkt,
        fraOgMed = 1.januar(2020).startOfDay(),
        tilOgMed = 2.januar(2020).startOfDay(),
        utbetalinger = listOf(
            Utbetaling.UtbetalingForSimulering(
                opprettet = fixedTidspunkt,
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = Fnr("12345678910"),
                utbetalingslinjer = nonEmptyListOf(
                    utbetalingslinjeNy(
                        periode = januar(2021),
                        beløp = 5000,
                    ),
                ),
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                avstemmingsnøkkel = Avstemmingsnøkkel(fixedTidspunkt),
                sakstype = Sakstype.UFØRE,
            ).toSimulertUtbetaling(
                simulering = Simulering(
                    gjelderId = Fnr("12345678910"),
                    gjelderNavn = "",
                    datoBeregnet = idag(fixedClock),
                    nettoBeløp = 5000,
                    måneder = listOf(SimulertMåned(måned = januar(2021))),
                    rawResponse = "AvstemmingPublisherTest baserer seg ikke på rå XML",
                ),
            ).toOversendtUtbetaling(
                oppdragsmelding = Utbetalingsrequest(value = ""),
            ).toKvittertUtbetaling(
                kvittering = Kvittering(
                    utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
                    originalKvittering = "hallo",
                    mottattTidspunkt = fixedTidspunkt,
                ),
            ),
        ),
        fagområde = Fagområde.SUUFORE,
    )

    private val konsistensavstemming = Avstemming.Konsistensavstemming.Ny(
        id = UUID30.randomUUID(),
        opprettet = fixedTidspunkt,
        løpendeFraOgMed = 1.januar(2021).startOfDay(),
        opprettetTilOgMed = 31.desember(2021).endOfDay(),
        utbetalinger = listOf(
            oversendtUtbetalingUtenKvittering(
                fnr = Fnr("88888888888"),
                saksnummer = Saksnummer(8888),
            ),
            oversendtUtbetalingUtenKvittering(
                fnr = Fnr("99999999999"),
                saksnummer = Saksnummer(9999),
            ),
        ),
        avstemmingXmlRequest = null,
        fagområde = Fagområde.SUUFORE,
    )

    class MqPublisherMock(private val response: Either<MqPublisher.CouldNotPublish, Unit>) : MqPublisher {
        var count = 0
        var publishedMessages = mutableListOf<String>()

        override fun publish(vararg messages: String): Either<MqPublisher.CouldNotPublish, Unit> {
            ++count
            this.publishedMessages.addAll(messages)
            return response
        }
    }
}
