package no.nav.su.se.bakover.database.utbetaling

import io.kotest.matchers.instanceOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import org.junit.jupiter.api.Test

internal class UtbetalingMapperTest {
    @Test
    fun `mapper til korrekte typer`() {
        UtbetalingMapper(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.now(),
            fnr = Fnr(fnr = "12345678910"),
            utbetalingslinjer = listOf(),
            type = Utbetaling.UtbetalingsType.NY,
            avstemmingsnøkkel = Avstemmingsnøkkel(),
            simulering = null,
            oppdragsmelding = null,
            kvittering = null,
            avstemmingId = null,
            oppdragId = UUID30.randomUUID(),
            behandler = NavIdentBruker.Saksbehandler("Z123")
        ).map() shouldBe instanceOf(Utbetaling.UtbetalingForSimulering::class)

        UtbetalingMapper(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.now(),
            fnr = Fnr(fnr = "12345678910"),
            utbetalingslinjer = listOf(),
            type = Utbetaling.UtbetalingsType.NY,
            avstemmingsnøkkel = Avstemmingsnøkkel(),
            simulering = Simulering(
                gjelderId = Fnr(fnr = "12345678910"),
                gjelderNavn = "navn",
                datoBeregnet = idag(),
                nettoBeløp = 0,
                periodeList = listOf()
            ),
            oppdragsmelding = null,
            kvittering = null,
            avstemmingId = null,
            oppdragId = UUID30.randomUUID(),
            behandler = NavIdentBruker.Saksbehandler("Z123")
        ).map() shouldBe instanceOf(Utbetaling.SimulertUtbetaling::class)

        UtbetalingMapper(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.now(),
            fnr = Fnr(fnr = "12345678910"),
            utbetalingslinjer = listOf(),
            type = Utbetaling.UtbetalingsType.NY,
            avstemmingsnøkkel = Avstemmingsnøkkel(),
            simulering = Simulering(
                gjelderId = Fnr(fnr = "12345678910"),
                gjelderNavn = "navn",
                datoBeregnet = idag(),
                nettoBeløp = 0,
                periodeList = listOf()
            ),
            oppdragsmelding = Oppdragsmelding(
                originalMelding = "",
                avstemmingsnøkkel = Avstemmingsnøkkel(
                    opprettet = Tidspunkt.now()
                )
            ),
            kvittering = null,
            avstemmingId = null,
            oppdragId = UUID30.randomUUID(),
            behandler = NavIdentBruker.Saksbehandler("Z123")
        ).map() shouldBe instanceOf(Utbetaling.OversendtUtbetaling::class)

        UtbetalingMapper(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.now(),
            fnr = Fnr(fnr = "12345678910"),
            utbetalingslinjer = listOf(),
            type = Utbetaling.UtbetalingsType.NY,
            avstemmingsnøkkel = Avstemmingsnøkkel(),
            simulering = Simulering(
                gjelderId = Fnr(fnr = "12345678910"),
                gjelderNavn = "navn",
                datoBeregnet = idag(),
                nettoBeløp = 0,
                periodeList = listOf()
            ),
            oppdragsmelding = Oppdragsmelding(
                originalMelding = "",
                avstemmingsnøkkel = Avstemmingsnøkkel(
                    opprettet = Tidspunkt.now()
                )
            ),
            kvittering = Kvittering(
                utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
                originalKvittering = "",
                mottattTidspunkt = Tidspunkt.now()

            ),
            avstemmingId = null,
            oppdragId = UUID30.randomUUID(),
            behandler = NavIdentBruker.Saksbehandler("Z123")
        ).map() shouldBe instanceOf(Utbetaling.KvittertUtbetaling::class)

        UtbetalingMapper(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.now(),
            fnr = Fnr(fnr = "12345678910"),
            utbetalingslinjer = listOf(),
            type = Utbetaling.UtbetalingsType.NY,
            avstemmingsnøkkel = Avstemmingsnøkkel(),
            simulering = Simulering(
                gjelderId = Fnr(fnr = "12345678910"),
                gjelderNavn = "navn",
                datoBeregnet = idag(),
                nettoBeløp = 0,
                periodeList = listOf()
            ),
            oppdragsmelding = Oppdragsmelding(
                originalMelding = "",
                avstemmingsnøkkel = Avstemmingsnøkkel(
                    opprettet = Tidspunkt.now()
                )
            ),
            kvittering = Kvittering(
                utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
                originalKvittering = "",
                mottattTidspunkt = Tidspunkt.now()

            ),
            avstemmingId = UUID30.randomUUID(),
            oppdragId = UUID30.randomUUID(),
            behandler = NavIdentBruker.Saksbehandler("Z123")
        ).map() shouldBe instanceOf(Utbetaling.AvstemtUtbetaling::class)
    }
}
