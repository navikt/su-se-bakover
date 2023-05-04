package no.nav.su.se.bakover.domain.tidslinje

import arrow.core.nonEmptyListOf
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.application.CopyArgs
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.april
import no.nav.su.se.bakover.common.periode.august
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.juli
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.common.periode.november
import no.nav.su.se.bakover.common.periode.oktober
import no.nav.su.se.bakover.common.periode.september
import no.nav.su.se.bakover.common.periode.tilMåned
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje.Companion.Validator
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje.Companion.lagTidslinje
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClock
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.Month
import java.time.YearMonth

private data class Tidslinjeobjekt(
    override val opprettet: Tidspunkt,
    override val periode: Periode,
) : KanPlasseresPåTidslinje<Tidslinjeobjekt> {
    override fun copy(args: CopyArgs.Tidslinje): Tidslinjeobjekt = when (args) {
        CopyArgs.Tidslinje.Full -> this.copy()
        is CopyArgs.Tidslinje.NyPeriode -> this.copy(periode = args.periode)
    }

    fun fjernPeriode(): List<Tidslinjeobjekt> = fjernPerioder(listOf(periode))
}

internal class TidslinjeTest {
    private val tikkendeKlokke = TikkendeKlokke(fixedClock)

    /**
     *  |---| a
     *  |---| b
     *  |---| resultat
     */
    @Test
    fun `ny overlapper gammel fullstendig`() {
        val a = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), år(2021))
        val b = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), år(2021))

        listOf(a, b).lagTidslinje()!!.krympTilPeriode(år(2021))?.let {
            val expected = Tidslinjeobjekt(b.opprettet, år(2021))
            it shouldBe listOf(expected)

            it.gjeldendeForDato(1.januar(2021)) shouldBe expected
            it.gjeldendeForDato(31.juli(2021)) shouldBe expected
            it.gjeldendeForDato(31.desember(2021)) shouldBe expected
            it.gjeldendeForDato(1.januar(2020)) shouldBe null
            it.gjeldendeForDato(31.desember(2020)) shouldBe null
        }
    }

    /**
     *  |---|     a
     *      |---| b
     *  |---|---| resultat
     */
    @Test
    fun `kombinerer gammel og ny som ikke overlapper `() {
        val a = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), januar(2021)..april(2021))
        val b = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), mai(2021)..desember(2021))

        listOf(a, b).lagTidslinje()!!.krympTilPeriode(år(2021)) shouldBe listOf(
            Tidslinjeobjekt(a.opprettet, januar(2021)..april(2021)),
            Tidslinjeobjekt(b.opprettet, mai(2021)..desember(2021)),
        )
    }

    /**
     *      |---| a
     *  |---|     b
     *  |---|---| resultat
     */
    @Test
    fun `kombinerer gammel og ny som ikke overlapper 2`() {
        val a = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), mai(2021)..desember(2021))
        val b = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), januar(2021)..april(2021))

        listOf(a, b).lagTidslinje()!!.krympTilPeriode(år(2021)) shouldBe listOf(
            Tidslinjeobjekt(b.opprettet, januar(2021)..april(2021)),
            Tidslinjeobjekt(a.opprettet, mai(2021)..desember(2021)),
        )
    }

    /**
     *  |---|   a
     *    |---| b
     *  |-|---| resultat
     */
    @Test
    fun `kombinerer gammel og ny som overlapper delvis framover i tid`() {
        val a = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), januar(2021)..juli(2021))
        val b = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), april(2021)..desember(2021))

        listOf(a, b).lagTidslinje()!!.krympTilPeriode(år(2021)) shouldBe listOf(
            Tidslinjeobjekt(a.opprettet, januar(2021)..mars(2021)),
            Tidslinjeobjekt(b.opprettet, april(2021)..desember(2021)),
        )
    }

    /**
     *    |---| a
     *  |---|   b
     *  |---|-| resultat
     */
    @Test
    fun `kombinerer gammel og ny som overlapper delvis bakover tid`() {
        val a = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), mars(2021)..desember(2021))
        val b = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), januar(2021)..april(2021))

        listOf(a, b).lagTidslinje()!!.krympTilPeriode(år(2021)) shouldBe listOf(
            Tidslinjeobjekt(b.opprettet, januar(2021)..april(2021)),
            Tidslinjeobjekt(a.opprettet, mai(2021)..desember(2021)),
        )
    }

    /**
     *  |---| a
     *  |-|   b
     *  |-|-| resultat
     */
    @Test
    fun `kombinerer gammel og ny som overlapper delvis bakover tid 2`() {
        val a = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), år(2021))
        val b = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), januar(2021)..mai(2021))

        listOf(a, b).lagTidslinje()!!.krympTilPeriode(år(2021)) shouldBe listOf(
            Tidslinjeobjekt(b.opprettet, januar(2021)..mai(2021)),
            Tidslinjeobjekt(a.opprettet, juni(2021)..desember(2021)),
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
        val a = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), februar(2021)..november(2021))
        val b = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), januar(2021)..februar(2021))
        val c = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), oktober(2021)..desember(2021))

        listOf(b, c, a).lagTidslinje()!!.krympTilPeriode(år(2021)) shouldBe listOf(
            Tidslinjeobjekt(b.opprettet, januar(2021)..februar(2021)),
            Tidslinjeobjekt(a.opprettet, mars(2021)..september(2021)),
            Tidslinjeobjekt(c.opprettet, oktober(2021)..desember(2021)),
        )
    }

    /**
     * |---|           a
     *     |-------|   b
     *     |-----|     c
     *       |---|     d
     * |-a-|c|-d-|b|  resultat
     */
    @Test
    fun `takler at kombinasjonen b, c, d fungerer uavhengig av rekkefølgen`() {
        val a = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), januar(2021)..april(2021))
        val b = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), mai(2021)..januar(2022))
        val c = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), mai(2021)..desember(2021))
        val d = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), juni(2021)..desember(2021))

        listOf(a, b, c, d).lagTidslinje()!!.krympTilPeriode(
            januar(2021)..desember(2022),
        )?.let {
            val expecteda = Tidslinjeobjekt(a.opprettet, januar(2021)..april(2021))
            val expectedc = Tidslinjeobjekt(c.opprettet, mai(2021))
            val expectedd = Tidslinjeobjekt(d.opprettet, juni(2021)..desember(2021))
            val expectedb = Tidslinjeobjekt(b.opprettet, januar(2022))

            it shouldBe listOf(expecteda, expectedc, expectedd, expectedb)
            it.gjeldendeForDato(1.januar(2021)) shouldBe expecteda
            it.gjeldendeForDato(1.mai(2021)) shouldBe expectedc
            it.gjeldendeForDato(1.juni(2021)) shouldBe expectedd
            it.gjeldendeForDato(1.januar(2022)) shouldBe expectedb
        }
    }

    /**
     *  |-----| a
     *    |-|   b
     *  |-|-|-| resultat
     */
    @Test
    fun `gammel overlapper ny fullstendig`() {
        val a = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), år(2021))
        val b = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), april(2021)..mai(2021))

        listOf(a, b).lagTidslinje()!!.krympTilPeriode(år(2021)) shouldBe listOf(
            Tidslinjeobjekt(a.opprettet, januar(2021)..mars(2021)),
            Tidslinjeobjekt(b.opprettet, april(2021)..mai(2021)),
            Tidslinjeobjekt(a.opprettet, juni(2021)..desember(2021)),
        )
    }

    /**
     *  |-----| a
     *    |---| b
     *  |-|---| resultat
     */
    @Test
    fun `ny erstatter siste del av gammel`() {
        val a = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), år(2021))
        val b = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), april(2021)..desember(2021))

        listOf(a, b).lagTidslinje()!!.krympTilPeriode(år(2021)) shouldBe listOf(
            Tidslinjeobjekt(a.opprettet, januar(2021)..mars(2021)),
            Tidslinjeobjekt(b.opprettet, april(2021)..desember(2021)),
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
        val a = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), år(2021))
        val b = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), juni(2021))
        val c = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), juli(2021)..desember(2021))
        val d = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), mars(2021)..juni(2021))

        listOf(a, d, c, b).lagTidslinje()!!.krympTilPeriode(år(2021)) shouldBe listOf(
            Tidslinjeobjekt(a.opprettet, januar(2021)..februar(2021)),
            Tidslinjeobjekt(d.opprettet, mars(2021)..juni(2021)),
            Tidslinjeobjekt(c.opprettet, juli(2021)..desember(2021)),
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
        val a = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), år(2021))
        val b = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), mars(2021)..april(2021))
        val c = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), mai(2021)..juli(2021))
        val d = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), august(2021)..desember(2021))

        listOf(b, a, c, d).lagTidslinje()!!.krympTilPeriode(år(2021)) shouldBe listOf(
            Tidslinjeobjekt(a.opprettet, januar(2021)..februar(2021)),
            Tidslinjeobjekt(b.opprettet, mars(2021)..april(2021)),
            Tidslinjeobjekt(c.opprettet, mai(2021)..juli(2021)),
            Tidslinjeobjekt(d.opprettet, august(2021)..desember(2021)),
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
        val a = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), år(2021))
        val b = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), januar(2021)..mars(2021))
        val c = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), juli(2021)..desember(2021))

        listOf(a, b, c).lagTidslinje()!!.krympTilPeriode(år(2021)) shouldBe listOf(
            Tidslinjeobjekt(b.opprettet, januar(2021)..mars(2021)),
            Tidslinjeobjekt(a.opprettet, april(2021)..juni(2021)),
            Tidslinjeobjekt(c.opprettet, juli(2021)..desember(2021)),
        )
    }

    /**
     *  |-|      a
     *       |-| b
     *  |-|  |-| resultat
     */
    @Test
    fun `kombinerer gammel og ny som er fullstendig adskilt i tid`() {
        val a = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), januar(2021)..mars(2021))
        val b = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), juli(2021)..desember(2021))

        listOf(a, b).lagTidslinje()!!.krympTilPeriode(år(2021)) shouldBe listOf(
            Tidslinjeobjekt(a.opprettet, januar(2021)..mars(2021)),
            Tidslinjeobjekt(b.opprettet, juli(2021)..desember(2021)),
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
        val a = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), februar(2021)..desember(2021))
        val b = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), mars(2021)..desember(2021))
        val c = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), juli(2021)..desember(2021))
        val d = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), år(2021))

        listOf(c, a, b, d).lagTidslinje()!!.krympTilPeriode(år(2021)) shouldBe listOf(
            Tidslinjeobjekt(d.opprettet, år(2021)),
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
        val a = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), februar(2021)..mai(2021))
        val b = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), januar(2021)..juni(2021))
        val c = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), juni(2021)..desember(2021))
        val d = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), april(2021)..juni(2021))

        listOf(a, b, c, d).lagTidslinje()!!.krympTilPeriode(år(2021)) shouldBe listOf(
            Tidslinjeobjekt(b.opprettet, januar(2021)..mars(2021)),
            Tidslinjeobjekt(d.opprettet, april(2021)..juni(2021)),
            Tidslinjeobjekt(c.opprettet, juli(2021)..desember(2021)),
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
        val a = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), januar(2021)..oktober(2021))
        val b = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), mars(2021)..desember(2021))
        val c = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), juni(2021)..juli(2021))

        listOf(a, b, c).lagTidslinje()!!.krympTilPeriode(år(2021)) shouldBe listOf(
            Tidslinjeobjekt(a.opprettet, januar(2021)..februar(2021)),
            Tidslinjeobjekt(b.opprettet, mars(2021)..mai(2021)),
            Tidslinjeobjekt(c.opprettet, juni(2021)..juli(2021)),
            Tidslinjeobjekt(b.opprettet, august(2021)..desember(2021)),
        )
    }

    @Test
    fun `kan lage tidslinje for forskjellige typer objekter`() {
        val a = Grunnlag.Uføregrunnlag(
            opprettet = Tidspunkt.now(tikkendeKlokke),
            periode = år(2021),
            uføregrad = Uføregrad.parse(50),
            forventetInntekt = 15000,
        )

        val b = Grunnlag.Uføregrunnlag(
            opprettet = Tidspunkt.now(tikkendeKlokke),
            periode = år(2021),
            uføregrad = Uføregrad.parse(100),
            forventetInntekt = 0,
        )

        listOf(a, b).lagTidslinje()!!.krympTilPeriode(år(2021))?.let {
            it shouldHaveSize 1
            it[0].let { resultat ->
                resultat.id shouldNotBe a.id
                resultat.id shouldNotBe b.id
                resultat.periode shouldBe år(2021)
                resultat.uføregrad shouldBe Uføregrad.parse(100)
                resultat.forventetInntekt shouldBe 0
            }
        }
    }

    @Test
    fun `returnerer tom liste hvis ingen elementer sendes inn`() {
        emptyList<Tidslinjeobjekt>().lagTidslinje() shouldBe null
    }

    /**
     *  |-----| a
     *    |-|   b
     *  |-|-|-| resultat
     */
    @Test
    fun `justerer tidslinjen i forhold til perioden som etterspørres`() {
        val a = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), år(2021))
        val b = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), april(2021)..mai(2021))

        /** |-|     periode
         *  |-----| a
         *    |-|   b
         *  |-|     resultat
         */
        listOf(a, b).lagTidslinje()!!.krympTilPeriode(
            januar(2021)..mars(2021),
        )?.let {
            val expected = Tidslinjeobjekt(a.opprettet, januar(2021)..mars(2021))
            it shouldBe listOf(expected)
            it.gjeldendeForDato(1.januar(2021)) shouldBe expected
            it.gjeldendeForDato(1.desember(2021)) shouldBe null
        }

        /**   |-|   periode
         *  |-----| a
         *    |-|   b
         *    |-|   resultat
         */

        listOf(a, b).lagTidslinje()!!.krympTilPeriode(
            april(2021)..mai(2021),
        ) shouldBe listOf(
            Tidslinjeobjekt(b.opprettet, april(2021)..mai(2021)),
        )

        /**   |---| periode
         *  |-----| a
         *    |-|   b
         *    |-|-| resultat
         */
        listOf(a, b).lagTidslinje()!!.krympTilPeriode(
            april(2021)..desember(2021),
        ) shouldBe listOf(
            Tidslinjeobjekt(b.opprettet, april(2021)..mai(2021)),
            Tidslinjeobjekt(a.opprettet, juni(2021)..desember(2021)),
        )

        /**        |-| periode
         *  |-----| a
         *    |-|   b
         */
        listOf(a, b).lagTidslinje()!!.krympTilPeriode(år(2022)) shouldBe null
    }

    @Test
    fun `validator kaster exception dersom tidslinja ikke har distinkte fra og med datoer`() {
        assertDoesNotThrow {
            Validator.valider(
                nonEmptyListOf(Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), år(2021))),
            )
        }
        assertThrows<IllegalStateException> {
            Validator.valider(
                nonEmptyListOf(
                    Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), år(2021)),
                    Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), år(2021)),
                ),
            )
        }.message shouldBe "Tidslinje har elementer med overlappende perioder!"

        assertThrows<IllegalStateException> {
            Validator.valider(
                nonEmptyListOf(
                    Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), januar(2021)..april(2021)),
                    Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), mai(2021)..desember(2021)),
                    Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), mai(2021)..juli(2021)),
                ),
            )
        }.message shouldBe "Tidslinje har elementer med overlappende perioder!"
    }

    @Test
    fun `validator kaster exception dersom tidslinja ikke har distinkte til og med datoer`() {
        assertThrows<IllegalStateException> {
            Validator.valider(
                nonEmptyListOf(
                    Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), år(2021)),
                    Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), mai(2021)..desember(2021)),
                ),
            )
        }.message shouldBe "Tidslinje har elementer med overlappende perioder!"

        assertThrows<IllegalStateException> {
            Validator.valider(
                nonEmptyListOf(
                    Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), januar(2021)..april(2021)),
                    Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), mai(2021)..desember(2021)),
                    Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), august(2021)..desember(2021)),
                ),
            )
        }.message shouldBe "Tidslinje har elementer med overlappende perioder!"
    }

    @Test
    fun `validator kaster exception dersom tidslinja har elementer med overlappende perioder`() {
        shouldThrowWithMessage<IllegalStateException>("Tidslinje har elementer med overlappende perioder!") {
            Validator.valider(
                nonEmptyListOf(
                    Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), år(2021)),
                    Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), juni(2021)..oktober(2021)),
                ),
            )
        }
    }

    @Test
    fun `måned etter tidslinje gir null`() {
        val a = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), år(2021))
        val b = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), mai(2021)..mai(2021))
        listOf(a, b).lagTidslinje()!!.fjernMånederFør(YearMonth.of(2023, Month.MAY).tilMåned()) shouldBe null
    }

    @Test
    fun `fjerner månedene som er før angitt dato`() {
        val a = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), år(2021))
        val b = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), april(2021)..mai(2021))

        listOf(a, b).lagTidslinje()!!.fjernMånederFør(mai(2021)).let {
            it shouldNotBe null
            val expectedA = Tidslinjeobjekt(b.opprettet, mai(2021)..mai(2021))
            val expectedB = Tidslinjeobjekt(a.opprettet, juni(2021)..desember(2021))
            it shouldBe listOf(expectedA, expectedB)
            it!!.gjeldendeForDato(1.mai(2021)) shouldBe expectedA
            it.gjeldendeForDato(1.desember(2021)) shouldBe expectedB
        }
    }

    @Nested
    inner class Maskering {
        @Test
        fun `maskerer en enkelt verdi`() {
            val a = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), januar(2021)..mars(2021))

            a.fjernPeriode().let {
                it shouldBe emptyList()
                it.lagTidslinje() shouldBe null
            }
        }

        @Test
        fun `maskerer en enkelt verdi for en gitt periode og justerer tidslinjen i henhold`() {
            val a = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), januar(2021)..mars(2021))

            a.fjernPerioder(listOf(februar(2021))).lagTidslinje()!!.krympTilPeriode(
                januar(2021)..mars(2021),
            ) shouldBe listOf(
                Tidslinjeobjekt(a.opprettet, januar(2021)),
                Tidslinjeobjekt(a.opprettet, mars(2021)),
            )
        }

        @Test
        fun `maskerer en av flere verdier`() {
            val a = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), januar(2021)..mars(2021))
            val b = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), februar(2021))

            (a.fjernPeriode() + b).lagTidslinje()!!.krympTilPeriode(
                januar(2021)..mars(2021),
            ) shouldBe listOf(
                Tidslinjeobjekt(b.opprettet, februar(2021)),
            )
        }

        @Test
        fun `maskerer verdier potpurri`() {
            val tikkendeKlokke = TikkendeKlokke()
            val a = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), år(2021))
            val b = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), februar(2021))
            val c = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), november(2021)..desember(2021))
            val d = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), mai(2021)..desember(2021))

            (listOf(a) + b.fjernPeriode() + c.fjernPeriode() + listOf(d))
                .lagTidslinje()!!
                .krympTilPeriode(år(2021)) shouldBe listOf(
                Tidslinjeobjekt(a.opprettet, januar(2021)..april(2021)),
                Tidslinjeobjekt(d.opprettet, mai(2021)..desember(2021)),
            )
        }

        @Test
        fun `maskering for perioder som ikke overlapper elementer`() {
            val a = Tidslinjeobjekt(Tidspunkt.now(tikkendeKlokke), år(2021))

            a.fjernPerioder(listOf(desember(2022)..mars(2023))) shouldBe listOf(a)
        }
    }
}
