package no.nav.su.se.bakover.domain.oppdrag.avstemming

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.endOfDay
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
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
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class KonsistensavstemmingTest {

    private val førsteKlokke = Clock.fixed(6.september(2020).startOfDay().instant, ZoneOffset.UTC)
    private val andreKlokke = førsteKlokke.plus(1, ChronoUnit.DAYS)
    private val tredjeKlokke = andreKlokke.plus(1, ChronoUnit.DAYS)
    private val fjerdeKlokke = tredjeKlokke.plus(1, ChronoUnit.DAYS)
    private val femteKlokke = fjerdeKlokke.plus(1, ChronoUnit.DAYS)

    @Test
    fun `håndterer tilfeller hvor det ikke eksisterer løpende utbetalinger`() {
        Avstemming.Konsistensavstemming.Ny(
            id = UUID30.randomUUID(),
            opprettet = fixedTidspunkt,
            løpendeFraOgMed = 1.januar(2021).startOfDay(),
            opprettetTilOgMed = 31.desember(2021).endOfDay(),
            utbetalinger = emptyList(),
            avstemmingXmlRequest = "",
            fagområde = Fagområde.SUUFORE,
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
            fagområde = Fagområde.SUUFORE,
        ).løpendeUtbetalinger shouldBe listOf(
            OppdragForKonsistensavstemming(
                saksnummer = saksnummer,
                fagområde = Fagområde.SUUFORE,
                fnr = fnr,
                utbetalingslinjer = listOf(
                    første.utbetalingslinjer[0],
                ).toOppdragslinjeForKonsistensavstemming(defaultAttestant),
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
            behandler = NavIdentBruker.Attestant("første"),
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
            behandler = NavIdentBruker.Attestant("andre"),
        )

        Avstemming.Konsistensavstemming.Ny(
            id = UUID30.randomUUID(),
            opprettet = fixedTidspunkt,
            løpendeFraOgMed = 1.januar(2021).startOfDay(),
            opprettetTilOgMed = Tidspunkt.now(andreKlokke),
            utbetalinger = listOf(første, andre),
            avstemmingXmlRequest = "",
            fagområde = Fagområde.SUUFORE,
        ).løpendeUtbetalinger shouldBe listOf(
            OppdragForKonsistensavstemming(
                saksnummer = saksnummer,
                fagområde = Fagområde.SUUFORE,
                fnr = fnr,
                utbetalingslinjer = listOf(
                    første.utbetalingslinjer[0].toOppdragslinjeForKonsistensavstemming(
                        nonEmptyListOf(
                            NavIdentBruker.Attestant(
                                "første",
                            ),
                        ),
                    ),
                    andre.utbetalingslinjer[0].toOppdragslinjeForKonsistensavstemming(
                        nonEmptyListOf(
                            NavIdentBruker.Attestant(
                                "andre",
                            ),
                        ),
                    ),
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
            fagområde = Fagområde.SUUFORE,
        ).løpendeUtbetalinger shouldBe listOf(
            OppdragForKonsistensavstemming(
                saksnummer = saksnummer,
                fagområde = Fagområde.SUUFORE,
                fnr = fnr,
                utbetalingslinjer = listOf(
                    s1u1.utbetalingslinjer[0],
                    s1u2.utbetalingslinjer[0],
                    s1u2.utbetalingslinjer[1],
                ).toOppdragslinjeForKonsistensavstemming(defaultAttestant),
            ),
            OppdragForKonsistensavstemming(
                saksnummer = saksnummer2,
                fagområde = Fagområde.SUUFORE,
                fnr = fnr2,
                utbetalingslinjer = listOf(
                    s2u1.utbetalingslinjer[0],
                    s2u2.utbetalingslinjer[0],
                ).toOppdragslinjeForKonsistensavstemming(defaultAttestant),
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
            fagområde = Fagområde.SUUFORE,
        ).løpendeUtbetalinger shouldBe listOf(
            OppdragForKonsistensavstemming(
                saksnummer = saksnummer,
                fagområde = Fagområde.SUUFORE,
                fnr = fnr,
                utbetalingslinjer = listOf(
                    s1u2.utbetalingslinjer[1],
                ).toOppdragslinjeForKonsistensavstemming(defaultAttestant),
            ),
            OppdragForKonsistensavstemming(
                saksnummer = saksnummer2,
                fagområde = Fagområde.SUUFORE,
                fnr = fnr2,
                utbetalingslinjer = listOf(
                    s2u2.utbetalingslinjer[0],
                ).toOppdragslinjeForKonsistensavstemming(defaultAttestant),
            ),
        )

        Avstemming.Konsistensavstemming.Ny(
            id = UUID30.randomUUID(),
            opprettet = fixedTidspunkt,
            løpendeFraOgMed = 1.januar(2021).startOfDay(),
            opprettetTilOgMed = 1.januar(2000).endOfDay(), // Dato langt bak i tid
            utbetalinger = listOf(s1u1, s1u2, s2u1, s2u2),
            avstemmingXmlRequest = "",
            fagområde = Fagområde.SUUFORE,
        ).løpendeUtbetalinger shouldBe emptyList()

        Avstemming.Konsistensavstemming.Ny(
            id = UUID30.randomUUID(),
            opprettet = fixedTidspunkt,
            løpendeFraOgMed = 1.januar(2021).startOfDay(),
            opprettetTilOgMed = Tidspunkt.now(andreKlokke).minus(1, ChronoUnit.DAYS), // Dato forut for andre klokke
            utbetalinger = listOf(s1u1, s1u2, s2u1, s2u2),
            avstemmingXmlRequest = "",
            fagområde = Fagområde.SUUFORE,
        ).løpendeUtbetalinger shouldBe listOf(
            OppdragForKonsistensavstemming(
                saksnummer = saksnummer,
                fagområde = Fagområde.SUUFORE,
                fnr = fnr,
                utbetalingslinjer = listOf(
                    s1u1.utbetalingslinjer[0],
                ).toOppdragslinjeForKonsistensavstemming(defaultAttestant),
            ),
            OppdragForKonsistensavstemming(
                saksnummer = saksnummer2,
                fagområde = Fagområde.SUUFORE,
                fnr = fnr2,
                utbetalingslinjer = listOf(
                    s2u1.utbetalingslinjer[0],
                    s2u1.utbetalingslinjer[1],
                ).toOppdragslinjeForKonsistensavstemming(defaultAttestant),
            ),
        )
    }

    @Test
    fun `opphørte linjer framover i tid inkluderes - tar kun med nye linjer, selv om opphør har samme id`() {
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
                    virkningsperiode = Periode.create(1.april(2021), første.utbetalingslinjer[0].tilOgMed),
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
            fagområde = Fagområde.SUUFORE,
        ).løpendeUtbetalinger shouldBe listOf(
            OppdragForKonsistensavstemming(
                saksnummer = saksnummer,
                fagområde = Fagområde.SUUFORE,
                fnr = fnr,
                utbetalingslinjer = listOf(
                    første.utbetalingslinjer[0].toOppdragslinjeForKonsistensavstemming(
                        nonEmptyListOf(
                            defaultAttestant,
                            defaultAttestant,
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `opphør i utbetalinger, utbetalinger på begge sider av opphør inkluderes`() {
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
                    virkningsperiode = Periode.create(1.april(2021), første.utbetalingslinjer[0].tilOgMed),
                    clock = andreKlokke,
                ),
            ),
        )

        val tredje = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(tredjeKlokke),
            utbetalingsLinjer = nonEmptyListOf(
                createUtbetalingslinje(
                    opprettet = Tidspunkt.now(tredjeKlokke),
                    fraOgMed = 1.september(2021),
                    tilOgMed = 31.desember(2021),
                    beløp = 5000,
                    forrigeUtbetalingslinjeId = andre.sisteUtbetalingslinje().id,
                ),
            ),
        )

        Avstemming.Konsistensavstemming.Ny(
            id = UUID30.randomUUID(),
            opprettet = fixedTidspunkt,
            løpendeFraOgMed = 1.mars(2021).startOfDay(),
            opprettetTilOgMed = 31.desember(2021).endOfDay(),
            utbetalinger = listOf(første, andre, tredje),
            avstemmingXmlRequest = "",
            fagområde = Fagområde.SUUFORE,
        ).løpendeUtbetalinger shouldBe listOf(
            OppdragForKonsistensavstemming(
                saksnummer = saksnummer,
                fagområde = Fagområde.SUUFORE,
                fnr = fnr,
                utbetalingslinjer = listOf(
                    første.utbetalingslinjer[0].toOppdragslinjeForKonsistensavstemming(
                        nonEmptyListOf(
                            defaultAttestant,
                            defaultAttestant,
                        ),
                    ),
                    tredje.utbetalingslinjer[0].toOppdragslinjeForKonsistensavstemming(nonEmptyListOf(defaultAttestant)),
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
                    virkningsperiode = Periode.create(1.april(2021), første.utbetalingslinjer[0].tilOgMed),
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
            fagområde = Fagområde.SUUFORE,
        ).løpendeUtbetalinger shouldBe emptyList()
    }

    @Test
    fun `ny, stans og reaktivering - tar kun med seg nye linjer selv om stans og reaktivering har samme id`() {
        val ny1 = createUtbetalingslinje(
            opprettet = Tidspunkt.now(førsteKlokke),
            fraOgMed = 1.januar(2021),
            tilOgMed = 30.april(2021),
            beløp = 10000,
            forrigeUtbetalingslinjeId = null,
        )
        val ny2 = createUtbetalingslinje(
            opprettet = Tidspunkt.now(førsteKlokke),
            fraOgMed = 1.mai(2021),
            tilOgMed = 31.desember(2021),
            beløp = 15000,
            forrigeUtbetalingslinjeId = ny1.id,
        )
        val første = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(førsteKlokke),
            utbetalingsLinjer = nonEmptyListOf(
                ny1, ny2,
            ),
            behandler = NavIdentBruker.Attestant("a1"),
        )

        val stans1 = Utbetalingslinje.Endring.Stans(
            utbetalingslinje = første.sisteUtbetalingslinje(),
            virkningstidspunkt = 1.august(2021),
            clock = andreKlokke,
        )
        val andre = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(andreKlokke),
            utbetalingsLinjer = nonEmptyListOf(
                stans1,
            ),
            behandler = defaultAttestant,
        )

        val ny3 = createUtbetalingslinje(
            opprettet = Tidspunkt.now(tredjeKlokke),
            fraOgMed = 1.august(2021),
            tilOgMed = 31.desember(2021),
            beløp = 5000,
            forrigeUtbetalingslinjeId = andre.sisteUtbetalingslinje().id,
        )

        val tredje = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(tredjeKlokke),
            utbetalingsLinjer = nonEmptyListOf(
                ny3,
            ),
            behandler = defaultAttestant,
        )

        val stans2 = Utbetalingslinje.Endring.Stans(
            utbetalingslinje = tredje.sisteUtbetalingslinje(),
            virkningstidspunkt = 1.august(2021),
            clock = fjerdeKlokke,
        )

        val fjerde = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(fjerdeKlokke),
            utbetalingsLinjer = nonEmptyListOf(
                stans2,
            ),
            behandler = NavIdentBruker.Attestant("a2"),
        )

        val gjen1 = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinje = fjerde.sisteUtbetalingslinje(),
            virkningstidspunkt = 1.august(2021),
            clock = femteKlokke,
        )

        val femte = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(femteKlokke),
            utbetalingsLinjer = nonEmptyListOf(
                gjen1,
            ),
            behandler = NavIdentBruker.Attestant("a3"),
        )

        Avstemming.Konsistensavstemming.Ny(
            id = UUID30.randomUUID(),
            opprettet = fixedTidspunkt,
            løpendeFraOgMed = 1.september(2021).startOfDay(zoneIdOslo),
            opprettetTilOgMed = 5.september(2021).endOfDay(zoneIdOslo),
            utbetalinger = listOf(første, andre, tredje, fjerde, femte),
            avstemmingXmlRequest = "",
            fagområde = Fagområde.SUUFORE,
        ).løpendeUtbetalinger shouldBe listOf(
            OppdragForKonsistensavstemming(
                saksnummer = saksnummer,
                fagområde = Fagområde.SUUFORE,
                fnr = fnr,
                utbetalingslinjer = listOf(
                    ny3.toOppdragslinjeForKonsistensavstemming(
                        nonEmptyListOf(
                            defaultAttestant,
                            NavIdentBruker.Attestant("a2"),
                            NavIdentBruker.Attestant("a3"),
                        ),
                    ),
                ),
            ),
        )

        Avstemming.Konsistensavstemming.Ny(
            id = UUID30.randomUUID(),
            opprettet = fixedTidspunkt,
            løpendeFraOgMed = 1.januar(2021).startOfDay(zoneIdOslo),
            opprettetTilOgMed = 5.september(2021).endOfDay(zoneIdOslo),
            utbetalinger = listOf(første, andre, tredje, fjerde, femte),
            avstemmingXmlRequest = "",
            fagområde = Fagområde.SUUFORE,
        ).løpendeUtbetalinger shouldBe listOf(
            OppdragForKonsistensavstemming(
                saksnummer = saksnummer,
                fagområde = Fagområde.SUUFORE,
                fnr = fnr,
                utbetalingslinjer = listOf(
                    ny1.toOppdragslinjeForKonsistensavstemming(
                        nonEmptyListOf(
                            NavIdentBruker.Attestant("a1"),
                        ),
                    ),
                    ny2.toOppdragslinjeForKonsistensavstemming(
                        nonEmptyListOf(
                            NavIdentBruker.Attestant("a1"),
                            defaultAttestant,
                        ),
                    ),
                    ny3.toOppdragslinjeForKonsistensavstemming(
                        nonEmptyListOf(
                            defaultAttestant,
                            NavIdentBruker.Attestant("a2"),
                            NavIdentBruker.Attestant("a3"),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `inkluderer alle attestanter for linjer som er endret`() {
        val ny1 = createUtbetalingslinje(
            opprettet = Tidspunkt.now(førsteKlokke),
            fraOgMed = 1.januar(2021),
            tilOgMed = 30.april(2021),
            beløp = 10000,
            forrigeUtbetalingslinjeId = null,
        )
        val ny2 = createUtbetalingslinje(
            opprettet = Tidspunkt.now(førsteKlokke),
            fraOgMed = 1.mai(2021),
            tilOgMed = 31.desember(2021),
            beløp = 15000,
            forrigeUtbetalingslinjeId = ny1.id,
        )
        val første = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(førsteKlokke),
            utbetalingsLinjer = nonEmptyListOf(
                ny1, ny2,
            ),
            behandler = NavIdentBruker.Attestant("a1"),
        )

        val stans1 = Utbetalingslinje.Endring.Stans(
            utbetalingslinje = første.sisteUtbetalingslinje(),
            virkningstidspunkt = 1.august(2021),
            clock = andreKlokke,
        )
        val andre = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(andreKlokke),
            utbetalingsLinjer = nonEmptyListOf(
                stans1,
            ),
            behandler = NavIdentBruker.Attestant("a2"),
        )

        val gjen1 = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinje = andre.sisteUtbetalingslinje(),
            virkningstidspunkt = 1.august(2021),
            clock = tredjeKlokke,
        )

        val tredje = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(tredjeKlokke),
            utbetalingsLinjer = nonEmptyListOf(
                gjen1,
            ),
            behandler = NavIdentBruker.Attestant("a3"),
        )

        Avstemming.Konsistensavstemming.Ny(
            id = UUID30.randomUUID(),
            opprettet = fixedTidspunkt,
            løpendeFraOgMed = 1.september(2021).startOfDay(zoneIdOslo),
            opprettetTilOgMed = 5.september(2021).endOfDay(zoneIdOslo),
            utbetalinger = listOf(første, andre, tredje),
            avstemmingXmlRequest = "",
            fagområde = Fagområde.SUUFORE,
        ).løpendeUtbetalinger shouldBe listOf(
            OppdragForKonsistensavstemming(
                saksnummer = saksnummer,
                fagområde = Fagområde.SUUFORE,
                fnr = fnr,
                utbetalingslinjer = listOf(
                    ny2.toOppdragslinjeForKonsistensavstemming(
                        nonEmptyListOf(
                            NavIdentBruker.Attestant("a1"),
                            NavIdentBruker.Attestant("a2"),
                            NavIdentBruker.Attestant("a3"),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `kan gjennomføre konsistensavstemming for helt vilkårlige datoer`() {
        val første = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(førsteKlokke),
            utbetalingsLinjer = nonEmptyListOf(
                createUtbetalingslinje(
                    opprettet = Tidspunkt.now(førsteKlokke),
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 30.april(2021),
                    beløp = 15000,
                    forrigeUtbetalingslinjeId = null,
                ),
                createUtbetalingslinje(
                    opprettet = Tidspunkt.now(førsteKlokke),
                    fraOgMed = 1.mai(2021),
                    tilOgMed = 31.desember(2021),
                    beløp = 17500,
                    forrigeUtbetalingslinjeId = null,
                ),
            ),
            behandler = NavIdentBruker.Attestant("første"),
        )

        val andre = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(andreKlokke),
            utbetalingsLinjer = nonEmptyListOf(
                createUtbetalingslinje(
                    opprettet = Tidspunkt.now(andreKlokke),
                    fraOgMed = 1.desember(2021),
                    tilOgMed = 31.desember(2021),
                    beløp = 20000,
                    forrigeUtbetalingslinjeId = null,
                ),
            ),
            behandler = NavIdentBruker.Attestant("andre"),
        )

        Avstemming.Konsistensavstemming.Ny(
            id = UUID30.randomUUID(),
            opprettet = fixedTidspunkt,
            løpendeFraOgMed = 1.januar(2021).startOfDay(),
            opprettetTilOgMed = Tidspunkt.now(andreKlokke),
            utbetalinger = listOf(første, andre),
            avstemmingXmlRequest = "",
            fagområde = Fagområde.SUUFORE,
        ).løpendeUtbetalinger shouldBe listOf(
            OppdragForKonsistensavstemming(
                saksnummer = saksnummer,
                fagområde = Fagområde.SUUFORE,
                fnr = fnr,
                utbetalingslinjer = listOf(
                    første.utbetalingslinjer[0].toOppdragslinjeForKonsistensavstemming(
                        nonEmptyListOf(
                            NavIdentBruker.Attestant(
                                "første",
                            ),
                        ),
                    ),
                    første.utbetalingslinjer[1].toOppdragslinjeForKonsistensavstemming(
                        nonEmptyListOf(
                            NavIdentBruker.Attestant(
                                "første",
                            ),
                        ),
                    ),
                    andre.utbetalingslinjer[0].toOppdragslinjeForKonsistensavstemming(
                        nonEmptyListOf(
                            NavIdentBruker.Attestant(
                                "andre",
                            ),
                        ),
                    ),
                ),
            ),
        )

        Avstemming.Konsistensavstemming.Ny(
            id = UUID30.randomUUID(),
            opprettet = fixedTidspunkt,
            løpendeFraOgMed = 17.juli(2021).startOfDay(),
            opprettetTilOgMed = Tidspunkt.now(andreKlokke),
            utbetalinger = listOf(første, andre),
            avstemmingXmlRequest = "",
            fagområde = Fagområde.SUUFORE,
        ).løpendeUtbetalinger shouldBe listOf(
            OppdragForKonsistensavstemming(
                saksnummer = saksnummer,
                fagområde = Fagområde.SUUFORE,
                fnr = fnr,
                utbetalingslinjer = listOf(
                    første.utbetalingslinjer[1].toOppdragslinjeForKonsistensavstemming(
                        nonEmptyListOf(
                            NavIdentBruker.Attestant(
                                "første",
                            ),
                        ),
                    ),
                    andre.utbetalingslinjer[0].toOppdragslinjeForKonsistensavstemming(
                        nonEmptyListOf(
                            NavIdentBruker.Attestant(
                                "andre",
                            ),
                        ),
                    ),
                ),
            ),
        )

        Avstemming.Konsistensavstemming.Ny(
            id = UUID30.randomUUID(),
            opprettet = fixedTidspunkt,
            løpendeFraOgMed = 28.desember(2021).endOfDay(),
            opprettetTilOgMed = Tidspunkt.now(andreKlokke),
            utbetalinger = listOf(første, andre),
            avstemmingXmlRequest = "",
            fagområde = Fagområde.SUUFORE,
        ).løpendeUtbetalinger shouldBe listOf(
            OppdragForKonsistensavstemming(
                saksnummer = saksnummer,
                fagområde = Fagområde.SUUFORE,
                fnr = fnr,
                utbetalingslinjer = listOf(
                    andre.utbetalingslinjer[0].toOppdragslinjeForKonsistensavstemming(
                        nonEmptyListOf(
                            NavIdentBruker.Attestant(
                                "andre",
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    private val defaultAttestant = NavIdentBruker.Attestant("Z123")

    private fun createUtbetaling(
        id: UUID30 = UUID30.randomUUID(),
        fnr: Fnr,
        saksnummer: Saksnummer,
        opprettet: Tidspunkt = fixedTidspunkt,
        utbetalingsLinjer: NonEmptyList<Utbetalingslinje>,
        behandler: NavIdentBruker = defaultAttestant,
    ): Utbetaling.OversendtUtbetaling.UtenKvittering {
        return Utbetaling.UtbetalingForSimulering(
            id = id,
            opprettet = opprettet,
            sakId = UUID.randomUUID(),
            saksnummer = saksnummer,
            fnr = fnr,
            utbetalingslinjer = utbetalingsLinjer,
            behandler = behandler,
            avstemmingsnøkkel = Avstemmingsnøkkel(opprettet = fixedTidspunkt),
            sakstype = Sakstype.UFØRE,
        ).toSimulertUtbetaling(
            simulering = Simulering(
                gjelderId = fnr,
                gjelderNavn = "navn",
                datoBeregnet = idag(fixedClock),
                nettoBeløp = 0,
                periodeList = listOf(),
            ),
        ).toOversendtUtbetaling(
            oppdragsmelding = Utbetalingsrequest(value = ""),
        )
    }

    private fun createUtbetalingslinje(
        opprettet: Tidspunkt,
        fraOgMed: LocalDate = 1.januar(2020),
        tilOgMed: LocalDate = 31.desember(2020),
        beløp: Int = 500,
        forrigeUtbetalingslinjeId: UUID30? = null,
        uføregrad: Uføregrad = Uføregrad.parse(50),
    ) = Utbetalingslinje.Ny(
        opprettet = opprettet,
        fraOgMed = fraOgMed,
        tilOgMed = tilOgMed,
        beløp = beløp,
        forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
        uføregrad = uføregrad,
    )

    private fun List<Utbetalingslinje>.toOppdragslinjeForKonsistensavstemming(attestant: NavIdentBruker): List<OppdragslinjeForKonsistensavstemming> {
        return map { it.toOppdragslinjeForKonsistensavstemming(nonEmptyListOf(attestant)) }
    }
}
