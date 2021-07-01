package no.nav.su.se.bakover.domain.tidslinje

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import org.junit.jupiter.api.Test
import java.time.temporal.ChronoUnit

internal class TidslinjeForUtbetalingerTest {

    @Test
    fun `ingen utbetalinger`() {
        TidslinjeForUtbetalinger(
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            objekter = emptyList(),
        ).tidslinje shouldBe emptyList()
    }

    @Test
    fun `en utbetaling`() {
        val første = Utbetalingslinje.Ny(
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
        )
        TidslinjeForUtbetalinger(
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            objekter = listOf(første),
        ).tidslinje shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                opprettet = første.opprettet,
                periode = Periode.create(1.januar(2020), 30.april(2020)),
                beløp = første.beløp,
            ),
        )
    }

    @Test
    fun `et par helt ordinære utbetalinger`() {
        val første = Utbetalingslinje.Ny(
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
        )
        val andre = Utbetalingslinje.Ny(
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
        )

        TidslinjeForUtbetalinger(
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            objekter = listOf(første, andre),
        ).tidslinje shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                opprettet = første.opprettet,
                periode = Periode.create(1.januar(2020), 30.april(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Ny(
                opprettet = andre.opprettet,
                periode = Periode.create(1.mai(2020), 31.desember(2020)),
                beløp = andre.beløp,
            ),
        )
    }

    @Test
    fun `enkel stans på tvers av måneder med forskjellig beløp`() {
        val første = Utbetalingslinje.Ny(
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
        )
        val andre = Utbetalingslinje.Ny(
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
        )
        val stans = Utbetalingslinje.Endring.Stans(
            utbetalingslinje = andre,
            virkningstidspunkt = 1.april(2020),
        )

        TidslinjeForUtbetalinger(
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            objekter = listOf(første, andre, stans),
        ).tidslinje shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                opprettet = første.opprettet,
                periode = Periode.create(1.januar(2020), 31.mars(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Stans(
                opprettet = stans.opprettet,
                periode = Periode.create(1.april(2020), 31.desember(2020)),
                beløp = 0,
            ),
        )
    }

    @Test
    fun `reaktivering av enkel stans på tvers av måneder med forskjellig beløp`() {
        val første = Utbetalingslinje.Ny(
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
        )
        val andre = Utbetalingslinje.Ny(
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
        )
        val stans = Utbetalingslinje.Endring.Stans(
            utbetalingslinje = andre,
            virkningstidspunkt = 1.mars(2020),
        )
        val reaktivering = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinje = stans,
            virkningstidspunkt = 1.april(2020),
        )

        TidslinjeForUtbetalinger(
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            objekter = listOf(første, andre, stans, reaktivering),
        ).tidslinje shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                opprettet = første.opprettet,
                periode = Periode.create(1.januar(2020), 29.februar(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Stans(
                opprettet = stans.opprettet,
                periode = Periode.create(1.mars(2020), 31.mars(2020)),
                beløp = 0,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                opprettet = reaktivering.opprettet.plus(1, ChronoUnit.MICROS),
                periode = Periode.create(1.april(2020), 30.april(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                opprettet = reaktivering.opprettet,
                periode = Periode.create(1.mai(2020), 31.desember(2020)),
                beløp = reaktivering.beløp,
            ),
        )
    }

    @Test
    fun `reaktivering av opphør på tvers av måneder med forskjellig beløp`() {
        val første = Utbetalingslinje.Ny(
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
        )
        val andre = Utbetalingslinje.Ny(
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
        )
        val opphør = Utbetalingslinje.Endring.Opphør(
            utbetalingslinje = andre,
            virkningstidspunkt = 1.mars(2020),
        )
        val reaktivering = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinje = opphør,
            virkningstidspunkt = 1.april(2020),
        )

        TidslinjeForUtbetalinger(
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            objekter = listOf(første, andre, opphør, reaktivering),
        ).tidslinje shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                opprettet = første.opprettet,
                periode = Periode.create(1.januar(2020), 29.februar(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Opphør(
                opprettet = opphør.opprettet,
                periode = Periode.create(1.mars(2020), 31.mars(2020)),
                beløp = 0,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                opprettet = reaktivering.opprettet.plus(1, ChronoUnit.MICROS),
                periode = Periode.create(1.april(2020), 30.april(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                opprettet = reaktivering.opprettet,
                periode = Periode.create(1.mai(2020), 31.desember(2020)),
                beløp = reaktivering.beløp,
            ),
        )
    }

    @Test
    fun `opphør av alle utbetalingene`() {
        val første = Utbetalingslinje.Ny(
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
        )
        val andre = Utbetalingslinje.Ny(
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
        )
        val opphør = Utbetalingslinje.Endring.Opphør(
            utbetalingslinje = andre,
            virkningstidspunkt = 1.januar(2020),
        )

        TidslinjeForUtbetalinger(
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            objekter = listOf(første, andre, opphør),
        ).tidslinje shouldBe listOf(
            UtbetalingslinjePåTidslinje.Opphør(
                opprettet = opphør.opprettet,
                periode = Periode.create(1.januar(2020), 31.desember(2020)),
                beløp = 0,
            ),
        )
    }

    @Test
    fun `reaktivering av ytelse som er opphørt`() {
        val første = Utbetalingslinje.Ny(
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
        )
        val andre = Utbetalingslinje.Ny(
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
        )
        val opphør = Utbetalingslinje.Endring.Opphør(
            utbetalingslinje = andre,
            virkningstidspunkt = 1.januar(2020),
        )
        val reaktivering = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinje = opphør,
            virkningstidspunkt = 1.januar(2020),
        )

        TidslinjeForUtbetalinger(
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            objekter = listOf(første, andre, opphør, reaktivering),
        ).tidslinje shouldBe listOf(
            UtbetalingslinjePåTidslinje.Reaktivering(
                opprettet = reaktivering.opprettet.plus(1, ChronoUnit.MICROS),
                periode = Periode.create(1.januar(2020), 30.april(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                opprettet = reaktivering.opprettet,
                periode = Periode.create(1.mai(2020), 31.desember(2020)),
                beløp = andre.beløp,
            ),
        )
    }

    @Test
    fun `reaktivering av ytelse som har blitt revurdert etter stans`() {
        val første = Utbetalingslinje.Ny(
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
        )
        val andre = Utbetalingslinje.Ny(
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
        )
        val førsteStans = Utbetalingslinje.Endring.Stans(
            utbetalingslinje = andre,
            virkningstidspunkt = 1.mars(2020),
        )
        val tredje = Utbetalingslinje.Ny(
            fraOgMed = 1.mars(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = førsteStans.id,
            beløp = 3000,
        )
        val andreStans = Utbetalingslinje.Endring.Stans(
            utbetalingslinje = tredje,
            virkningstidspunkt = 1.oktober(2020),
        )
        val reaktivering = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinje = andreStans,
            virkningstidspunkt = 1.november(2020),
        )

        TidslinjeForUtbetalinger(
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            objekter = listOf(andre, første, tredje, førsteStans, andreStans, reaktivering),
        ).tidslinje shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                opprettet = første.opprettet,
                periode = Periode.create(1.januar(2020), 29.februar(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Ny(
                opprettet = tredje.opprettet,
                periode = Periode.create(1.mars(2020), 30.september(2020)),
                beløp = tredje.beløp,
            ),
            UtbetalingslinjePåTidslinje.Stans(
                opprettet = andreStans.opprettet,
                periode = Periode.create(1.oktober(2020), 31.oktober(2020)),
                beløp = 0,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                opprettet = reaktivering.opprettet,
                periode = Periode.create(1.november(2020), 31.desember(2020)),
                beløp = reaktivering.beløp,
            ),
        )
    }

    @Test
    fun `kan stanse tidligerer reaktivert ytelse igjen`() {
        val første = Utbetalingslinje.Ny(
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
        )
        val andre = Utbetalingslinje.Ny(
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
        )
        val førsteStans = Utbetalingslinje.Endring.Stans(
            utbetalingslinje = andre,
            virkningstidspunkt = 1.april(2020),
        )
        val reaktivering = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinje = førsteStans,
            virkningstidspunkt = 1.april(2020),
        )
        val andreStans = Utbetalingslinje.Endring.Stans(
            utbetalingslinje = reaktivering,
            virkningstidspunkt = 1.april(2020),
        )

        TidslinjeForUtbetalinger(
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            objekter = listOf(andre, første, førsteStans, andreStans, reaktivering),
        ).tidslinje shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                opprettet = første.opprettet,
                periode = Periode.create(1.januar(2020), 31.mars(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Stans(
                opprettet = andreStans.opprettet,
                periode = Periode.create(1.april(2020), 31.desember(2020)),
                beløp = 0,
            ),
        )
    }

    @Test
    fun `reaktivering av tidligere stans og reaktiveringer på tvers av måneder med forskjellig beløp`() {
        val første = Utbetalingslinje.Ny(
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
        )
        val andre = Utbetalingslinje.Ny(
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
        )
        val førsteStans = Utbetalingslinje.Endring.Stans(
            utbetalingslinje = andre,
            virkningstidspunkt = 1.mars(2020),
        )
        val førsteReaktivering = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinje = førsteStans,
            virkningstidspunkt = 1.mars(2020),
        )
        val andreStans = Utbetalingslinje.Endring.Stans(
            utbetalingslinje = førsteReaktivering,
            virkningstidspunkt = 1.oktober(2020),
        )
        val andreReaktivering = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinje = førsteStans,
            virkningstidspunkt = 1.november(2020),
        )

        TidslinjeForUtbetalinger(
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            objekter = listOf(andre, andreReaktivering, førsteStans, førsteReaktivering, første, andreStans),
        ).tidslinje shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                opprettet = første.opprettet,
                periode = Periode.create(1.januar(2020), 29.februar(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                opprettet = førsteReaktivering.opprettet.plus(1, ChronoUnit.MICROS),
                periode = Periode.create(1.mars(2020), 30.april(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                opprettet = førsteReaktivering.opprettet,
                periode = Periode.create(1.mai(2020), 30.september(2020)),
                beløp = andre.beløp,
            ),
            UtbetalingslinjePåTidslinje.Stans(
                opprettet = andreStans.opprettet,
                periode = Periode.create(1.oktober(2020), 31.oktober(2020)),
                beløp = 0,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                opprettet = andreReaktivering.opprettet,
                periode = Periode.create(1.november(2020), 31.desember(2020)),
                beløp = andreReaktivering.beløp,
            ),
        )
    }

    @Test
    fun `opphør av måned tilbake i tid med påfølgende reaktivering`() {
        val første = Utbetalingslinje.Ny(
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
        )
        val andre = Utbetalingslinje.Ny(
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
        )
        val opphør = Utbetalingslinje.Endring.Opphør(
            utbetalingslinje = andre,
            virkningstidspunkt = 1.mars(2020),
        )
        val reaktivering = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinje = opphør,
            virkningstidspunkt = 1.april(2020),
        )

        TidslinjeForUtbetalinger(
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            objekter = listOf(første, andre, opphør, reaktivering),
        ).tidslinje shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                opprettet = første.opprettet,
                periode = Periode.create(1.januar(2020), 29.februar(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Opphør(
                opprettet = opphør.opprettet,
                periode = Periode.create(1.mars(2020), 31.mars(2020)),
                beløp = 0,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                opprettet = reaktivering.opprettet.plus(1, ChronoUnit.MICROS),
                periode = Periode.create(1.april(2020), 30.april(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                opprettet = reaktivering.opprettet,
                periode = Periode.create(1.mai(2020), 31.desember(2020)),
                beløp = andre.beløp,
            ),
        )
    }

    @Test
    fun `potpurri`() {
        val første = Utbetalingslinje.Ny(
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
        )
        val andre = Utbetalingslinje.Ny(
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
        )
        val opphør = Utbetalingslinje.Endring.Opphør(
            utbetalingslinje = andre,
            virkningstidspunkt = 1.oktober(2020),
        )
        val tredje = Utbetalingslinje.Ny(
            fraOgMed = 1.oktober(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = opphør.id,
            beløp = 3000,
        )
        val stans = Utbetalingslinje.Endring.Stans(
            utbetalingslinje = tredje,
            virkningstidspunkt = 1.august(2020),
        )
        val reaktivering = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinje = stans,
            virkningstidspunkt = 1.august(2020),
        )

        TidslinjeForUtbetalinger(
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            objekter = listOf(første, andre, opphør, tredje, stans, reaktivering),
        ).tidslinje shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                opprettet = første.opprettet,
                periode = Periode.create(1.januar(2020), 30.april(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Ny(
                opprettet = andre.opprettet,
                periode = Periode.create(1.mai(2020), 31.juli(2020)),
                beløp = andre.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                opprettet = reaktivering.opprettet.plus(1, ChronoUnit.MICROS),
                periode = Periode.create(1.august(2020), 30.september(2020)),
                beløp = andre.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                opprettet = reaktivering.opprettet,
                periode = Periode.create(1.oktober(2020), 31.desember(2020)),
                beløp = tredje.beløp,
            ),
        )
    }

    @Test
    fun `helt ordinære utbetalinger - rekkefølgen på input har ikke noe å si`() {
        val første = Utbetalingslinje.Ny(
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
        )
        val andre = Utbetalingslinje.Ny(
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
        )
        val tredje = Utbetalingslinje.Ny(
            fraOgMed = 1.januar(2021),
            tilOgMed = 31.desember(2021),
            forrigeUtbetalingslinjeId = andre.id,
            beløp = 3000,
        )
        val fjerde = Utbetalingslinje.Ny(
            fraOgMed = 1.februar(2021),
            tilOgMed = 31.desember(2021),
            forrigeUtbetalingslinjeId = tredje.id,
            beløp = 4000,
        )
        val femte = Utbetalingslinje.Ny(
            fraOgMed = 1.mars(2021),
            tilOgMed = 31.desember(2021),
            forrigeUtbetalingslinjeId = fjerde.id,
            beløp = 5000,
        )

        TidslinjeForUtbetalinger(
            periode = Periode.create(1.januar(2020), 31.desember(2021)),
            objekter = listOf(andre, femte, tredje, første, fjerde),
        ).tidslinje shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                opprettet = første.opprettet,
                periode = Periode.create(1.januar(2020), 30.april(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Ny(
                opprettet = andre.opprettet,
                periode = Periode.create(1.mai(2020), 31.desember(2020)),
                beløp = andre.beløp,
            ),
            UtbetalingslinjePåTidslinje.Ny(
                opprettet = tredje.opprettet,
                periode = Periode.create(1.januar(2021), 31.januar(2021)),
                beløp = tredje.beløp,
            ),
            UtbetalingslinjePåTidslinje.Ny(
                opprettet = fjerde.opprettet,
                periode = Periode.create(1.februar(2021), 28.februar(2021)),
                beløp = fjerde.beløp,
            ),
            UtbetalingslinjePåTidslinje.Ny(
                opprettet = femte.opprettet,
                periode = Periode.create(1.mars(2021), 31.desember(2021)),
                beløp = femte.beløp,
            ),
        )
    }
}
