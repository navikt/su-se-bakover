package no.nav.su.se.bakover.database.utbetaling

import arrow.core.nonEmptyListOf
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.utbetalingslinje
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
            utbetalingslinjer = nonEmptyListOf(
                utbetalingslinje(
                    periode = januar(2021),
                    beløp = 0,
                ),
            ),
            avstemmingsnøkkel = Avstemmingsnøkkel(opprettet = fixedTidspunkt),
            simulering = Simulering(
                gjelderId = Fnr(fnr = "12345678910"),
                gjelderNavn = "navn",
                datoBeregnet = idag(fixedClock),
                nettoBeløp = 0,
                periodeList = listOf(
                    SimulertPeriode(
                        fraOgMed = januar(2021).fraOgMed,
                        tilOgMed = januar(2021).tilOgMed,
                        utbetaling = null,
                    ),
                ),
                rawResponse = "UtbetalingMapperTest baserer ikke denne på rå XML.",
            ),
            utbetalingsrequest = Utbetalingsrequest(
                value = "",
            ),
            kvittering = null,
            avstemmingId = null,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            sakstype = Sakstype.UFØRE,
        ).map().shouldBeInstanceOf<Utbetaling.OversendtUtbetaling.UtenKvittering>()

        UtbetalingMapper(
            id = UUID30.randomUUID(),
            opprettet = fixedTidspunkt,
            sakId = UUID.randomUUID(),
            saksnummer = Saksnummer(2021),
            fnr = Fnr(fnr = "12345678910"),
            utbetalingslinjer = nonEmptyListOf(
                utbetalingslinje(
                    periode = januar(2021),
                    beløp = 0,
                ),
            ),
            avstemmingsnøkkel = Avstemmingsnøkkel(opprettet = fixedTidspunkt),
            simulering = Simulering(
                gjelderId = Fnr(fnr = "12345678910"),
                gjelderNavn = "navn",
                datoBeregnet = idag(fixedClock),
                nettoBeløp = 0,
                periodeList = listOf(
                    SimulertPeriode(
                        fraOgMed = januar(2021).fraOgMed,
                        tilOgMed = januar(2021).tilOgMed,
                        utbetaling = null,
                    ),
                ),
                rawResponse = "UtbetalingsstrategiGjenopptaTest baserer ikke denne på rå XML.",
            ),
            utbetalingsrequest = Utbetalingsrequest(
                value = "",
            ),
            kvittering = Kvittering(
                utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
                originalKvittering = "",
                mottattTidspunkt = fixedTidspunkt,

            ),
            avstemmingId = null,
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            sakstype = Sakstype.UFØRE,
        ).map().shouldBeInstanceOf<Utbetaling.OversendtUtbetaling.MedKvittering>()
    }
}
