package no.nav.su.se.bakover.domain.tidslinje

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit.DAYS

private data class Tidslinjeobjekt(
    override val opprettet: Tidspunkt,
    private val periode: Periode
) : KanPlasseresPåTidslinje<Tidslinjeobjekt> {
    override fun getPeriode(): Periode = periode
    override fun copy(args: CopyArgs.Tidslinje): Tidslinjeobjekt = when (args) {
        CopyArgs.Tidslinje.Full -> this.copy()
        is CopyArgs.Tidslinje.NyPeriode -> this.copy(periode = args.periode)
    }
}

internal class TidslinjeTest {
    private val fixedClock: Clock = Clock.fixed(1.januar(2021).startOfDay().instant, ZoneOffset.UTC)

    /**
     *  |---| a
     *  |---| b
     *  |---| resultat
     */
    @Test
    fun `ny overlapper gammel fullstendig`() {
        val a = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock),
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021)
            )
        )

        val b = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock).plus(1, DAYS),
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021)
            )
        )

        Tidslinje(
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021)
            ),
            objekter = listOf(a, b),
            clock = fixedClock
        ).tidslinje shouldBe listOf(
            Tidslinjeobjekt(
                opprettet = b.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.desember(2021)
                )
            )
        )
    }

    /**
     *  |---|     a
     *      |---| b
     *  |---|---| resultat
     */
    @Test
    fun `kombinerer gammel og ny som ikke overlapper `() {
        val a = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock),
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 30.april(2021)
            )
        )

        val b = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock).plus(1, DAYS),
            periode = Periode.create(
                fraOgMed = 1.mai(2021),
                tilOgMed = 31.desember(2021)
            )
        )

        Tidslinje(
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021)
            ),
            objekter = listOf(b, a),
            clock = fixedClock
        ).tidslinje shouldBe listOf(
            Tidslinjeobjekt(
                opprettet = a.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 30.april(2021)
                )
            ),
            Tidslinjeobjekt(
                opprettet = b.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.mai(2021),
                    tilOgMed = 31.desember(2021)
                )
            )
        )
    }

    /**
     *      |---| a
     *  |---|     b
     *  |---|---| resultat
     */
    @Test
    fun `kombinerer gammel og ny som ikke overlapper 2`() {
        val a = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock),
            periode = Periode.create(
                fraOgMed = 1.mai(2021),
                tilOgMed = 31.desember(2021)
            )
        )

        val b = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock).plus(1, DAYS),
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 30.april(2021)
            )
        )

        Tidslinje(
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021)
            ),
            objekter = listOf(a, b),
            clock = fixedClock
        ).tidslinje shouldBe listOf(
            Tidslinjeobjekt(
                opprettet = b.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 30.april(2021)
                )
            ),
            Tidslinjeobjekt(
                opprettet = a.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.mai(2021),
                    tilOgMed = 31.desember(2021)
                )
            )
        )
    }

    /**
     *  |---|   a
     *    |---| b
     *  |-|---| resultat
     */
    @Test
    fun `kombinerer gammel og ny som overlapper delvis framover i tid`() {
        val a = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock),
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.juli(2021)
            )
        )

        val b = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock).plus(1, DAYS),
            periode = Periode.create(
                fraOgMed = 1.april(2021),
                tilOgMed = 31.desember(2021)
            )
        )

        Tidslinje(
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021)
            ),
            objekter = listOf(a, b),
            clock = fixedClock
        ).tidslinje shouldBe listOf(
            Tidslinjeobjekt(
                opprettet = a.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.mars(2021)
                )
            ),
            Tidslinjeobjekt(
                opprettet = b.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.april(2021),
                    tilOgMed = 31.desember(2021)
                )
            )
        )
    }

    /**
     *    |---| a
     *  |---|   b
     *  |---|-| resultat
     */
    @Test
    fun `kombinerer gammel og ny som overlapper delvis bakover tid`() {
        val a = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock),
            periode = Periode.create(
                fraOgMed = 1.mars(2021),
                tilOgMed = 31.desember(2021)
            )
        )

        val b = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock).plus(1, DAYS),
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 30.april(2021)
            )
        )

        Tidslinje(
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021)
            ),
            objekter = listOf(a, b),
            clock = fixedClock
        ).tidslinje shouldBe listOf(
            Tidslinjeobjekt(
                opprettet = b.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 30.april(2021)
                )
            ),
            Tidslinjeobjekt(
                opprettet = a.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.mai(2021),
                    tilOgMed = 31.desember(2021)
                )
            )
        )
    }

    /**
     *  |---| a
     *  |-|   b
     *  |-|-| resultat
     */
    @Test
    fun `kombinerer gammel og ny som overlapper delvis bakover tid 2`() {
        val a = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock),
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021)
            )
        )

        val b = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock).plus(1, DAYS),
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.mai(2021)
            )
        )

        Tidslinje(
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021)
            ),
            objekter = listOf(b, a),
            clock = fixedClock
        ).tidslinje shouldBe listOf(
            Tidslinjeobjekt(
                opprettet = b.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.mai(2021)
                )
            ),
            Tidslinjeobjekt(
                opprettet = a.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.juni(2021),
                    tilOgMed = 31.desember(2021)
                )
            )
        )
    }

    /**
     *  |---|  a
     * |-|     b
     *     |-| c
     * |-|-|-| resultat
     */
    @Test
    fun `kombinerer gammel og ny som overlapper delvis bakover og framover i tid`() {
        val a = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock),
            periode = Periode.create(
                fraOgMed = 1.februar(2021),
                tilOgMed = 30.november(2021)
            )
        )

        val b = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock).plus(1, DAYS),
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 28.februar(2021)
            )
        )

        val c = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock).plus(2, DAYS),
            periode = Periode.create(
                fraOgMed = 1.oktober(2021),
                tilOgMed = 31.desember(2021)
            )
        )

        Tidslinje(
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021)
            ),
            objekter = listOf(b, c, a),
            clock = fixedClock
        ).tidslinje shouldBe listOf(
            Tidslinjeobjekt(
                opprettet = b.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 28.februar(2021)
                )
            ),
            Tidslinjeobjekt(
                opprettet = a.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.mars(2021),
                    tilOgMed = 30.september(2021)
                )
            ),
            Tidslinjeobjekt(
                opprettet = c.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.oktober(2021),
                    tilOgMed = 31.desember(2021)
                )
            )
        )
    }

    /**
     *  |-----| a
     *    |-|   b
     *  |-|-|-| resultat
     */
    @Test
    fun `gammel overlapper ny fullstendig`() {
        val a = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock),
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021)
            )
        )

        val b = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock).plus(1, DAYS),
            periode = Periode.create(
                fraOgMed = 1.april(2021),
                tilOgMed = 31.mai(2021)
            )
        )

        Tidslinje(
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021)
            ),
            objekter = listOf(a, b),
            clock = fixedClock
        ).tidslinje shouldBe listOf(
            Tidslinjeobjekt(
                opprettet = a.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.mars(2021)
                )
            ),
            Tidslinjeobjekt(
                opprettet = b.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.april(2021),
                    tilOgMed = 31.mai(2021)
                )
            ),
            Tidslinjeobjekt(
                opprettet = a.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.juni(2021),
                    tilOgMed = 31.desember(2021)
                )
            )
        )
    }

    /**
     *  |--------| a
     *      |-|    b
     *        |--| c
     *    |---|    d
     *  |-|---|--| resultat
     */
    @Test
    fun `potpurri 1`() {
        val a = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock),
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021)
            )
        )

        val b = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock).plus(1, DAYS),
            periode = Periode.create(
                fraOgMed = 1.juni(2021),
                tilOgMed = 30.juni(2021)
            )
        )

        val c = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock).plus(2, DAYS),
            periode = Periode.create(
                fraOgMed = 1.juli(2021),
                tilOgMed = 31.desember(2021)
            )
        )

        val d = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock).plus(3, DAYS),
            periode = Periode.create(
                fraOgMed = 1.mars(2021),
                tilOgMed = 30.juni(2021)
            )
        )

        Tidslinje(
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021)
            ),
            objekter = listOf(a, d, c, b),
            clock = fixedClock
        ).tidslinje shouldBe listOf(
            Tidslinjeobjekt(
                opprettet = a.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 28.februar(2021)
                )
            ),
            Tidslinjeobjekt(
                opprettet = d.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.mars(2021),
                    tilOgMed = 30.juni(2021)
                )
            ),
            Tidslinjeobjekt(
                opprettet = c.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.juli(2021),
                    tilOgMed = 31.desember(2021)
                )
            )
        )
    }

    /**
     *  |--------| a
     *    |-|      b
     *      |-|    c
     *        |--| d
     *  |-|-|-|--| resultat
     */
    @Test
    fun `potpurri 2`() {
        val a = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock),
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021)
            )
        )

        val b = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock).plus(1, DAYS),
            periode = Periode.create(
                fraOgMed = 1.mars(2021),
                tilOgMed = 30.april(2021)
            )
        )

        val c = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock).plus(2, DAYS),
            periode = Periode.create(
                fraOgMed = 1.mai(2021),
                tilOgMed = 31.juli(2021)
            )
        )

        val d = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock).plus(3, DAYS),
            periode = Periode.create(
                fraOgMed = 1.august(2021),
                tilOgMed = 31.desember(2021)
            )
        )

        Tidslinje(
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021)
            ),
            objekter = listOf(b, a, c, d),
            clock = fixedClock
        ).tidslinje shouldBe listOf(
            Tidslinjeobjekt(
                opprettet = a.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 28.februar(2021)
                )
            ),
            Tidslinjeobjekt(
                opprettet = b.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.mars(2021),
                    tilOgMed = 30.april(2021)
                )
            ),
            Tidslinjeobjekt(
                opprettet = c.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.mai(2021),
                    tilOgMed = 31.juli(2021)
                )
            ),
            Tidslinjeobjekt(
                opprettet = d.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.august(2021),
                    tilOgMed = 31.desember(2021)
                )
            )
        )
    }

    /**
     *  |-----| a
     *  |-|     b
     *      |-| c
     *  |-|-|-| resultat
     */
    @Test
    fun `kombinerer gammel og ny hvor ny verken overlapper eller tilstøter`() {
        val a = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock),
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021)
            )
        )

        val b = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock).plus(1, DAYS),
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.mars(2021)
            )
        )

        val c = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock).plus(2, DAYS),
            periode = Periode.create(
                fraOgMed = 1.juli(2021),
                tilOgMed = 31.desember(2021)
            )
        )

        Tidslinje(
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021)
            ),
            objekter = listOf(a, b, c),
            clock = fixedClock
        ).tidslinje shouldBe listOf(
            Tidslinjeobjekt(
                opprettet = b.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.mars(2021)
                )
            ),
            Tidslinjeobjekt(
                opprettet = a.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.april(2021),
                    tilOgMed = 30.juni(2021)
                )
            ),
            Tidslinjeobjekt(
                opprettet = c.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.juli(2021),
                    tilOgMed = 31.desember(2021)
                )
            )
        )
    }

    /**
     *  |-|      a
     *       |-| b
     *  |-|  |-| resultat
     */
    @Test
    fun `kombinerer gammel og ny som er fullstendig adskilt i tid`() {
        val a = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock),
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.mars(2021)
            )
        )

        val b = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock).plus(1, DAYS),
            periode = Periode.create(
                fraOgMed = 1.juli(2021),
                tilOgMed = 31.desember(2021)
            )
        )

        Tidslinje(
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021)
            ),
            objekter = listOf(b, a),
            clock = fixedClock
        ).tidslinje shouldBe listOf(
            Tidslinjeobjekt(
                opprettet = a.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.mars(2021)
                )
            ),
            Tidslinjeobjekt(
                opprettet = b.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.juli(2021),
                    tilOgMed = 31.desember(2021)
                )
            )
        )
    }

    /**
     *  |------| a
     *    |----| b
     *      |--| c
     * |-------| d
     * |-------| resultat
     */
    @Test
    fun `overskriver gamle med nyere`() {
        val a = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock),
            periode = Periode.create(
                fraOgMed = 1.februar(2021),
                tilOgMed = 31.desember(2021)
            )
        )

        val b = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock).plus(1, DAYS),
            periode = Periode.create(
                fraOgMed = 1.mars(2021),
                tilOgMed = 31.desember(2021)
            )
        )

        val c = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock).plus(2, DAYS),
            periode = Periode.create(
                fraOgMed = 1.juli(2021),
                tilOgMed = 31.desember(2021)
            )
        )

        val d = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock).plus(3, DAYS),
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021)
            )
        )

        Tidslinje(
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021)
            ),
            objekter = listOf(c, a, b, d),
            clock = fixedClock
        ).tidslinje shouldBe listOf(
            Tidslinjeobjekt(
                opprettet = d.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.desember(2021)
                )
            )
        )
    }

    /**
     *  |--|    a
     * |----|   b
     *    |---| c
     *   |--|   d
     * |-|--|-| resultat
     */
    @Test
    fun `potpurri 3`() {
        val a = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock),
            periode = Periode.create(
                fraOgMed = 1.februar(2021),
                tilOgMed = 31.mai(2021)
            )
        )

        val b = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock).plus(1, DAYS),
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 30.juni(2021)
            )
        )

        val c = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock).plus(2, DAYS),
            periode = Periode.create(
                fraOgMed = 1.juni(2021),
                tilOgMed = 31.desember(2021)
            )
        )

        val d = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock).plus(3, DAYS),
            periode = Periode.create(
                fraOgMed = 1.april(2021),
                tilOgMed = 30.juni(2021)
            )
        )

        Tidslinje(
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021)
            ),
            objekter = listOf(a, b, c, d),
            clock = fixedClock
        ).tidslinje shouldBe listOf(
            Tidslinjeobjekt(
                opprettet = b.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.mars(2021)
                )
            ),
            Tidslinjeobjekt(
                opprettet = d.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.april(2021),
                    tilOgMed = 30.juni(2021)
                )
            ),
            Tidslinjeobjekt(
                opprettet = c.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.juli(2021),
                    tilOgMed = 31.desember(2021)
                )
            )
        )
    }

    /**
     * |-------|   a
     *   |-------| b
     *     |--|    c
     * |-|-|--|--| resultat
     */
    @Test
    fun `potpurri 4`() {
        val a = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock),
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.oktober(2021)
            )
        )

        val b = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock).plus(1, DAYS),
            periode = Periode.create(
                fraOgMed = 1.mars(2021),
                tilOgMed = 31.desember(2021)
            )
        )

        val c = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock).plus(2, DAYS),
            periode = Periode.create(
                fraOgMed = 1.juni(2021),
                tilOgMed = 31.juli(2021)
            )
        )

        Tidslinje(
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021)
            ),
            objekter = listOf(a, b, c),
            clock = fixedClock
        ).tidslinje shouldBe listOf(
            Tidslinjeobjekt(
                opprettet = a.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 28.februar(2021)
                )
            ),
            Tidslinjeobjekt(
                opprettet = b.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.mars(2021),
                    tilOgMed = 31.mai(2021)
                )
            ),
            Tidslinjeobjekt(
                opprettet = c.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.juni(2021),
                    tilOgMed = 31.juli(2021)
                )
            ),
            Tidslinjeobjekt(
                opprettet = b.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.august(2021),
                    tilOgMed = 31.desember(2021)
                )
            )
        )
    }

    @Test
    fun `kan lage tidslinje for forskjellige typer objekter`() {
        val a = Grunnlag.Uføregrunnlag(
            opprettet = Tidspunkt.now(fixedClock),
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021)
            ),
            uføregrad = Uføregrad.parse(50),
            forventetInntekt = 15000
        )

        val b = Grunnlag.Uføregrunnlag(
            opprettet = Tidspunkt.now(fixedClock).plus(1, DAYS),
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021)
            ),
            uføregrad = Uføregrad.parse(100),
            forventetInntekt = 0
        )

        Tidslinje<Grunnlag.Uføregrunnlag>(
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021)
            ),
            objekter = listOf(a, b),
            clock = fixedClock
        ).tidslinje.let {
            it shouldHaveSize 1
            it[0].let { resultat ->
                resultat.id shouldNotBe a.id
                resultat.id shouldNotBe b.id
                resultat.getPeriode() shouldBe Periode.create(
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.desember(2021)
                )
                resultat.uføregrad shouldBe Uføregrad.parse(100)
                resultat.forventetInntekt shouldBe 0
            }
        }
    }

    @Test
    fun `returnerer tom liste hvis ingen elementer sendes inn`() {
        Tidslinje<Tidslinjeobjekt>(
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021)
            ),
            objekter = emptyList(),
            clock = fixedClock
        ).tidslinje shouldBe emptyList()
    }

    /**
     *  |-----| a
     *    |-|   b
     *  |-|-|-| resultat
     */
    @Test
    fun `justerer tidslinjen i forhold til perioden som etterspørres`() {
        val a = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock),
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.desember(2021)
            )
        )

        val b = Tidslinjeobjekt(
            opprettet = Tidspunkt.now(fixedClock).plus(1, DAYS),
            periode = Periode.create(
                fraOgMed = 1.april(2021),
                tilOgMed = 31.mai(2021)
            )
        )

        /** |-|     periode
         *  |-----| a
         *    |-|   b
         *  |-|     resultat
         */
        Tidslinje(
            periode = Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.mars(2021)
            ),
            objekter = listOf(a, b),
            clock = fixedClock
        ).tidslinje shouldBe listOf(
            Tidslinjeobjekt(
                opprettet = a.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.mars(2021)
                )
            )
        )

        /**   |-|   periode
         *  |-----| a
         *    |-|   b
         *    |-|   resultat
         */
        Tidslinje(
            periode = Periode.create(
                fraOgMed = 1.april(2021),
                tilOgMed = 31.mai(2021)
            ),
            objekter = listOf(a, b),
            clock = fixedClock
        ).tidslinje shouldBe listOf(
            Tidslinjeobjekt(
                opprettet = b.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.april(2021),
                    tilOgMed = 31.mai(2021)
                )
            )
        )

        /**   |---| periode
         *  |-----| a
         *    |-|   b
         *    |-|-| resultat
         */
        Tidslinje(
            periode = Periode.create(
                fraOgMed = 1.april(2021),
                tilOgMed = 31.desember(2021)
            ),
            objekter = listOf(a, b),
            clock = fixedClock
        ).tidslinje shouldBe listOf(
            Tidslinjeobjekt(
                opprettet = b.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.april(2021),
                    tilOgMed = 31.mai(2021)
                )
            ),
            Tidslinjeobjekt(
                opprettet = a.opprettet,
                periode = Periode.create(
                    fraOgMed = 1.juni(2021),
                    tilOgMed = 31.desember(2021)
                )
            )
        )

        /**        |-| periode
         *  |-----| a
         *    |-|   b
         */
        Tidslinje(
            periode = Periode.create(
                fraOgMed = 1.januar(2022),
                tilOgMed = 31.desember(2022)
            ),
            objekter = listOf(a, b),
            clock = fixedClock
        ).tidslinje shouldBe emptyList()
    }

    // TODO lag et psycho ekempel
    @Test
    fun `potpurri of potpurris`() {
    }
}
