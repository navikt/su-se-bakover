package no.nav.su.se.bakover.database.utbetaling

import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.database.fixedTidspunkt
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import org.junit.jupiter.api.Test
import java.util.UUID

internal class UtbetalingMapperTest {

    @Test
    fun `mapper til korrekte typer`() {
        UtbetalingMapper(
            id = UUID30.randomUUID(),
            opprettet = fixedTidspunkt,
            sakId = UUID.randomUUID(),
            saksnummer = Saksnummer(2021),
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
            utbetalingsrequest = Utbetalingsrequest(
                value = ""
            ),
            kvittering = null,
            avstemmingId = null,
            behandler = NavIdentBruker.Saksbehandler("Z123")
        ).map().shouldBeInstanceOf<Utbetaling.OversendtUtbetaling.UtenKvittering>()

        UtbetalingMapper(
            id = UUID30.randomUUID(),
            opprettet = fixedTidspunkt,
            sakId = UUID.randomUUID(),
            saksnummer = Saksnummer(2021),
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
            utbetalingsrequest = Utbetalingsrequest(
                value = ""
            ),
            kvittering = Kvittering(
                utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
                originalKvittering = "",
                mottattTidspunkt = fixedTidspunkt

            ),
            avstemmingId = null,
            behandler = NavIdentBruker.Saksbehandler("Z123")
        ).map().shouldBeInstanceOf<Utbetaling.OversendtUtbetaling.MedKvittering>()
    }
}
