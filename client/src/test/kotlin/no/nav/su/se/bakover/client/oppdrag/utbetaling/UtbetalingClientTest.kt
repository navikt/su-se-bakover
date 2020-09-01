package no.nav.su.se.bakover.client.oppdrag.utbetaling

import arrow.core.Either
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.oppdrag.MqClient
import no.nav.su.se.bakover.client.oppdrag.MqClient.CouldNotPublish
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

internal class UtbetalingClientTest {

    @Test
    fun `Teste Client`() {
        val mqClient = object : MqClient {
            var count = 0
            var message: String? = null
            override fun publish(message: String): Either<CouldNotPublish, Unit> {
                ++count
                this.message = message
                return Unit.right()
            }
        }
        val clock = Clock.fixed(Instant.parse("1970-01-01T00:00:00.000+01:00"), ZoneOffset.UTC)
        val client = UtbetalingClient(clock, mqClient)

        val utbetaling = Utbetaling(
            behandlingId = UUID.randomUUID(),
            utbetalingslinjer = listOf(
                Utbetalingslinje(
                    fom = 1.januar(2020),
                    tom = 14.januar(2020),
                    beløp = 10,
                    forrigeUtbetalingslinjeId = null
                )
            ),
            oppdragId = UUID30.randomUUID()
        )

        client.sendUtbetaling(utbetaling, "Saksbehandler") shouldBe Unit.right()
        mqClient.count shouldBe 1
        val expected =
            """
            <?xml version='1.0' encoding='UTF-8'?>
            <Oppdrag>
              <oppdrag-110>
                <kodeAksjon>1</kodeAksjon>
                <kodeEndring>NY</kodeEndring>
                <kodeFagomraade>SUUFORE</kodeFagomraade>
                <fagsystemId>${utbetaling.oppdragId}</fagsystemId>
                <utbetFrekvens>MND</utbetFrekvens>
                <oppdragGjelderId>Saksbehandler</oppdragGjelderId>
                <datoOppdragGjelderFom>1970-01-01</datoOppdragGjelderFom>
                <saksbehId>SU</saksbehId>
                <avstemming-115>
                  <kodeKomponent>SUUFORE</kodeKomponent>
                  <nokkelAvstemming>TODO</nokkelAvstemming>
                  <tidspktMelding>1970-01-01-00.00.00.000000</tidspktMelding>
                </avstemming-115>
                <oppdrags-enhet-120>
                  <typeEnhet>BOS</typeEnhet>
                  <enhet>8020</enhet>
                  <datoEnhetFom>1970-01-01</datoEnhetFom>
                </oppdrags-enhet-120>
                <oppdrags-linje-150>
                  <kodeEndringLinje>NY</kodeEndringLinje>
                  <delytelseId>${utbetaling.utbetalingslinjer[0].id}</delytelseId>
                  <kodeKlassifik>SUUFORE</kodeKlassifik>
                  <datoVedtakFom>2020-01-01</datoVedtakFom>
                  <datoVedtakTom>2020-01-14</datoVedtakTom>
                  <sats>10</sats>
                  <fradragTillegg>T</fradragTillegg>
                  <typeSats>MND</typeSats>
                  <brukKjoreplan>N</brukKjoreplan>
                  <saksbehId>SU</saksbehId>
                  <utbetalesTilId>Saksbehandler</utbetalesTilId>
                </oppdrags-linje-150>
              </oppdrag-110>
            </Oppdrag>
            """.trimIndent()
        mqClient.message?.trimIndent() shouldBe expected
    }
}
