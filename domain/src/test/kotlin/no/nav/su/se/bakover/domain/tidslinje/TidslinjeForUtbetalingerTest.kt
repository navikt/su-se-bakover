package no.nav.su.se.bakover.domain.tidslinje

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
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
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

internal class TidslinjeForUtbetalingerTest {

    private val clock = Clock.systemUTC()

    @Test
    fun `ingen utbetalinger`() {
        TidslinjeForUtbetalinger(
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            utbetalingslinjer = emptyList(),
        ).tidslinje shouldBe emptyList()
    }

    @Test
    fun `en utbetaling`() {
        val første = Utbetalingslinje.Ny(
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
        )
        TidslinjeForUtbetalinger(
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            utbetalingslinjer = listOf(første),
        ).tidslinje shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,
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
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
            uføregrad = Uføregrad.parse(50),
        )

        TidslinjeForUtbetalinger(
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            utbetalingslinjer = listOf(første, andre),
        ).tidslinje shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,
                opprettet = første.opprettet,
                periode = Periode.create(1.januar(2020), 30.april(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = andre.id,
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
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
            uføregrad = Uføregrad.parse(50),
        )
        val stans = Utbetalingslinje.Endring.Stans(
            utbetalingslinje = andre,
            virkningstidspunkt = 1.april(2020),
            clock = clock,
        )

        TidslinjeForUtbetalinger(
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            utbetalingslinjer = listOf(første, andre, stans),
        ).tidslinje shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,
                opprettet = første.opprettet,
                periode = Periode.create(1.januar(2020), 31.mars(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Stans(
                kopiertFraId = stans.id,
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
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
            uføregrad = Uføregrad.parse(50),
        )
        val stans = Utbetalingslinje.Endring.Stans(
            utbetalingslinje = andre,
            virkningstidspunkt = 1.mars(2020),
            clock = clock,
        )
        val reaktivering = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinje = stans,
            virkningstidspunkt = 1.april(2020),
            clock = clock,
        )

        TidslinjeForUtbetalinger(
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            utbetalingslinjer = listOf(første, andre, stans, reaktivering),
        ).tidslinje shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,
                opprettet = første.opprettet,
                periode = Periode.create(1.januar(2020), 29.februar(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Stans(
                kopiertFraId = stans.id,
                opprettet = stans.opprettet,
                periode = Periode.create(1.mars(2020), 31.mars(2020)),
                beløp = 0,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = første.id,
                opprettet = reaktivering.opprettet.plus(1, ChronoUnit.MICROS),
                periode = Periode.create(1.april(2020), 30.april(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = reaktivering.id,
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
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
            uføregrad = Uføregrad.parse(50),
        )
        val opphør = Utbetalingslinje.Endring.Opphør(
            utbetalingslinje = andre,
            virkningstidspunkt = 1.mars(2020),
            clock = clock,
        )
        val reaktivering = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinje = opphør,
            virkningstidspunkt = 1.april(2020),
            clock = clock,
        )

        TidslinjeForUtbetalinger(
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            utbetalingslinjer = listOf(første, andre, opphør, reaktivering),
        ).tidslinje shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,
                opprettet = første.opprettet,
                periode = Periode.create(1.januar(2020), 29.februar(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Opphør(
                kopiertFraId = opphør.id,
                opprettet = opphør.opprettet,
                periode = Periode.create(1.mars(2020), 31.mars(2020)),
                beløp = 0,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = første.id,
                opprettet = reaktivering.opprettet.plus(1, ChronoUnit.MICROS),
                periode = Periode.create(1.april(2020), 30.april(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = reaktivering.id,
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
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
            uføregrad = Uføregrad.parse(50),
        )
        val opphør = Utbetalingslinje.Endring.Opphør(
            utbetalingslinje = andre,
            virkningstidspunkt = 1.januar(2020),
            clock = clock,
        )

        TidslinjeForUtbetalinger(
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            utbetalingslinjer = listOf(første, andre, opphør),
        ).tidslinje shouldBe listOf(
            UtbetalingslinjePåTidslinje.Opphør(
                kopiertFraId = opphør.id,
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
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
            uføregrad = Uføregrad.parse(50),
        )
        val opphør = Utbetalingslinje.Endring.Opphør(
            utbetalingslinje = andre,
            virkningstidspunkt = 1.januar(2020),
            clock = clock,
        )
        val reaktivering = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinje = opphør,
            virkningstidspunkt = 1.januar(2020),
            clock = clock,
        )

        TidslinjeForUtbetalinger(
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            utbetalingslinjer = listOf(første, andre, opphør, reaktivering),
        ).tidslinje shouldBe listOf(
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = første.id,
                opprettet = reaktivering.opprettet.plus(1, ChronoUnit.MICROS),
                periode = Periode.create(1.januar(2020), 30.april(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = andre.id,
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
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
            uføregrad = Uføregrad.parse(50),
        )
        val førsteStans = Utbetalingslinje.Endring.Stans(
            utbetalingslinje = andre,
            virkningstidspunkt = 1.mars(2020),
            clock = clock,
        )
        val tredje = Utbetalingslinje.Ny(
            fraOgMed = 1.mars(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = førsteStans.id,
            beløp = 3000,
            uføregrad = Uføregrad.parse(50),
        )
        val andreStans = Utbetalingslinje.Endring.Stans(
            utbetalingslinje = tredje,
            virkningstidspunkt = 1.oktober(2020),
            clock = clock,
        )
        val reaktivering = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinje = andreStans,
            virkningstidspunkt = 1.november(2020),
            clock = clock,
        )

        TidslinjeForUtbetalinger(
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            utbetalingslinjer = listOf(andre, første, tredje, førsteStans, andreStans, reaktivering),
        ).tidslinje shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,
                opprettet = første.opprettet,
                periode = Periode.create(1.januar(2020), 29.februar(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = tredje.id,
                opprettet = tredje.opprettet,
                periode = Periode.create(1.mars(2020), 30.september(2020)),
                beløp = tredje.beløp,
            ),
            UtbetalingslinjePåTidslinje.Stans(
                kopiertFraId = andreStans.id,
                opprettet = andreStans.opprettet,
                periode = Periode.create(1.oktober(2020), 31.oktober(2020)),
                beløp = 0,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = reaktivering.id,
                opprettet = reaktivering.opprettet,
                periode = Periode.create(1.november(2020), 31.desember(2020)),
                beløp = reaktivering.beløp,
            ),
        )
    }

    @Test
    fun `kan stanse tidligere reaktivert ytelse igjen`() {
        val første = Utbetalingslinje.Ny(
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
            uføregrad = Uføregrad.parse(50),
        )
        val førsteStans = Utbetalingslinje.Endring.Stans(
            utbetalingslinje = andre,
            virkningstidspunkt = 1.april(2020),
            clock = clock,
        )
        val reaktivering = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinje = førsteStans,
            virkningstidspunkt = 1.april(2020),
            clock = clock,
        )
        val andreStans = Utbetalingslinje.Endring.Stans(
            utbetalingslinje = reaktivering,
            virkningstidspunkt = 1.april(2020),
            clock = clock,
        )

        TidslinjeForUtbetalinger(
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            utbetalingslinjer = listOf(andre, første, førsteStans, andreStans, reaktivering),
        ).tidslinje shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,
                opprettet = første.opprettet,
                periode = Periode.create(1.januar(2020), 31.mars(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Stans(
                kopiertFraId = andreStans.id,
                opprettet = andreStans.opprettet,
                periode = Periode.create(1.april(2020), 31.desember(2020)),
                beløp = 0,
            ),
        )
    }

    @Test
    fun `kaster exception dersom regenerert informasjon interfererer med original informasjon som skulle vært ferskere`() {
        val førsteTidspunkt: Clock = Clock.fixed(
            1.januar(2021).atTime(1, 2, 3, 456789000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC,
        )
        val andreTidspunkt: Clock = Clock.fixed(
            1.januar(2021).atTime(1, 2, 3, 456790000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC,
        )
        val tredjeTidspunkt: Clock = Clock.fixed(
            1.januar(2021).atTime(1, 2, 3, 456791000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC,
        )
        val fjerdeTidspunkt: Clock = Clock.fixed(
            1.januar(2021).atTime(1, 2, 3, 456792000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC,
        )

        val første = Utbetalingslinje.Ny(
            opprettet = Tidspunkt.now(førsteTidspunkt),
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            opprettet = Tidspunkt.now(førsteTidspunkt),
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
            uføregrad = Uføregrad.parse(50),
        )
        val førsteStans = Utbetalingslinje.Endring.Stans(
            utbetalingslinje = andre,
            virkningstidspunkt = 1.april(2020),
            clock = andreTidspunkt,
        )
        val reaktivering = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinje = førsteStans,
            virkningstidspunkt = 1.april(2020),
            clock = tredjeTidspunkt,
        )
        val andreStans = Utbetalingslinje.Endring.Stans(
            utbetalingslinje = reaktivering,
            virkningstidspunkt = 1.april(2020),
            clock = fjerdeTidspunkt,
        )

        assertThrows<TidslinjeForUtbetalinger.RegenerertInformasjonVilOverskriveOriginaleOpplysningerSomErFerskereException> {
            TidslinjeForUtbetalinger(
                periode = Periode.create(1.januar(2020), 31.desember(2020)),
                utbetalingslinjer = listOf(andre, første, førsteStans, andreStans, reaktivering),
            ).tidslinje
        }
    }

    @Test
    fun `regenerert informasjon får samme opprettettidspunkt som ferskere informasjon, men perioden overlapper ikke`() {
        val førsteTidspunkt: Clock = Clock.fixed(
            1.januar(2021).atTime(1, 2, 3, 456789000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC,
        )
        val andreTidspunkt: Clock = Clock.fixed(
            1.januar(2021).atTime(1, 2, 3, 456790000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC,
        )
        val tredjeTidspunkt: Clock = Clock.fixed(
            1.januar(2021).atTime(1, 2, 3, 456791000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC,
        )
        val fjerdeTidspunkt: Clock = Clock.fixed(
            1.januar(2021).atTime(1, 2, 3, 456792000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC,
        )

        val første = Utbetalingslinje.Ny(
            opprettet = Tidspunkt.now(førsteTidspunkt),
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            opprettet = Tidspunkt.now(førsteTidspunkt),
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
            uføregrad = Uføregrad.parse(50),
        )
        val førsteStans = Utbetalingslinje.Endring.Stans(
            utbetalingslinje = andre,
            virkningstidspunkt = 1.april(2020),
            clock = andreTidspunkt,
        )
        val reaktivering = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinje = førsteStans,
            virkningstidspunkt = 1.april(2020),
            clock = tredjeTidspunkt,
        )
        val tredje = Utbetalingslinje.Ny(
            opprettet = Tidspunkt.now(fjerdeTidspunkt),
            fraOgMed = 1.januar(2021),
            tilOgMed = 31.desember(2021),
            forrigeUtbetalingslinjeId = reaktivering.id,
            beløp = 3000,
            uføregrad = Uføregrad.parse(50),
        )

        TidslinjeForUtbetalinger(
            periode = Periode.create(1.januar(2020), 31.desember(2021)),
            utbetalingslinjer = listOf(andre, første, førsteStans, tredje, reaktivering),
        ).tidslinje shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,
                opprettet = første.opprettet,
                periode = Periode.create(1.januar(2020), 31.mars(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = første.id,
                opprettet = tredje.opprettet,
                periode = Periode.create(1.april(2020), 30.april(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = andre.id,
                opprettet = reaktivering.opprettet,
                periode = Periode.create(1.mai(2020), 31.desember(2020)),
                beløp = andre.beløp,
            ),
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = tredje.id,
                opprettet = tredje.opprettet,
                periode = Periode.create(1.januar(2021), 31.desember(2021)),
                beløp = tredje.beløp,
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
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
            uføregrad = Uføregrad.parse(50),
        )
        val førsteStans = Utbetalingslinje.Endring.Stans(
            utbetalingslinje = andre,
            virkningstidspunkt = 1.mars(2020),
            clock = clock,
        )
        val førsteReaktivering = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinje = førsteStans,
            virkningstidspunkt = 1.mars(2020),
            clock = clock,
        )
        val andreStans = Utbetalingslinje.Endring.Stans(
            utbetalingslinje = førsteReaktivering,
            virkningstidspunkt = 1.oktober(2020),
            clock = clock,
        )
        val andreReaktivering = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinje = andreStans,
            virkningstidspunkt = 1.november(2020),
            clock = clock,
        )

        TidslinjeForUtbetalinger(
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            utbetalingslinjer = listOf(andre, andreReaktivering, førsteStans, førsteReaktivering, første, andreStans),
        ).tidslinje shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,
                opprettet = første.opprettet,
                periode = Periode.create(1.januar(2020), 29.februar(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = første.id,
                opprettet = førsteReaktivering.opprettet.plus(1, ChronoUnit.MICROS),
                periode = Periode.create(1.mars(2020), 30.april(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = andre.id,
                opprettet = førsteReaktivering.opprettet,
                periode = Periode.create(1.mai(2020), 30.september(2020)),
                beløp = andre.beløp,
            ),
            UtbetalingslinjePåTidslinje.Stans(
                kopiertFraId = andreStans.id,
                opprettet = andreStans.opprettet,
                periode = Periode.create(1.oktober(2020), 31.oktober(2020)),
                beløp = 0,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = andreReaktivering.id,
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
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
            uføregrad = Uføregrad.parse(50),
        )
        val opphør = Utbetalingslinje.Endring.Opphør(
            utbetalingslinje = andre,
            virkningstidspunkt = 1.mars(2020),
            clock = clock,
        )
        val reaktivering = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinje = opphør,
            virkningstidspunkt = 1.april(2020),
            clock = clock,
        )

        TidslinjeForUtbetalinger(
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            utbetalingslinjer = listOf(første, andre, opphør, reaktivering),
        ).tidslinje shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,
                opprettet = første.opprettet,
                periode = Periode.create(1.januar(2020), 29.februar(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Opphør(
                kopiertFraId = opphør.id,
                opprettet = opphør.opprettet,
                periode = Periode.create(1.mars(2020), 31.mars(2020)),
                beløp = 0,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = første.id,
                opprettet = reaktivering.opprettet.plus(1, ChronoUnit.MICROS),
                periode = Periode.create(1.april(2020), 30.april(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = andre.id,
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
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
            uføregrad = Uføregrad.parse(50),
        )
        val opphør = Utbetalingslinje.Endring.Opphør(
            utbetalingslinje = andre,
            virkningstidspunkt = 1.oktober(2020),
            clock = clock,
        )
        val tredje = Utbetalingslinje.Ny(
            fraOgMed = 1.oktober(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = opphør.id,
            beløp = 3000,
            uføregrad = Uføregrad.parse(50),
        )
        val stans = Utbetalingslinje.Endring.Stans(
            utbetalingslinje = tredje,
            virkningstidspunkt = 1.august(2020),
            clock = clock,
        )
        val reaktivering = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinje = stans,
            virkningstidspunkt = 1.august(2020),
            clock = clock,
        )

        TidslinjeForUtbetalinger(
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            utbetalingslinjer = listOf(første, andre, opphør, tredje, stans, reaktivering),
        ).tidslinje shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,
                opprettet = første.opprettet,
                periode = Periode.create(1.januar(2020), 30.april(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = andre.id,
                opprettet = andre.opprettet,
                periode = Periode.create(1.mai(2020), 31.juli(2020)),
                beløp = andre.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = andre.id,
                opprettet = reaktivering.opprettet.plus(1, ChronoUnit.MICROS),
                periode = Periode.create(1.august(2020), 30.september(2020)),
                beløp = andre.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = tredje.id,
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
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
            uføregrad = Uføregrad.parse(50),
        )
        val tredje = Utbetalingslinje.Ny(
            fraOgMed = 1.januar(2021),
            tilOgMed = 31.desember(2021),
            forrigeUtbetalingslinjeId = andre.id,
            beløp = 3000,
            uføregrad = Uføregrad.parse(50),
        )
        val fjerde = Utbetalingslinje.Ny(
            fraOgMed = 1.februar(2021),
            tilOgMed = 31.desember(2021),
            forrigeUtbetalingslinjeId = tredje.id,
            beløp = 4000,
            uføregrad = Uføregrad.parse(50),
        )
        val femte = Utbetalingslinje.Ny(
            fraOgMed = 1.mars(2021),
            tilOgMed = 31.desember(2021),
            forrigeUtbetalingslinjeId = fjerde.id,
            beløp = 5000,
            uføregrad = Uføregrad.parse(50),
        )

        TidslinjeForUtbetalinger(
            periode = Periode.create(1.januar(2020), 31.desember(2021)),
            utbetalingslinjer = listOf(andre, femte, tredje, første, fjerde),
        ).tidslinje shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,
                opprettet = første.opprettet,
                periode = Periode.create(1.januar(2020), 30.april(2020)),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = andre.id,
                opprettet = andre.opprettet,
                periode = Periode.create(1.mai(2020), 31.desember(2020)),
                beløp = andre.beløp,
            ),
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = tredje.id,
                opprettet = tredje.opprettet,
                periode = Periode.create(1.januar(2021), 31.januar(2021)),
                beløp = tredje.beløp,
            ),
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = fjerde.id,
                opprettet = fjerde.opprettet,
                periode = Periode.create(1.februar(2021), 28.februar(2021)),
                beløp = fjerde.beløp,
            ),
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = femte.id,
                opprettet = femte.opprettet,
                periode = Periode.create(1.mars(2021), 31.desember(2021)),
                beløp = femte.beløp,
            ),
        )
    }
}
