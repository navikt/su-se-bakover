package no.nav.su.se.bakover.domain.tidslinje

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.fixedClock
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.april
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.common.periode.november
import no.nav.su.se.bakover.common.periode.oktober
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.oppdrag.tidslinje
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.opphørtUtbetalingslinje
import no.nav.su.se.bakover.test.plus
import no.nav.su.se.bakover.test.utbetalingslinje
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.temporal.ChronoUnit

internal class TidslinjeForUtbetalingerTest {

    @Test
    fun `en utbetaling`() {
        val clock = TikkendeKlokke()
        val første = Utbetalingslinje.Ny(
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
        )
        TidslinjeForUtbetalinger(
            utbetalingslinjer = nonEmptyListOf(første),
        ) shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,
                opprettet = første.opprettet,
                periode = Periode.create(
                    1.januar(2020),
                    30.april(2020),
                ),
                beløp = første.beløp,
            ),
        )
    }

    @Test
    fun `et par helt ordinære utbetalinger`() {
        val clock = TikkendeKlokke()
        val førsteTidspunkt = Tidspunkt.now(clock)
        val første = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
            uføregrad = Uføregrad.parse(50),
        )

        TidslinjeForUtbetalinger(
            utbetalingslinjer = nonEmptyListOf(
                første,
                andre,
            ),
        ) shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,
                opprettet = første.opprettet,
                periode = Periode.create(
                    1.januar(2020),
                    30.april(2020),
                ),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = andre.id,
                opprettet = andre.opprettet,
                periode = Periode.create(
                    1.mai(2020),
                    31.desember(2020),
                ),
                beløp = andre.beløp,
            ),
        )
    }

    @Test
    fun `enkel stans på tvers av måneder med forskjellig beløp`() {
        val clock = TikkendeKlokke()
        val førsteTidspunkt = Tidspunkt.now(clock)
        val første = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
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
            utbetalingslinjer = nonEmptyListOf(
                første,
                andre,
                stans,
            ),
        ) shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,
                opprettet = første.opprettet,
                periode = Periode.create(
                    1.januar(2020),
                    31.mars(2020),
                ),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Stans(
                kopiertFraId = stans.id,
                opprettet = stans.opprettet,
                periode = Periode.create(
                    1.april(2020),
                    31.desember(2020),
                ),
                beløp = 0,
            ),
        )
    }

    @Test
    fun `reaktivering av enkel stans på tvers av måneder med forskjellig beløp`() {
        val clock = TikkendeKlokke()
        val førsteTidspunkt = Tidspunkt.now(clock)
        val første = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
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
            utbetalingslinjer = nonEmptyListOf(
                første,
                andre,
                stans,
                reaktivering,
            ),
        ) shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,
                opprettet = første.opprettet,
                periode = Periode.create(
                    1.januar(2020),
                    29.februar(2020),
                ),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Stans(
                kopiertFraId = stans.id,
                opprettet = stans.opprettet,
                periode = mars(2020),
                beløp = 0,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = første.id,
                opprettet = reaktivering.opprettet.plus(
                    1,
                    ChronoUnit.MICROS,
                ),
                periode = april(2020),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = reaktivering.id,
                opprettet = reaktivering.opprettet,
                periode = Periode.create(
                    1.mai(2020),
                    31.desember(2020),
                ),
                beløp = reaktivering.beløp,
            ),
        )
    }

    @Test
    fun `reaktivering av opphør på tvers av måneder med forskjellig beløp`() {
        val clock = TikkendeKlokke()
        val førsteTidspunkt = Tidspunkt.now(clock)
        val første = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
            uføregrad = Uføregrad.parse(50),
        )
        val opphør = Utbetalingslinje.Endring.Opphør(
            utbetalingslinje = andre,
            virkningsperiode = Periode.create(
                1.mars(2020),
                andre.periode.tilOgMed,
            ),
            clock = clock,
        )
        val reaktivering = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinje = opphør,
            virkningstidspunkt = 1.april(2020),
            clock = clock,
        )

        TidslinjeForUtbetalinger(
            utbetalingslinjer = nonEmptyListOf(
                første,
                andre,
                opphør,
                reaktivering,
            ),
        ) shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,
                opprettet = første.opprettet,
                periode = Periode.create(
                    1.januar(2020),
                    29.februar(2020),
                ),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Opphør(
                kopiertFraId = opphør.id,
                opprettet = opphør.opprettet,
                periode = mars(2020),
                beløp = 0,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = første.id,
                opprettet = reaktivering.opprettet.plus(
                    1,
                    ChronoUnit.MICROS,
                ),
                periode = april(2020),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = reaktivering.id,
                opprettet = reaktivering.opprettet,
                periode = Periode.create(
                    1.mai(2020),
                    31.desember(2020),
                ),
                beløp = reaktivering.beløp,
            ),
        )
    }

    @Test
    fun `opphør av alle utbetalingene`() {
        val clock = TikkendeKlokke()
        val førsteTidspunkt = Tidspunkt.now(clock)
        val første = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
            uføregrad = Uføregrad.parse(50),
        )
        val opphør = Utbetalingslinje.Endring.Opphør(
            utbetalingslinje = andre,
            virkningsperiode = Periode.create(
                1.januar(2020),
                andre.periode.tilOgMed,
            ),
            clock = clock,
        )

        TidslinjeForUtbetalinger(
            utbetalingslinjer = nonEmptyListOf(
                første,
                andre,
                opphør,
            ),
        ) shouldBe listOf(
            UtbetalingslinjePåTidslinje.Opphør(
                kopiertFraId = opphør.id,
                opprettet = opphør.opprettet,
                periode = år(2020),
                beløp = 0,
            ),
        )
    }

    @Test
    fun `reaktivering av ytelse som er opphørt`() {
        val clock = TikkendeKlokke()
        val førsteTidspunkt = Tidspunkt.now(clock)
        val første = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
            uføregrad = Uføregrad.parse(50),
        )
        val opphør = Utbetalingslinje.Endring.Opphør(
            utbetalingslinje = andre,
            virkningsperiode = Periode.create(
                1.januar(2020),
                andre.periode.tilOgMed,
            ),
            clock = clock,
        )
        val reaktivering = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinje = opphør,
            virkningstidspunkt = 1.januar(2020),
            clock = clock,
        )

        TidslinjeForUtbetalinger(
            utbetalingslinjer = nonEmptyListOf(
                første,
                andre,
                opphør,
                reaktivering,
            ),
        ) shouldBe listOf(
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = første.id,
                opprettet = reaktivering.opprettet.plus(
                    1,
                    ChronoUnit.MICROS,
                ),
                periode = Periode.create(
                    1.januar(2020),
                    30.april(2020),
                ),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = andre.id,
                opprettet = reaktivering.opprettet,
                periode = Periode.create(
                    1.mai(2020),
                    31.desember(2020),
                ),
                beløp = andre.beløp,
            ),
        )
    }

    @Test
    fun `reaktivering av ytelse som har blitt revurdert etter stans`() {
        val clock = TikkendeKlokke()
        val første = Utbetalingslinje.Ny(
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            opprettet = Tidspunkt.now(clock),
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
            opprettet = Tidspunkt.now(clock),
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
            utbetalingslinjer = nonEmptyListOf(
                andre,
                første,
                tredje,
                førsteStans,
                andreStans,
                reaktivering,
            ),
        ) shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,
                opprettet = første.opprettet,
                periode = Periode.create(
                    1.januar(2020),
                    29.februar(2020),
                ),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = tredje.id,
                opprettet = tredje.opprettet,
                periode = Periode.create(
                    1.mars(2020),
                    30.september(2020),
                ),
                beløp = tredje.beløp,
            ),
            UtbetalingslinjePåTidslinje.Stans(
                kopiertFraId = andreStans.id,
                opprettet = andreStans.opprettet,
                periode = oktober(2020),
                beløp = 0,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = reaktivering.id,
                opprettet = reaktivering.opprettet,
                periode = Periode.create(
                    1.november(2020),
                    31.desember(2020),
                ),
                beløp = reaktivering.beløp,
            ),
        )
    }

    @Test
    fun `kan stanse tidligere reaktivert ytelse igjen`() {
        val clock = TikkendeKlokke()
        val førsteTidspunkt = Tidspunkt.now(clock)
        val første = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
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
            utbetalingslinjer = nonEmptyListOf(
                andre,
                første,
                førsteStans,
                andreStans,
                reaktivering,
            ),
        ) shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,
                opprettet = første.opprettet,
                periode = Periode.create(
                    1.januar(2020),
                    31.mars(2020),
                ),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Stans(
                kopiertFraId = andreStans.id,
                opprettet = andreStans.opprettet,
                periode = Periode.create(
                    1.april(2020),
                    31.desember(2020),
                ),
                beløp = 0,
            ),
        )
    }

    @Test
    fun `kaster exception dersom regenerert informasjon interfererer med original informasjon som skulle vært ferskere`() {
        val clock = TikkendeKlokke()
        val førsteTidspunkt = Tidspunkt.now(clock)

        val første = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
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
        val (reaktivering, reaktiveringOpprettet) = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinje = førsteStans,
            virkningstidspunkt = 1.april(2020),
            clock = clock,
        ).let {
            it to it.opprettet
        }
        val andreStans = Utbetalingslinje.Endring.Stans(
            utbetalingslinje = reaktivering,
            virkningstidspunkt = 1.april(2020),
            clock = reaktiveringOpprettet.fixedClock()
                .plus(
                    1,
                    Tidspunkt.unit,
                ),
        )

        assertThrows<TidslinjeForUtbetalinger.RegenerertInformasjonVilOverskriveOriginaleOpplysningerSomErFerskereException> {
            TidslinjeForUtbetalinger(
                utbetalingslinjer = nonEmptyListOf(
                    andre,
                    første,
                    førsteStans,
                    andreStans,
                    reaktivering,
                ),
            )
        }
    }

    @Test
    fun `regenerert informasjon får samme opprettettidspunkt som ferskere informasjon, men perioden overlapper ikke`() {
        val clock = TikkendeKlokke()
        val førsteTidspunkt = Tidspunkt.now(clock)

        val første = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
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
        val (reaktivering, reaktiveringOpprettet) = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinje = førsteStans,
            virkningstidspunkt = 1.april(2020),
            clock = clock,
        ).let {
            it to it.opprettet
        }
        val tredje = Utbetalingslinje.Ny(
            opprettet = reaktiveringOpprettet.plusUnits(1),
            fraOgMed = 1.januar(2021),
            tilOgMed = 31.desember(2021),
            forrigeUtbetalingslinjeId = reaktivering.id,
            beløp = 3000,
            uføregrad = Uføregrad.parse(50),
        )

        TidslinjeForUtbetalinger(
            utbetalingslinjer = nonEmptyListOf(
                andre,
                første,
                førsteStans,
                tredje,
                reaktivering,
            ),
        ) shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,
                opprettet = første.opprettet,
                periode = Periode.create(
                    1.januar(2020),
                    31.mars(2020),
                ),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = første.id,
                opprettet = tredje.opprettet,
                periode = april(2020),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = andre.id,
                opprettet = reaktivering.opprettet,
                periode = Periode.create(
                    1.mai(2020),
                    31.desember(2020),
                ),
                beløp = andre.beløp,
            ),
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = tredje.id,
                opprettet = tredje.opprettet,
                periode = år(2021),
                beløp = tredje.beløp,
            ),
        )
    }

    @Test
    fun `reaktivering av tidligere stans og reaktiveringer på tvers av måneder med forskjellig beløp`() {
        val clock = TikkendeKlokke()
        val førsteTidspunkt = Tidspunkt.now(clock)
        val første = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
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
            utbetalingslinjer = nonEmptyListOf(
                andre,
                andreReaktivering,
                førsteStans,
                førsteReaktivering,
                første,
                andreStans,
            ),
        ) shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,
                opprettet = første.opprettet,
                periode = Periode.create(
                    1.januar(2020),
                    29.februar(2020),
                ),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = første.id,
                opprettet = førsteReaktivering.opprettet.plus(
                    1,
                    ChronoUnit.MICROS,
                ),
                periode = Periode.create(
                    1.mars(2020),
                    30.april(2020),
                ),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = andre.id,
                opprettet = førsteReaktivering.opprettet,
                periode = Periode.create(
                    1.mai(2020),
                    30.september(2020),
                ),
                beløp = andre.beløp,
            ),
            UtbetalingslinjePåTidslinje.Stans(
                kopiertFraId = andreStans.id,
                opprettet = andreStans.opprettet,
                periode = oktober(2020),
                beløp = 0,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = andreReaktivering.id,
                opprettet = andreReaktivering.opprettet,
                periode = Periode.create(
                    1.november(2020),
                    31.desember(2020),
                ),
                beløp = andreReaktivering.beløp,
            ),
        )
    }

    @Test
    fun `opphør av måned tilbake i tid med påfølgende reaktivering`() {
        val clock = TikkendeKlokke()
        val førsteTidspunkt = Tidspunkt.now(clock)
        val første = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
            uføregrad = Uføregrad.parse(50),
        )
        val opphør = Utbetalingslinje.Endring.Opphør(
            utbetalingslinje = andre,
            virkningsperiode = Periode.create(
                1.mars(2020),
                andre.periode.tilOgMed,
            ),
            clock = clock,
        )
        val reaktivering = Utbetalingslinje.Endring.Reaktivering(
            utbetalingslinje = opphør,
            virkningstidspunkt = 1.april(2020),
            clock = clock,
        )

        TidslinjeForUtbetalinger(
            utbetalingslinjer = nonEmptyListOf(
                første,
                andre,
                opphør,
                reaktivering,
            ),
        ) shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,
                opprettet = første.opprettet,
                periode = Periode.create(
                    1.januar(2020),
                    29.februar(2020),
                ),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Opphør(
                kopiertFraId = opphør.id,
                opprettet = opphør.opprettet,
                periode = mars(2020),
                beløp = 0,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = første.id,
                opprettet = reaktivering.opprettet.plus(
                    1,
                    ChronoUnit.MICROS,
                ),
                periode = april(2020),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = andre.id,
                opprettet = reaktivering.opprettet,
                periode = Periode.create(
                    1.mai(2020),
                    31.desember(2020),
                ),
                beløp = andre.beløp,
            ),
        )
    }

    @Test
    fun `blanding av alle utbetalingstypene`() {
        val clock = TikkendeKlokke()
        val førsteTidspunkt = Tidspunkt.now(clock)
        val første = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            opprettet = førsteTidspunkt,
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
            uføregrad = Uføregrad.parse(50),
        )
        val opphør = Utbetalingslinje.Endring.Opphør(
            utbetalingslinje = andre,
            virkningsperiode = Periode.create(
                1.oktober(2020),
                andre.periode.tilOgMed,
            ),
            clock = clock,
        )
        val tredje = Utbetalingslinje.Ny(
            opprettet = Tidspunkt.now(clock),
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
            utbetalingslinjer = nonEmptyListOf(
                første,
                andre,
                opphør,
                tredje,
                stans,
                reaktivering,
            ),
        ) shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,
                opprettet = første.opprettet,
                periode = Periode.create(
                    1.januar(2020),
                    30.april(2020),
                ),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = andre.id,
                opprettet = andre.opprettet,
                periode = Periode.create(
                    1.mai(2020),
                    31.juli(2020),
                ),
                beløp = andre.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = andre.id,
                opprettet = reaktivering.opprettet.plus(
                    1,
                    ChronoUnit.MICROS,
                ),
                periode = Periode.create(
                    1.august(2020),
                    30.september(2020),
                ),
                beløp = andre.beløp,
            ),
            UtbetalingslinjePåTidslinje.Reaktivering(
                kopiertFraId = tredje.id,
                opprettet = reaktivering.opprettet,
                periode = Periode.create(
                    1.oktober(2020),
                    31.desember(2020),
                ),
                beløp = tredje.beløp,
            ),
        )
    }

    @Test
    fun `helt ordinære utbetalinger - rekkefølgen på input har ikke noe å si`() {
        val clock = TikkendeKlokke()
        val første = Utbetalingslinje.Ny(
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
        )
        val andre = Utbetalingslinje.Ny(
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = første.id,
            beløp = 2000,
            uføregrad = Uføregrad.parse(50),
        )
        val tredje = Utbetalingslinje.Ny(
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.januar(2021),
            tilOgMed = 31.desember(2021),
            forrigeUtbetalingslinjeId = andre.id,
            beløp = 3000,
            uføregrad = Uføregrad.parse(50),
        )
        val fjerde = Utbetalingslinje.Ny(
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.februar(2021),
            tilOgMed = 31.desember(2021),
            forrigeUtbetalingslinjeId = tredje.id,
            beløp = 4000,
            uføregrad = Uføregrad.parse(50),
        )
        val femte = Utbetalingslinje.Ny(
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.mars(2021),
            tilOgMed = 31.desember(2021),
            forrigeUtbetalingslinjeId = fjerde.id,
            beløp = 5000,
            uføregrad = Uføregrad.parse(50),
        )

        TidslinjeForUtbetalinger(
            utbetalingslinjer = nonEmptyListOf(
                andre,
                femte,
                tredje,
                første,
                fjerde,
            ),
        ) shouldBe listOf(
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = første.id,
                opprettet = første.opprettet,
                periode = Periode.create(
                    1.januar(2020),
                    30.april(2020),
                ),
                beløp = første.beløp,
            ),
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = andre.id,
                opprettet = andre.opprettet,
                periode = Periode.create(
                    1.mai(2020),
                    31.desember(2020),
                ),
                beløp = andre.beløp,
            ),
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = tredje.id,
                opprettet = tredje.opprettet,
                periode = januar(2021),
                beløp = tredje.beløp,
            ),
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = fjerde.id,
                opprettet = fjerde.opprettet,
                periode = februar(2021),
                beløp = fjerde.beløp,
            ),
            UtbetalingslinjePåTidslinje.Ny(
                kopiertFraId = femte.id,
                opprettet = femte.opprettet,
                periode = Periode.create(
                    1.mars(2021),
                    31.desember(2021),
                ),
                beløp = femte.beløp,
            ),
        )
    }

    @Test
    fun `tidslinje er evkvialent med seg selv `() {
        val clock = TikkendeKlokke()
        val ny = Utbetalingslinje.Ny(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
        )
        listOf(ny).tidslinje().getOrFail().ekvivalentMed(listOf(ny).tidslinje().getOrFail()) shouldBe true
        listOf(
            ny,
            ny.copy(
                opprettet = ny.opprettet.plusUnits(1),
            ),
        ).tidslinje().getOrFail().ekvivalentMed(
            listOf(
                ny,
                ny.copy(
                    opprettet = ny.opprettet.plusUnits(1),
                ),
            ).tidslinje().getOrFail(),
        ) shouldBe true

        listOf(ny).tidslinje().getOrFail().ekvivalentMed(
            listOf(
                ny,
                ny.copy(
                    opprettet = ny.opprettet.plusUnits(1),
                ),
            ).tidslinje().getOrFail(),
        ) shouldBe true
    }

    @Test
    fun `tidslinje er evkvialent - lik periode og beløp `() {
        val clock = TikkendeKlokke()
        val a = Utbetalingslinje.Ny(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
        )
        val b = Utbetalingslinje.Ny(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = UUID30.randomUUID(),
            beløp = 1000,
            uføregrad = Uføregrad.parse(100),
        )
        listOf(a).tidslinje().getOrFail().ekvivalentMed(listOf(b).tidslinje().getOrFail()) shouldBe true
    }

    @Test
    fun `tidslinje er ikke evkvialent - forskjellig periode og beløp`() {
        val clock = TikkendeKlokke()
        val a = Utbetalingslinje.Ny(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
        )
        val b = Utbetalingslinje.Ny(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.februar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = UUID30.randomUUID(),
            beløp = 1000,
            uføregrad = Uføregrad.parse(100),
        )
        val bb = Utbetalingslinje.Ny(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 5000,
            uføregrad = Uføregrad.parse(50),
        )
        listOf(a).tidslinje().getOrFail().ekvivalentMed(listOf(b).tidslinje().getOrFail()) shouldBe false
        listOf(a).tidslinje().getOrFail().ekvivalentMed(listOf(bb).tidslinje().getOrFail()) shouldBe false
    }

    @Test
    fun `rest kombinasjoner`() {
        val clock = TikkendeKlokke()
        val a = Utbetalingslinje.Ny(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
        )
        val b = Utbetalingslinje.Ny(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.januar(2020),
            tilOgMed = 30.april(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 1000,
            uføregrad = Uføregrad.parse(50),
        )
        val c = Utbetalingslinje.Ny(
            id = UUID30.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.desember(2020),
            forrigeUtbetalingslinjeId = null,
            beløp = 5000,
            uføregrad = Uføregrad.parse(50),
        )
        listOf(a).tidslinje().getOrFail().ekvivalentMed(listOf(b).tidslinje().getOrFail()) shouldBe true
        listOf(b).tidslinje().getOrFail().ekvivalentMed(listOf(a).tidslinje().getOrFail()) shouldBe true

        listOf(a).tidslinje().getOrFail().ekvivalentMed(listOf(c).tidslinje().getOrFail()) shouldBe false
        listOf(c).tidslinje().getOrFail().ekvivalentMed(listOf(a).tidslinje().getOrFail()) shouldBe false

        listOf(b).tidslinje().getOrFail().ekvivalentMed(listOf(c).tidslinje().getOrFail()) shouldBe false
        listOf(c).tidslinje().getOrFail().ekvivalentMed(listOf(b).tidslinje().getOrFail()) shouldBe false

        listOf(a, b).tidslinje().getOrFail().ekvivalentMed(listOf(c).tidslinje().getOrFail()) shouldBe false
        listOf(c).tidslinje().getOrFail().ekvivalentMed(listOf(a, b).tidslinje().getOrFail()) shouldBe false

        listOf(b, c).tidslinje().getOrFail().ekvivalentMed(listOf(a).tidslinje().getOrFail()) shouldBe false
        listOf(a).tidslinje().getOrFail().ekvivalentMed(listOf(b, c).tidslinje().getOrFail()) shouldBe false

        listOf(a, c).tidslinje().getOrFail().ekvivalentMed(listOf(b).tidslinje().getOrFail()) shouldBe false
        listOf(b).tidslinje().getOrFail().ekvivalentMed(listOf(a, c).tidslinje().getOrFail()) shouldBe false
    }

    @Test
    fun `periode som er innenfor tidslinjensperiode blir krympet ok`() {
        TidslinjeForUtbetalinger(nonEmptyListOf(utbetalingslinje())).let {
            it.periode shouldBe år(2021)
            it.krympTilPeriode(mai(2021)..november(2021))!!.ekvivalentMed(
                TidslinjeForUtbetalinger(nonEmptyListOf(utbetalingslinje(periode = mai(2021)..november(2021)))),
            ) shouldBe true
        }

        TidslinjeForUtbetalinger(nonEmptyListOf(utbetalingslinje())).let {
            it.periode shouldBe år(2021)
            it.krympTilPeriode(1.mai(2021))!!.ekvivalentMed(
                TidslinjeForUtbetalinger(nonEmptyListOf(utbetalingslinje(periode = mai(2021)..desember(2021)))),
            ) shouldBe true
        }
    }

    @Test
    fun `tidslinjens periode er ok dersom man kryper til en periode som er større`() {
        val expectedPeriode = mai(2021)..desember(2021)
        TidslinjeForUtbetalinger(nonEmptyListOf(utbetalingslinje(periode = expectedPeriode))).let {
            it.periode shouldBe expectedPeriode
            it.krympTilPeriode(år(2021))!!.ekvivalentMed(
                TidslinjeForUtbetalinger(nonEmptyListOf(utbetalingslinje(periode = expectedPeriode))),
            ) shouldBe true
        }
    }

    @Test
    fun `gir null dersom perioden som skal bli krympet til, ikke er i tidslinjens periode`() {
        TidslinjeForUtbetalinger(nonEmptyListOf(utbetalingslinje())).let {
            it.periode shouldBe år(2021)
            it.krympTilPeriode(mai(2022)..november(2022)) shouldBe null
        }
    }

    @Test
    fun `ekvivalent innenfor periode med seg selv`() {
        val expectedPeriode = år(2022)
        val tidslinje = TidslinjeForUtbetalinger(nonEmptyListOf(utbetalingslinje(periode = expectedPeriode)))
        tidslinje.ekvivalentMedInnenforPeriode(tidslinje, expectedPeriode) shouldBe true
    }

    @Test
    fun `ekvivalent innenfor periode der 2 tidslinjer er ulik`() {
        val tidslinje1 = TidslinjeForUtbetalinger(nonEmptyListOf(utbetalingslinje(periode = år(2022))))
        val tidslinje2 = TidslinjeForUtbetalinger(
            nonEmptyListOf(
                opphørtUtbetalingslinje(periode = januar(2022)..mai(2022)),
                utbetalingslinje(periode = juni(2022)..desember(2022)),
            ),
        )
        tidslinje1.ekvivalentMedInnenforPeriode(tidslinje2, juni(2022)..desember(2022)) shouldBe true
    }

    @Test
    fun `false selv om 2 ulike tidslinjer er ekvivalente, men perioden som sjekkes for har ikke noe relevans`() {
        val tidslinje1 = TidslinjeForUtbetalinger(nonEmptyListOf(utbetalingslinje(periode = år(2022))))
        val tidslinje2 = TidslinjeForUtbetalinger(
            nonEmptyListOf(
                opphørtUtbetalingslinje(periode = januar(2022)..mai(2022)),
                utbetalingslinje(periode = juni(2022)..desember(2022)),
            ),
        )
        tidslinje1.ekvivalentMedInnenforPeriode(tidslinje2, juni(2023)..desember(2023)) shouldBe false
    }
}
