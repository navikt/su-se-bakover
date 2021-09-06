package no.nav.su.se.bakover.domain.oppdrag.avstemming

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.endOfDay
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.plus
import no.nav.su.se.bakover.test.saksnummer
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class KonsistensavstemmingTest {

    private val førsteKlokke = fixedClock
    private val andreKlokke = førsteKlokke.plus(50, ChronoUnit.DAYS)

    @Test
    fun `håndterer tilfeller hvor det ikke eksisterer løpende utbetalinger`() {
        Avstemming.Konsistensavstemming.Ny(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.now(),
            løpendeFraOgMed = 1.januar(2021).startOfDay(),
            opprettetTilOgMed = 31.desember(2021).endOfDay(),
            utbetalinger = emptyList(),
            avstemmingXmlRequest = "",
        ).løpendeUtbetalinger shouldBe emptyList()
    }

    @Test
    fun `en sak med en enkelt utbetaling`() {
        val første = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(førsteKlokke),
            utbetalingsLinjer = nonEmptyListOf(
                createUtbetalingslinje(
                    opprettet = Tidspunkt.now(førsteKlokke),
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.desember(2021),
                    beløp = 15000,
                    forrigeUtbetalingslinjeId = null,
                ),
            ),
        )

        Avstemming.Konsistensavstemming.Ny(
            id = UUID30.randomUUID(),
            opprettet = fixedTidspunkt,
            løpendeFraOgMed = 1.januar(2021).startOfDay(),
            opprettetTilOgMed = Tidspunkt.now(andreKlokke),
            utbetalinger = listOf(første),
            avstemmingXmlRequest = "",
        ).løpendeUtbetalinger shouldBe listOf(
            OppdragForKonsistensavstemming(
                id = første.id,
                opprettet = første.opprettet,
                sakId = første.sakId,
                saksnummer = første.saksnummer,
                fnr = første.fnr,
                utbetalingslinjer = listOf(
                    første.utbetalingslinjer[0],
                ),
            ),
        )
    }

    @Test
    fun `en sak med to utbetalinger`() {
        val første = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(førsteKlokke),
            utbetalingsLinjer = nonEmptyListOf(
                createUtbetalingslinje(
                    opprettet = Tidspunkt.now(førsteKlokke),
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.desember(2021),
                    beløp = 15000,
                    forrigeUtbetalingslinjeId = null,
                ),
            ),
        )

        val andre = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(andreKlokke),
            utbetalingsLinjer = nonEmptyListOf(
                createUtbetalingslinje(
                    opprettet = Tidspunkt.now(andreKlokke),
                    fraOgMed = 1.mai(2021),
                    tilOgMed = 31.desember(2021),
                    beløp = 20000,
                    forrigeUtbetalingslinjeId = null,
                ),
            ),
        )

        Avstemming.Konsistensavstemming.Ny(
            id = UUID30.randomUUID(),
            opprettet = fixedTidspunkt,
            løpendeFraOgMed = 1.januar(2021).startOfDay(),
            opprettetTilOgMed = Tidspunkt.now(andreKlokke),
            utbetalinger = listOf(første, andre),
            avstemmingXmlRequest = "",
        ).løpendeUtbetalinger shouldBe listOf(
            OppdragForKonsistensavstemming(
                id = første.id,
                opprettet = første.opprettet,
                sakId = første.sakId,
                saksnummer = første.saksnummer,
                fnr = første.fnr,
                utbetalingslinjer = listOf(
                    første.utbetalingslinjer[0],
                    andre.utbetalingslinjer[0],
                ),
            ),
        )
    }

    @Test
    fun `flere saker med flere utbetalinger`() {
        val s1u1 = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(førsteKlokke),
            utbetalingsLinjer = nonEmptyListOf(
                createUtbetalingslinje(
                    opprettet = Tidspunkt.now(førsteKlokke),
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.desember(2021),
                    beløp = 15000,
                    forrigeUtbetalingslinjeId = null,
                ),
            ),
        )

        val s1u2 = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(andreKlokke),
            utbetalingsLinjer = nonEmptyListOf(
                createUtbetalingslinje(
                    opprettet = Tidspunkt.now(andreKlokke),
                    fraOgMed = 1.mai(2021),
                    tilOgMed = 31.juli(2021),
                    beløp = 20000,
                    forrigeUtbetalingslinjeId = null,
                ),
                createUtbetalingslinje(
                    opprettet = Tidspunkt.now(andreKlokke),
                    fraOgMed = 1.september(2021),
                    tilOgMed = 31.desember(2021),
                    beløp = 20000,
                    forrigeUtbetalingslinjeId = null,
                ),
            ),
        )

        val fnr2 = Fnr.generer()
        val saksnummer2 = Saksnummer(9999)
        val s2u1 = createUtbetaling(
            fnr = fnr2,
            saksnummer = saksnummer2,
            opprettet = Tidspunkt.now(førsteKlokke),
            utbetalingsLinjer = nonEmptyListOf(
                createUtbetalingslinje(
                    opprettet = Tidspunkt.now(førsteKlokke),
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.juli(2021),
                    beløp = 15000,
                    forrigeUtbetalingslinjeId = null,
                ),
                createUtbetalingslinje(
                    opprettet = Tidspunkt.now(førsteKlokke),
                    fraOgMed = 1.september(2021),
                    tilOgMed = 31.desember(2021),
                    beløp = 18000,
                    forrigeUtbetalingslinjeId = null,
                ),
            ),
        )

        val s2u2 = createUtbetaling(
            fnr = fnr2,
            saksnummer = saksnummer2,
            opprettet = Tidspunkt.now(andreKlokke),
            utbetalingsLinjer = nonEmptyListOf(
                createUtbetalingslinje(
                    opprettet = Tidspunkt.now(andreKlokke),
                    fraOgMed = 1.mai(2021),
                    tilOgMed = 31.desember(2021),
                    beløp = 20000,
                    forrigeUtbetalingslinjeId = null,
                ),
            ),
        )

        Avstemming.Konsistensavstemming.Ny(
            id = UUID30.randomUUID(),
            opprettet = fixedTidspunkt,
            løpendeFraOgMed = 1.januar(2021).startOfDay(),
            opprettetTilOgMed = Tidspunkt.now(andreKlokke),
            utbetalinger = listOf(s1u1, s1u2, s2u1, s2u2),
            avstemmingXmlRequest = "",
        ).løpendeUtbetalinger shouldBe listOf(
            OppdragForKonsistensavstemming(
                id = s1u1.id,
                opprettet = s1u1.opprettet,
                sakId = s1u1.sakId,
                saksnummer = s1u1.saksnummer,
                fnr = s1u1.fnr,
                utbetalingslinjer = listOf(
                    s1u1.utbetalingslinjer[0],
                    s1u2.utbetalingslinjer[0],
                    s1u2.utbetalingslinjer[1],
                ),
            ),
            OppdragForKonsistensavstemming(
                id = s2u1.id,
                opprettet = s2u1.opprettet,
                sakId = s2u1.sakId,
                saksnummer = s2u1.saksnummer,
                fnr = s2u1.fnr,
                utbetalingslinjer = listOf(
                    s2u1.utbetalingslinjer[0],
                    s2u2.utbetalingslinjer[0],
                ),
            ),
        )
    }

    @Test
    fun `flere saker med flere utbetalinger - justering av input-parametere påvirker resultatet`() {
        val s1u1 = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(førsteKlokke),
            utbetalingsLinjer = nonEmptyListOf(
                createUtbetalingslinje(
                    opprettet = Tidspunkt.now(førsteKlokke),
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.desember(2021),
                    beløp = 15000,
                    forrigeUtbetalingslinjeId = null,
                ),
            ),
        )

        val s1u2 = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(andreKlokke),
            utbetalingsLinjer = nonEmptyListOf(
                createUtbetalingslinje(
                    opprettet = Tidspunkt.now(andreKlokke),
                    fraOgMed = 1.mai(2021),
                    tilOgMed = 31.juli(2021),
                    beløp = 20000,
                    forrigeUtbetalingslinjeId = null,
                ),
                createUtbetalingslinje(
                    opprettet = Tidspunkt.now(andreKlokke),
                    fraOgMed = 1.september(2021),
                    tilOgMed = 31.desember(2021),
                    beløp = 20000,
                    forrigeUtbetalingslinjeId = null,
                ),
            ),
        )

        val fnr2 = Fnr.generer()
        val saksnummer2 = Saksnummer(9999)
        val s2u1 = createUtbetaling(
            fnr = fnr2,
            saksnummer = saksnummer2,
            opprettet = Tidspunkt.now(førsteKlokke),
            utbetalingsLinjer = nonEmptyListOf(
                createUtbetalingslinje(
                    opprettet = Tidspunkt.now(førsteKlokke),
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.juli(2021),
                    beløp = 15000,
                    forrigeUtbetalingslinjeId = null,
                ),
                createUtbetalingslinje(
                    opprettet = Tidspunkt.now(førsteKlokke),
                    fraOgMed = 1.september(2021),
                    tilOgMed = 31.desember(2021),
                    beløp = 18000,
                    forrigeUtbetalingslinjeId = null,
                ),
            ),
        )

        val s2u2 = createUtbetaling(
            fnr = fnr2,
            saksnummer = saksnummer2,
            opprettet = Tidspunkt.now(andreKlokke),
            utbetalingsLinjer = nonEmptyListOf(
                createUtbetalingslinje(
                    opprettet = Tidspunkt.now(andreKlokke),
                    fraOgMed = 1.mai(2021),
                    tilOgMed = 31.desember(2021),
                    beløp = 20000,
                    forrigeUtbetalingslinjeId = null,
                ),
            ),
        )

        Avstemming.Konsistensavstemming.Ny(
            id = UUID30.randomUUID(),
            opprettet = fixedTidspunkt,
            løpendeFraOgMed = 1.desember(2021).startOfDay(), // Dato litt lenger fram i tid
            opprettetTilOgMed = Tidspunkt.now(andreKlokke),
            utbetalinger = listOf(s1u1, s1u2, s2u1, s2u2),
            avstemmingXmlRequest = "",
        ).løpendeUtbetalinger shouldBe listOf(
            OppdragForKonsistensavstemming(
                id = s1u1.id,
                opprettet = s1u1.opprettet,
                sakId = s1u1.sakId,
                saksnummer = s1u1.saksnummer,
                fnr = s1u1.fnr,
                utbetalingslinjer = listOf(
                    s1u2.utbetalingslinjer[1],
                ),
            ),
            OppdragForKonsistensavstemming(
                id = s2u1.id,
                opprettet = s2u1.opprettet,
                sakId = s2u1.sakId,
                saksnummer = s2u1.saksnummer,
                fnr = s2u1.fnr,
                utbetalingslinjer = listOf(
                    s2u2.utbetalingslinjer[0],
                ),
            ),
        )

        Avstemming.Konsistensavstemming.Ny(
            id = UUID30.randomUUID(),
            opprettet = fixedTidspunkt,
            løpendeFraOgMed = 1.januar(2021).startOfDay(),
            opprettetTilOgMed = 1.januar(2000).endOfDay(), // Dato langt bak i tid
            utbetalinger = listOf(s1u1, s1u2, s2u1, s2u2),
            avstemmingXmlRequest = "",
        ).løpendeUtbetalinger shouldBe emptyList()

        Avstemming.Konsistensavstemming.Ny(
            id = UUID30.randomUUID(),
            opprettet = fixedTidspunkt,
            løpendeFraOgMed = 1.januar(2021).startOfDay(),
            opprettetTilOgMed = Tidspunkt.now(andreKlokke).minus(1, ChronoUnit.DAYS), // Dato forut for andre klokke
            utbetalinger = listOf(s1u1, s1u2, s2u1, s2u2),
            avstemmingXmlRequest = "",
        ).løpendeUtbetalinger shouldBe listOf(
            OppdragForKonsistensavstemming(
                id = s1u1.id,
                opprettet = s1u1.opprettet,
                sakId = s1u1.sakId,
                saksnummer = s1u1.saksnummer,
                fnr = s1u1.fnr,
                utbetalingslinjer = listOf(
                    s1u1.utbetalingslinjer[0],
                ),
            ),
            OppdragForKonsistensavstemming(
                id = s2u1.id,
                opprettet = s2u1.opprettet,
                sakId = s2u1.sakId,
                saksnummer = s2u1.saksnummer,
                fnr = s2u1.fnr,
                utbetalingslinjer = listOf(
                    s2u1.utbetalingslinjer[0],
                    s2u1.utbetalingslinjer[1],
                ),
            ),
        )
    }

    @Test
    fun `opphørte linjer framover i tid inkluderes`() {
        val første = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(førsteKlokke),
            utbetalingsLinjer = nonEmptyListOf(
                createUtbetalingslinje(
                    opprettet = Tidspunkt.now(førsteKlokke),
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.desember(2021),
                    beløp = 15000,
                    forrigeUtbetalingslinjeId = null,
                ),
            ),
        )

        val andre = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(andreKlokke),
            utbetalingsLinjer = nonEmptyListOf(
                Utbetalingslinje.Endring.Opphør(
                    utbetalingslinje = første.utbetalingslinjer[0],
                    virkningstidspunkt = 1.april(2021),
                    clock = andreKlokke,
                ),
            ),
        )

        Avstemming.Konsistensavstemming.Ny(
            id = UUID30.randomUUID(),
            opprettet = fixedTidspunkt,
            løpendeFraOgMed = 1.januar(2021).startOfDay(),
            opprettetTilOgMed = 31.desember(2021).endOfDay(),
            utbetalinger = listOf(første, andre),
            avstemmingXmlRequest = "",
        ).løpendeUtbetalinger shouldBe listOf(
            OppdragForKonsistensavstemming(
                id = første.id,
                opprettet = første.opprettet,
                sakId = første.sakId,
                saksnummer = første.saksnummer,
                fnr = første.fnr,
                utbetalingslinjer = listOf(
                    første.utbetalingslinjer[0],
                    andre.utbetalingslinjer[0],
                ),
            ),
        )
    }

    @Test
    fun `opphørte ytelser plukkes ikke ut til avstemming`() {
        val første = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(førsteKlokke),
            utbetalingsLinjer = nonEmptyListOf(
                createUtbetalingslinje(
                    opprettet = Tidspunkt.now(førsteKlokke),
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.desember(2021),
                    beløp = 15000,
                    forrigeUtbetalingslinjeId = null,
                ),
            ),
        )

        val andre = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(andreKlokke),
            utbetalingsLinjer = nonEmptyListOf(
                Utbetalingslinje.Endring.Opphør(
                    utbetalingslinje = første.utbetalingslinjer[0],
                    virkningstidspunkt = 1.april(2021),
                    clock = andreKlokke,
                ),
            ),
        )

        Avstemming.Konsistensavstemming.Ny(
            id = UUID30.randomUUID(),
            opprettet = fixedTidspunkt,
            løpendeFraOgMed = 1.april(2021).startOfDay(),
            opprettetTilOgMed = Tidspunkt.now(andreKlokke),
            utbetalinger = listOf(første, andre),
            avstemmingXmlRequest = "",
        ).løpendeUtbetalinger shouldBe emptyList()
    }

    private fun createUtbetaling(
        id: UUID30 = UUID30.randomUUID(),
        fnr: Fnr,
        saksnummer: Saksnummer,
        opprettet: Tidspunkt = Tidspunkt.now(),
        utbetalingsLinjer: NonEmptyList<Utbetalingslinje>,
    ) = Utbetaling.OversendtUtbetaling.UtenKvittering(
        id = id,
        sakId = UUID.randomUUID(),
        saksnummer = saksnummer,
        utbetalingslinjer = utbetalingsLinjer,
        fnr = fnr,
        opprettet = opprettet,
        type = Utbetaling.UtbetalingsType.NY,
        behandler = NavIdentBruker.Saksbehandler("Z123"),
        avstemmingsnøkkel = Avstemmingsnøkkel(),
        simulering = Simulering(
            gjelderId = fnr,
            gjelderNavn = "navn",
            datoBeregnet = idag(),
            nettoBeløp = 0,
            periodeList = listOf(),
        ),
        utbetalingsrequest = Utbetalingsrequest(value = ""),

    )

    private fun createUtbetalingslinje(
        opprettet: Tidspunkt,
        fraOgMed: LocalDate = 1.januar(2020),
        tilOgMed: LocalDate = 31.desember(2020),
        beløp: Int = 500,
        forrigeUtbetalingslinjeId: UUID30? = null,
    ) = Utbetalingslinje.Ny(
        opprettet = opprettet,
        fraOgMed = fraOgMed,
        tilOgMed = tilOgMed,
        beløp = beløp,
        forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
    )
}
