package no.nav.su.se.bakover.domain.satser

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.fixedClock
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.april
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.scaleTo4
import no.nav.su.se.bakover.domain.grunnbeløp.GrunnbeløpForMåned
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.satsFactoryTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.Month

internal class SatsFactoryForSupplerendeStønadTest {

    @Nested
    inner class UførFlyktning {
        @Test
        fun `ordinær - desember 2014 er ikke tilgjengelig`() {
            shouldThrow<IllegalStateException> {
                satsFactoryTest(clock = Clock.systemUTC()).ordinær(desember(2014))
            }.message shouldBe "Kan ikke avgjøre full supplerende stønad for måned: Måned(årOgMåned=2014-12). Vi har bare data for perioden: Periode(fraOgMed=2015-01-01, tilOgMed=2029-12-31)"
        }

        @Test
        fun `høy - desember 2014 er ikke tilgjengelig`() {
            shouldThrow<IllegalStateException> {
                satsFactoryTest(clock = Clock.systemUTC()).høy(desember(2014))
            }.message shouldBe "Kan ikke avgjøre full supplerende stønad for måned: Måned(årOgMåned=2014-12). Vi har bare data for perioden: Periode(fraOgMed=2015-01-01, tilOgMed=2029-12-31)"
        }

        @Test
        fun `ordinær - januar 2021`() {
            satsFactoryTest(clock = Clock.systemUTC()).ordinær(januar(2021)).let {
                it shouldBe FullSupplerendeStønadForMåned(
                    måned = januar(2021),
                    satskategori = Satskategori.ORDINÆR,
                    grunnbeløp = GrunnbeløpForMåned(
                        måned = januar(2021),
                        grunnbeløpPerÅr = 101351,
                        ikrafttredelse = 1.mai(2020),
                        virkningstidspunkt = 1.mai(2020),
                    ),
                    minsteÅrligYtelseForUføretrygdede = MinsteÅrligYtelseForUføretrygdedeForMåned(
                        faktor = Faktor(2.28),
                        satsKategori = Satskategori.ORDINÆR,
                        ikrafttredelse = 1.januar(2015),
                        måned = januar(2021),
                    ),
                    toProsentAvHøyForMåned = BigDecimal("418.9174666666666666666666666666667"), // 2.48 * 106399 * 0.02 / 12
                )
                it.satsPerÅr shouldBe BigDecimal("231080.28") // 2.28 * 101351
                it.satsForMåned shouldBe BigDecimal("19256.69") // 2.28 * 101351 / 12
                it.satsForMånedAvrundet shouldBe 19257
                it.satsForMånedAsDouble shouldBe 19256.69
                it.ikrafttredelse shouldBe 1.mai(2020)
                it.toProsentAvHøyForMånedAsDouble shouldBe 418.9174666666666666666666666666667
            }
        }

        @Test
        fun `høy - januar 2021`() {
            satsFactoryTest(clock = Clock.systemUTC()).høy(januar(2021)).let {
                it shouldBe FullSupplerendeStønadForMåned(
                    måned = januar(2021),
                    satskategori = Satskategori.HØY,
                    grunnbeløp = GrunnbeløpForMåned(
                        måned = januar(2021),
                        grunnbeløpPerÅr = 101351,
                        ikrafttredelse = 1.mai(2020),
                        virkningstidspunkt = 1.mai(2020),
                    ),
                    minsteÅrligYtelseForUføretrygdede = MinsteÅrligYtelseForUføretrygdedeForMåned(
                        faktor = Faktor(2.48),
                        satsKategori = Satskategori.HØY,
                        ikrafttredelse = 1.januar(2015),
                        måned = januar(2021),
                    ),
                    toProsentAvHøyForMåned = BigDecimal("418.9174666666666666666666666666667"), // 2.48 * 101351 * 0.02 / 12
                )
                it.satsPerÅr shouldBe BigDecimal("251350.48") // 2.48 * 101351
                it.satsForMåned.scaleTo4() shouldBe BigDecimal("20945.8733") // 2.48 * 101351 / 12
                it.satsForMånedAvrundet shouldBe 20946
                it.satsForMånedAsDouble shouldBe 20945.873333333333
                it.ikrafttredelse shouldBe 1.mai(2020)
                it.toProsentAvHøyForMånedAsDouble shouldBe 418.9174666666666666666666666666667
            }
        }

        @Test
        fun `ordinær - mai 2021`() {
            satsFactoryTest(clock = Clock.systemUTC()).ordinær(mai(2021)).let {
                it shouldBe FullSupplerendeStønadForMåned(
                    måned = mai(2021),
                    satskategori = Satskategori.ORDINÆR,
                    grunnbeløp = GrunnbeløpForMåned(
                        måned = mai(2021),
                        grunnbeløpPerÅr = 106399,
                        ikrafttredelse = 1.mai(2021),
                        virkningstidspunkt = 1.mai(2021),
                    ),
                    minsteÅrligYtelseForUføretrygdede = MinsteÅrligYtelseForUføretrygdedeForMåned(
                        faktor = Faktor(2.28),
                        satsKategori = Satskategori.ORDINÆR,
                        ikrafttredelse = 1.januar(2015),
                        måned = mai(2021),
                    ),
                    toProsentAvHøyForMåned = BigDecimal("439.7825333333333333333333333333333"), // 2.48 * 106399 * 0.02 / 12
                )
                it.satsPerÅr shouldBe BigDecimal("242589.72") // 2.28 * 106399
                it.satsForMåned shouldBe BigDecimal("20215.81") // 2.28 * 106399 / 12
                it.satsForMånedAvrundet shouldBe 20216
                it.satsForMånedAsDouble shouldBe 20215.81
                it.ikrafttredelse shouldBe 1.mai(2021)
                it.toProsentAvHøyForMånedAsDouble shouldBe 439.7825333333333333333333333333333
            }
        }

        @Test
        fun `høy - mai 2021`() {
            satsFactoryTest(clock = Clock.systemUTC()).høy(mai(2021)).let {
                it shouldBe FullSupplerendeStønadForMåned(
                    måned = mai(2021),
                    satskategori = Satskategori.HØY,
                    grunnbeløp = GrunnbeløpForMåned(
                        måned = mai(2021),
                        grunnbeløpPerÅr = 106399,
                        ikrafttredelse = 1.mai(2021),
                        virkningstidspunkt = 1.mai(2021),
                    ),
                    minsteÅrligYtelseForUføretrygdede = MinsteÅrligYtelseForUføretrygdedeForMåned(
                        faktor = Faktor(2.48),
                        satsKategori = Satskategori.HØY,
                        ikrafttredelse = 1.januar(2015),
                        måned = mai(2021),
                    ),
                    toProsentAvHøyForMåned = BigDecimal("439.7825333333333333333333333333333"), // 2.48 * 106399 * 0.02 / 12
                )
                it.satsPerÅr shouldBe BigDecimal("263869.52") // 2.48 * 106399
                it.satsForMåned.scaleTo4() shouldBe BigDecimal("21989.1267") // 2.48 * 106399 / 12
                it.satsForMånedAvrundet shouldBe 21989
                it.satsForMånedAsDouble shouldBe 21989.126666666667
                it.ikrafttredelse shouldBe 1.mai(2021)
                it.toProsentAvHøyForMånedAsDouble shouldBe 439.7825333333333333333333333333333
            }
        }

        @Test
        fun `ordinær - mai 2022`() {
            satsFactoryTest(clock = Clock.systemUTC()).ordinær(mai(2022)).let {
                it shouldBe FullSupplerendeStønadForMåned(
                    måned = mai(2022),
                    satskategori = Satskategori.ORDINÆR,
                    grunnbeløp = GrunnbeløpForMåned(
                        måned = mai(2022),
                        grunnbeløpPerÅr = 111477,
                        ikrafttredelse = 20.mai(2022),
                        virkningstidspunkt = 1.mai(2022),
                    ),
                    minsteÅrligYtelseForUføretrygdede = MinsteÅrligYtelseForUføretrygdedeForMåned(
                        faktor = Faktor(2.28),
                        satsKategori = Satskategori.ORDINÆR,
                        ikrafttredelse = 1.januar(2015),
                        måned = mai(2022),
                    ),
                    toProsentAvHøyForMåned = BigDecimal("460.7716"), // 2.48 * G2022-5 * 0.02 / 12
                )
                it.satsPerÅr shouldBe BigDecimal("254167.56") // 2.28 * G2022-5
                it.satsForMåned shouldBe BigDecimal("21180.63") // 2.28 * G2022-5 / 12
                it.satsForMånedAvrundet shouldBe 21181
                it.satsForMånedAsDouble shouldBe 21180.63
                it.ikrafttredelse shouldBe 20.mai(2022)
                it.toProsentAvHøyForMånedAsDouble shouldBe 460.7716
            }
        }

        @Test
        fun `høy - mai 2022`() {
            satsFactoryTest(clock = Clock.systemUTC()).høy(mai(2022)).let {
                it shouldBe FullSupplerendeStønadForMåned(
                    måned = mai(2022),
                    satskategori = Satskategori.HØY,
                    grunnbeløp = GrunnbeløpForMåned(
                        måned = mai(2022),
                        grunnbeløpPerÅr = 111477,
                        ikrafttredelse = 20.mai(2022),
                        virkningstidspunkt = 1.mai(2022),
                    ),
                    minsteÅrligYtelseForUføretrygdede = MinsteÅrligYtelseForUføretrygdedeForMåned(
                        faktor = Faktor(2.48),
                        satsKategori = Satskategori.HØY,
                        ikrafttredelse = 1.januar(2015),
                        måned = mai(2022),
                    ),
                    toProsentAvHøyForMåned = BigDecimal("460.7716"), // 2.48 * G2022-5 * 0.02 / 12
                )
                it.satsPerÅr shouldBe BigDecimal("276462.96") // 2.48 * G2022-5
                it.satsForMåned.scaleTo4() shouldBe BigDecimal("23038.5800") // 2.48 * G2022-5 / 12
                it.satsForMånedAvrundet shouldBe 23039
                it.satsForMånedAsDouble shouldBe 23038.5800
                it.ikrafttredelse shouldBe 20.mai(2022)
                it.toProsentAvHøyForMånedAsDouble shouldBe 460.7716
            }
        }

        @Test
        fun `finn siste g-endringsdato for 2021-04-30`() {
            val expectedIkrafttredelse = 1.mai(2020)
            satsFactoryTest(clock = Clock.systemUTC()).høy(april(2021)).let {
                it.grunnbeløp.ikrafttredelse shouldBe expectedIkrafttredelse
                it.ikrafttredelse shouldBe expectedIkrafttredelse
            }
        }

        @Test
        fun `finn siste g-endringsdato for 2021-05-01`() {
            val expectedIkrafttredelse = LocalDate.of(2021, Month.MAY, 1)
            satsFactoryTest(clock = Clock.systemUTC()).høy(mai(2021)).let {
                it.grunnbeløp.ikrafttredelse shouldBe expectedIkrafttredelse
                it.ikrafttredelse shouldBe expectedIkrafttredelse
            }
        }

        @Test
        fun `verdi for mai 2022 i januar 2020`() {
            satsFactoryTest(clock = 1.januar(2020).fixedClock()).forSatskategori(mai(2022), Satskategori.HØY)
                .let {
                    it shouldBe FullSupplerendeStønadForMåned(
                        måned = mai(2022),
                        satskategori = Satskategori.HØY,
                        grunnbeløp = GrunnbeløpForMåned(
                            måned = mai(2022),
                            grunnbeløpPerÅr = 99858,
                            ikrafttredelse = 1.mai(2019),
                            virkningstidspunkt = 1.mai(2019),
                        ),
                        minsteÅrligYtelseForUføretrygdede = MinsteÅrligYtelseForUføretrygdedeForMåned(
                            faktor = Faktor(2.48),
                            satsKategori = Satskategori.HØY,
                            ikrafttredelse = 1.januar(2015),
                            måned = mai(2022),
                        ),
                        toProsentAvHøyForMåned = BigDecimal("412.7464"), // 2.48 * G2022-5 * 0.02 / 12
                    )
                    it.satsPerÅr shouldBe BigDecimal("247647.84") // 2.48 * G2022-5
                    it.satsForMåned.scaleTo4() shouldBe BigDecimal("20637.3200") // 2.48 * G2022-5 / 12
                    it.satsForMånedAvrundet shouldBe 20637
                    it.satsForMånedAsDouble shouldBe 20637.3200
                    it.ikrafttredelse shouldBe 1.mai(2019)
                    it.toProsentAvHøyForMånedAsDouble shouldBe 412.7464
                }
        }

        @Test
        fun `historisk med dagens dato er lik seg selv`() {
            satsFactoryTest(clock = Clock.systemUTC()).forSatskategori(
                mai(2022),
                Satskategori.HØY,
            ) shouldBe
                satsFactoryTest(clock = Clock.systemUTC()).forSatskategori(mai(2022), Satskategori.HØY)
        }

        @Test
        fun `klokke påvirker hvilke satser som er tilgjengelig`() {
            val nå = satsFactoryTest(clock = Clock.systemUTC())
            val før = satsFactoryTest(clock = fixedClock)
            val forMåned = mai(2021)

            nå.høy(forMåned).grunnbeløp shouldBe GrunnbeløpForMåned(
                måned = mai(2021),
                grunnbeløpPerÅr = 106399,
                ikrafttredelse = 1.mai(2021),
                virkningstidspunkt = 1.mai(2021),
            )
            val gammelMai = før.høy(forMåned).grunnbeløp

            gammelMai shouldBe GrunnbeløpForMåned(
                måned = mai(2021),
                grunnbeløpPerÅr = 101351,
                ikrafttredelse = 1.mai(2020),
                virkningstidspunkt = 1.mai(2020),
            )

            nå.gjeldende(LocalDate.now(fixedClock)).høy(forMåned).grunnbeløp shouldBe gammelMai
        }

        @Test
        fun `factory for allerede utregnede verdier caches`() {
            val satsFactory = satsFactoryTest

            satsFactory.gjeldende(fixedLocalDate) shouldBeSameInstanceAs satsFactoryTest

            val verdier = Periode.create(1.januar(2020), 30.april(2022))
                .måneder()
                .map { satsFactory.gjeldende(it.fraOgMed) }
                .distinct()

            verdier shouldHaveSize 3

            val factoryMai19TilMai20 = verdier[0]
            val factoryMai20TilMai21 = verdier[1]
            val factoryMai21TilMai22 = verdier[2]

            factoryMai19TilMai20.gjeldende(1.juni(2019)) shouldBeSameInstanceAs factoryMai19TilMai20
            factoryMai19TilMai20.gjeldende(1.mars(2020)) shouldBeSameInstanceAs factoryMai19TilMai20
            factoryMai20TilMai21.gjeldende(1.juni(2020)) shouldBeSameInstanceAs factoryMai20TilMai21
            factoryMai20TilMai21.gjeldende(1.mars(2021)) shouldBeSameInstanceAs factoryMai20TilMai21
            factoryMai21TilMai22.gjeldende(1.juni(2021)) shouldBeSameInstanceAs factoryMai21TilMai22
            factoryMai21TilMai22.gjeldende(1.mars(2022)) shouldBeSameInstanceAs factoryMai21TilMai22

            factoryMai19TilMai20.høy(januar(2022)).grunnbeløp shouldBe GrunnbeløpForMåned(
                måned = januar(2022),
                grunnbeløpPerÅr = 99858,
                ikrafttredelse = 1.mai(2019),
                virkningstidspunkt = 1.mai(2019),
            )
            factoryMai20TilMai21.høy(januar(2022)).grunnbeløp shouldBe GrunnbeløpForMåned(
                måned = januar(2022),
                grunnbeløpPerÅr = 101351,
                ikrafttredelse = 1.mai(2020),
                virkningstidspunkt = 1.mai(2020),
            )
            factoryMai21TilMai22.høy(januar(2022)).grunnbeløp shouldBe GrunnbeløpForMåned(
                måned = januar(2022),
                grunnbeløpPerÅr = 106399,
                ikrafttredelse = 1.mai(2021),
                virkningstidspunkt = 1.mai(2021),
            )
        }
    }
}
