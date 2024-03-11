package no.nav.su.se.bakover.domain.oppdrag.avstemming

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Rekkefølge
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.extensions.april
import no.nav.su.se.bakover.common.extensions.august
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.endOfDay
import no.nav.su.se.bakover.common.extensions.idag
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.juli
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.extensions.mars
import no.nav.su.se.bakover.common.extensions.september
import no.nav.su.se.bakover.common.extensions.startOfDay
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.extensions.zoneIdOslo
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.minAndMaxOf
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.plus
import no.nav.su.se.bakover.test.saksnummer
import org.junit.jupiter.api.Test
import vilkår.uføre.domain.Uføregrad
import økonomi.domain.Fagområde
import økonomi.domain.avstemming.Avstemmingsnøkkel
import økonomi.domain.simulering.Simulering
import økonomi.domain.simulering.SimulertMåned
import økonomi.domain.utbetaling.ForrigeUtbetalingslinjeKoblendeListe
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.Utbetalingslinje
import økonomi.domain.utbetaling.Utbetalingsrequest
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
                ),
            ),
        )

        val s1u2 = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(andreKlokke),
            utbetalingsLinjer = ForrigeUtbetalingslinjeKoblendeListe(
                listOf(
                    createUtbetalingslinje(
                        opprettet = Tidspunkt.now(andreKlokke),
                        fraOgMed = 1.mai(2021),
                        tilOgMed = 31.juli(2021),
                        beløp = 20000,
                    ),
                    createUtbetalingslinje(
                        opprettet = Tidspunkt.now(andreKlokke).plusUnits(1),
                        fraOgMed = 1.september(2021),
                        tilOgMed = 31.desember(2021),
                        beløp = 20000,
                        rekkefølge = Rekkefølge.skip(0),
                    ),
                ),
            ).toNonEmptyList(),
        )

        val fnr2 = Fnr.generer()
        val saksnummer2 = Saksnummer(9999)
        val s2u1 = createUtbetaling(
            fnr = fnr2,
            saksnummer = saksnummer2,
            opprettet = Tidspunkt.now(førsteKlokke),
            utbetalingsLinjer = ForrigeUtbetalingslinjeKoblendeListe(
                listOf(
                    createUtbetalingslinje(
                        opprettet = Tidspunkt.now(førsteKlokke),
                        fraOgMed = 1.januar(2021),
                        tilOgMed = 31.juli(2021),
                        beløp = 15000,
                    ),
                    createUtbetalingslinje(
                        opprettet = Tidspunkt.now(førsteKlokke).plusUnits(1),
                        fraOgMed = 1.september(2021),
                        tilOgMed = 31.desember(2021),
                        beløp = 18000,
                        rekkefølge = Rekkefølge.skip(0),
                    ),
                ),
            ).toNonEmptyList(),
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
                ),
            ),
        )

        val s1u2 = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(andreKlokke),
            utbetalingsLinjer = ForrigeUtbetalingslinjeKoblendeListe(
                listOf(
                    createUtbetalingslinje(
                        opprettet = Tidspunkt.now(andreKlokke),
                        fraOgMed = 1.mai(2021),
                        tilOgMed = 31.juli(2021),
                        beløp = 20000,
                    ),
                    createUtbetalingslinje(
                        opprettet = Tidspunkt.now(andreKlokke).plusUnits(1),
                        fraOgMed = 1.september(2021),
                        tilOgMed = 31.desember(2021),
                        beløp = 20000,
                        rekkefølge = Rekkefølge.skip(0),
                    ),
                ),
            ).toNonEmptyList(),
        )

        val fnr2 = Fnr.generer()
        val saksnummer2 = Saksnummer(9999)
        val s2u1 = createUtbetaling(
            fnr = fnr2,
            saksnummer = saksnummer2,
            opprettet = Tidspunkt.now(førsteKlokke),
            utbetalingsLinjer = ForrigeUtbetalingslinjeKoblendeListe(
                listOf(
                    createUtbetalingslinje(
                        opprettet = Tidspunkt.now(førsteKlokke),
                        fraOgMed = 1.januar(2021),
                        tilOgMed = 31.juli(2021),
                        beløp = 15000,
                    ),
                    createUtbetalingslinje(
                        opprettet = Tidspunkt.now(førsteKlokke).plusUnits(1),
                        fraOgMed = 1.september(2021),
                        tilOgMed = 31.desember(2021),
                        beløp = 18000,
                        rekkefølge = Rekkefølge.skip(0),
                    ),
                ),
            ).toNonEmptyList(),
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
                ),
            ),
        )

        Avstemming.Konsistensavstemming.Ny(
            id = UUID30.randomUUID(),
            opprettet = fixedTidspunkt,
            // Dato litt lenger fram i tid
            løpendeFraOgMed = 1.desember(2021).startOfDay(),
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
            // Dato langt bak i tid
            opprettetTilOgMed = 1.januar(2000).endOfDay(),
            utbetalinger = listOf(s1u1, s1u2, s2u1, s2u2),
            avstemmingXmlRequest = "",
            fagområde = Fagområde.SUUFORE,
        ).løpendeUtbetalinger shouldBe emptyList()

        Avstemming.Konsistensavstemming.Ny(
            id = UUID30.randomUUID(),
            opprettet = fixedTidspunkt,
            løpendeFraOgMed = 1.januar(2021).startOfDay(),
            // Dato forut for andre klokke
            opprettetTilOgMed = Tidspunkt.now(andreKlokke).minus(1, ChronoUnit.DAYS),
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
        val rekkefølge = Rekkefølge.generator()
        val første = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(førsteKlokke),
            utbetalingsLinjer = nonEmptyListOf(
                createUtbetalingslinje(
                    opprettet = Tidspunkt.now(førsteKlokke),
                    rekkefølge = rekkefølge.neste(),
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.desember(2021),
                    beløp = 15000,
                ),
            ),
        )

        val andre = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(andreKlokke),
            utbetalingsLinjer = nonEmptyListOf(
                Utbetalingslinje.Endring.Opphør(
                    utbetalingslinjeSomSkalEndres = første.utbetalingslinjer[0],
                    virkningsperiode = Periode.create(1.april(2021), første.utbetalingslinjer[0].periode.tilOgMed),
                    clock = andreKlokke,
                    rekkefølge = Rekkefølge.start(),
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
                ),
            ),
        )

        val andre = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(andreKlokke),
            utbetalingsLinjer = nonEmptyListOf(
                Utbetalingslinje.Endring.Opphør(
                    utbetalingslinjeSomSkalEndres = første.utbetalingslinjer[0],
                    virkningsperiode = Periode.create(1.april(2021), første.utbetalingslinjer[0].periode.tilOgMed),
                    clock = andreKlokke,
                    rekkefølge = Rekkefølge.start(),
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
                    rekkefølge = Rekkefølge.start(),
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
                    rekkefølge = Rekkefølge.start(),
                ),
            ),
        )

        val andre = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(andreKlokke),
            utbetalingsLinjer = nonEmptyListOf(
                Utbetalingslinje.Endring.Opphør(
                    utbetalingslinjeSomSkalEndres = første.utbetalingslinjer[0],
                    virkningsperiode = Periode.create(1.april(2021), første.utbetalingslinjer[0].periode.tilOgMed),
                    clock = andreKlokke,
                    rekkefølge = Rekkefølge.start(),
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
        val rekkefølge = Rekkefølge.generator()
        val første = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(førsteKlokke),
            utbetalingsLinjer = ForrigeUtbetalingslinjeKoblendeListe(
                listOf(
                    createUtbetalingslinje(
                        opprettet = Tidspunkt.now(førsteKlokke),
                        fraOgMed = 1.januar(2021),
                        tilOgMed = 30.april(2021),
                        beløp = 10000,
                        rekkefølge = rekkefølge.neste(),
                    ),
                    createUtbetalingslinje(
                        opprettet = Tidspunkt.now(førsteKlokke).plusUnits(1),
                        fraOgMed = 1.mai(2021),
                        tilOgMed = 31.desember(2021),
                        beløp = 15000,
                        rekkefølge = rekkefølge.neste(),
                    ),
                ),
            ).toNonEmptyList(),
            behandler = NavIdentBruker.Attestant("a1"),
        )

        val stans1 = Utbetalingslinje.Endring.Stans(
            utbetalingslinjeSomSkalEndres = første.sisteUtbetalingslinje(),
            virkningstidspunkt = 1.august(2021),
            clock = andreKlokke,
            rekkefølge = Rekkefølge.start(),
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

        val tredje = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(tredjeKlokke),
            utbetalingsLinjer = nonEmptyListOf(
                createUtbetalingslinje(
                    opprettet = Tidspunkt.now(tredjeKlokke),
                    fraOgMed = 1.august(2021),
                    tilOgMed = 31.desember(2021),
                    beløp = 5000,
                    rekkefølge = Rekkefølge.start(),
                ),
            ),
            behandler = defaultAttestant,
        )

        val stans2 = Utbetalingslinje.Endring.Stans(
            utbetalingslinjeSomSkalEndres = tredje.sisteUtbetalingslinje(),
            virkningstidspunkt = 1.august(2021),
            clock = fjerdeKlokke,
            rekkefølge = Rekkefølge.start(),
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
            utbetalingslinjeSomSkalEndres = fjerde.sisteUtbetalingslinje(),
            virkningstidspunkt = 1.august(2021),
            clock = femteKlokke,
            rekkefølge = Rekkefølge.start(),
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
                    tredje.sisteUtbetalingslinje().toOppdragslinjeForKonsistensavstemming(
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
                    første.utbetalingslinjer.first().toOppdragslinjeForKonsistensavstemming(
                        nonEmptyListOf(
                            NavIdentBruker.Attestant("a1"),
                        ),
                    ),
                    første.utbetalingslinjer.last().toOppdragslinjeForKonsistensavstemming(
                        nonEmptyListOf(
                            NavIdentBruker.Attestant("a1"),
                            defaultAttestant,
                        ),
                    ),
                    tredje.sisteUtbetalingslinje().toOppdragslinjeForKonsistensavstemming(
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
    fun `ny og stans i første utbetaling og reaktivering i andre - filtrerer vekk stans og reaktivering, men beholder attestant`() {
        val førsteKlokke = Clock.fixed(6.september(2021).startOfDay().instant, ZoneOffset.UTC)
        val andreKlokke = førsteKlokke.plus(1, ChronoUnit.DAYS)
        val førsteutbetalingslinje = createUtbetalingslinje(
            opprettet = Tidspunkt.now(førsteKlokke),
            fraOgMed = 1.januar(2021),
            tilOgMed = 30.april(2021),
            beløp = 10000,
        )
        val stans = Utbetalingslinje.Endring.Stans(
            utbetalingslinjeSomSkalEndres = førsteutbetalingslinje,
            virkningstidspunkt = 1.april(2021),
            clock = førsteKlokke.plus(1, ChronoUnit.MICROS),
            rekkefølge = Rekkefølge.ANDRE,
        )

        val første = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(førsteKlokke),
            utbetalingsLinjer = ForrigeUtbetalingslinjeKoblendeListe(
                listOf(
                    førsteutbetalingslinje,
                    stans,
                ),
            ).toNonEmptyList(),
            behandler = NavIdentBruker.Attestant("a1"),
        )

        val reaktivering = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinjeSomSkalEndres = stans,
            virkningstidspunkt = 1.april(2021),
            clock = andreKlokke,
            rekkefølge = Rekkefølge.start(),
        )

        val andre = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(andreKlokke),
            utbetalingsLinjer = ForrigeUtbetalingslinjeKoblendeListe(
                listOf(
                    reaktivering,
                ),
            ).toNonEmptyList(),
            behandler = NavIdentBruker.Attestant("a2"),
        )

        val løpendeUtbetalinger = Avstemming.Konsistensavstemming.Ny(
            id = UUID30.randomUUID(),
            opprettet = fixedTidspunkt,
            løpendeFraOgMed = 1.april(2021).startOfDay(zoneIdOslo),
            opprettetTilOgMed = 7.september(2021).endOfDay(zoneIdOslo),
            utbetalinger = listOf(første, andre),
            avstemmingXmlRequest = "",
            fagområde = Fagområde.SUUFORE,
        ).løpendeUtbetalinger
        løpendeUtbetalinger shouldBe listOf(
            OppdragForKonsistensavstemming(
                saksnummer = saksnummer,
                fagområde = Fagområde.SUUFORE,
                fnr = fnr,
                utbetalingslinjer = listOf(
                    førsteutbetalingslinje.toOppdragslinjeForKonsistensavstemming(
                        nonEmptyListOf(
                            NavIdentBruker.Attestant("a1"),
                            NavIdentBruker.Attestant("a2"),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `inkluderer alle attestanter for linjer som er endret`() {
        val rekkefølge = Rekkefølge.generator()
        val første = createUtbetaling(
            fnr = fnr,
            saksnummer = saksnummer,
            opprettet = Tidspunkt.now(førsteKlokke),
            utbetalingsLinjer = ForrigeUtbetalingslinjeKoblendeListe(
                listOf(
                    createUtbetalingslinje(
                        opprettet = Tidspunkt.now(førsteKlokke),
                        fraOgMed = 1.januar(2021),
                        tilOgMed = 30.april(2021),
                        beløp = 10000,
                        rekkefølge = rekkefølge.neste(),
                    ),
                    createUtbetalingslinje(
                        opprettet = Tidspunkt.now(førsteKlokke).plusUnits(1),
                        fraOgMed = 1.mai(2021),
                        tilOgMed = 31.desember(2021),
                        beløp = 15000,
                        rekkefølge = rekkefølge.neste(),
                    ),
                ),
            ).toNonEmptyList(),
            behandler = NavIdentBruker.Attestant("a1"),
        )

        val stans1 = Utbetalingslinje.Endring.Stans(
            utbetalingslinjeSomSkalEndres = første.sisteUtbetalingslinje(),
            virkningstidspunkt = 1.august(2021),
            clock = andreKlokke,
            rekkefølge = Rekkefølge.start(),
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
            utbetalingslinjeSomSkalEndres = stans1,
            virkningstidspunkt = 1.august(2021),
            clock = tredjeKlokke,
            rekkefølge = Rekkefølge.start(),
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
                    første.sisteUtbetalingslinje().toOppdragslinjeForKonsistensavstemming(
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
            utbetalingsLinjer = ForrigeUtbetalingslinjeKoblendeListe(
                listOf(
                    createUtbetalingslinje(
                        opprettet = Tidspunkt.now(førsteKlokke),
                        fraOgMed = 1.januar(2021),
                        tilOgMed = 30.april(2021),
                        beløp = 15000,
                    ),
                    createUtbetalingslinje(
                        opprettet = Tidspunkt.now(førsteKlokke).plusUnits(1),
                        fraOgMed = 1.mai(2021),
                        tilOgMed = 31.desember(2021),
                        beløp = 17500,
                        rekkefølge = Rekkefølge.skip(0),
                    ),
                ),
            ).toNonEmptyList(),
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
                måneder = SimulertMåned.create(utbetalingsLinjer.map { it.periode }.minAndMaxOf()),
                rawResponse = "KonsistensavstemmingTest baserer ikke denne på rå XML.",
            ),
        ).toOversendtUtbetaling(
            oppdragsmelding = Utbetalingsrequest(value = ""),
        )
    }

    private fun createUtbetalingslinje(
        opprettet: Tidspunkt,
        rekkefølge: Rekkefølge = Rekkefølge.start(),
        fraOgMed: LocalDate = 1.januar(2020),
        tilOgMed: LocalDate = 31.desember(2020),
        beløp: Int = 500,
        uføregrad: Uføregrad = Uføregrad.parse(50),
    ) = Utbetalingslinje.Ny(
        opprettet = opprettet,
        rekkefølge = rekkefølge,
        fraOgMed = fraOgMed,
        tilOgMed = tilOgMed,
        beløp = beløp,
        forrigeUtbetalingslinjeId = null,
        uføregrad = uføregrad,
    )

    private fun List<Utbetalingslinje>.toOppdragslinjeForKonsistensavstemming(attestant: NavIdentBruker): List<OppdragslinjeForKonsistensavstemming> {
        return map { it.toOppdragslinjeForKonsistensavstemming(nonEmptyListOf(attestant)) }
    }
}
