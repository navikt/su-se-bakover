package sats.domain

import arrow.core.nonEmptyListOf
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import no.nav.su.se.bakover.common.domain.Faktor
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.juni
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.extensions.november
import no.nav.su.se.bakover.common.extensions.scaleTo4
import no.nav.su.se.bakover.common.extensions.september
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.november
import no.nav.su.se.bakover.domain.satser.SatsFactoryForSupplerendeStønad
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.satsFactoryTest
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sats.domain.grunnbeløp.GrunnbeløpForMåned
import sats.domain.grunnbeløp.Grunnbeløpsendring
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Month

internal class SatsFactoryForSupplerendeStønadUføreTest {

    @Nested
    inner class UførFlyktning {
        @Test
        fun `ordinær - desember 2019 er ikke tilgjengelig`() {
            shouldThrow<IllegalArgumentException> {
                satsFactoryTestPåDato(påDato = fixedLocalDate).ordinærUføre(desember(2019))
            }.message shouldBe "Har ikke data for etterspurt måned: 2019-12. Vi har bare data fra og med måned: 2020-01"
        }

        @Test
        fun `høy - desember 2019 er ikke tilgjengelig`() {
            shouldThrow<IllegalArgumentException> {
                satsFactoryTestPåDato(påDato = fixedLocalDate).høyUføre(desember(2019))
            }.message shouldBe "Har ikke data for etterspurt måned: 2019-12. Vi har bare data fra og med måned: 2020-01"
        }

        @Test
        fun `ordinær - januar 2020 er tilgjengelig`() {
            shouldNotThrowAny {
                satsFactoryTestPåDato(påDato = fixedLocalDate).ordinærUføre(januar(2020))
            }
        }

        @Test
        fun `høy - januar 2020 er tilgjengelig`() {
            shouldNotThrowAny {
                satsFactoryTestPåDato(påDato = fixedLocalDate).høyUføre(mai(2020))
            }
        }

        @Test
        fun `ordinær - januar 2021`() {
            satsFactoryTestPåDato(påDato = fixedLocalDate).ordinærUføre(januar(2021)).let {
                it shouldBe FullSupplerendeStønadForMåned.Uføre(
                    måned = januar(2021),
                    satskategori = Satskategori.ORDINÆR,
                    grunnbeløp = GrunnbeløpForMåned(
                        måned = januar(2021),
                        grunnbeløpPerÅr = 101351,
                        ikrafttredelse = 4.september(2020),
                        virkningstidspunkt = 1.mai(2020),
                    ),
                    minsteÅrligYtelseForUføretrygdede = MinsteÅrligYtelseForUføretrygdedeForMåned(
                        faktor = Faktor(2.28),
                        satsKategori = Satskategori.ORDINÆR,
                        ikrafttredelse = 1.januar(2015),
                        virkningstidspunkt = 1.januar(2015),
                        måned = januar(2021),
                    ),
                    // 2.48 * 106399 * 0.02 / 12
                    toProsentAvHøyForMåned = BigDecimal("418.9174666666666666666666666666667"),
                )
                it.satsPerÅr shouldBe BigDecimal("231080.28") // 2.28 * 101351
                it.satsForMåned shouldBe BigDecimal("19256.69") // 2.28 * 101351 / 12
                it.satsForMånedAvrundet shouldBe 19257
                it.satsForMånedAsDouble shouldBe 19256.69
                it.ikrafttredelse shouldBe 4.september(2020)
                it.toProsentAvHøyForMånedAsDouble shouldBe 418.91746666666666
            }
        }

        @Test
        fun `høy - januar 2021`() {
            satsFactoryTestPåDato(påDato = fixedLocalDate).høyUføre(januar(2021)).let {
                it shouldBe FullSupplerendeStønadForMåned.Uføre(
                    måned = januar(2021),
                    satskategori = Satskategori.HØY,
                    grunnbeløp = GrunnbeløpForMåned(
                        måned = januar(2021),
                        grunnbeløpPerÅr = 101351,
                        ikrafttredelse = 4.september(2020),
                        virkningstidspunkt = 1.mai(2020),
                    ),
                    minsteÅrligYtelseForUføretrygdede = MinsteÅrligYtelseForUføretrygdedeForMåned(
                        faktor = Faktor(2.48),
                        satsKategori = Satskategori.HØY,
                        ikrafttredelse = 1.januar(2015),
                        virkningstidspunkt = 1.januar(2015),
                        måned = januar(2021),
                    ),
                    // 2.48 * 101351 * 0.02 / 12
                    toProsentAvHøyForMåned = BigDecimal("418.9174666666666666666666666666667"),
                )
                it.satsPerÅr shouldBe BigDecimal("251350.48") // 2.48 * 101351
                it.satsForMåned.scaleTo4() shouldBe BigDecimal("20945.8733") // 2.48 * 101351 / 12
                it.satsForMånedAvrundet shouldBe 20946
                it.satsForMånedAsDouble shouldBe 20945.873333333333
                it.ikrafttredelse shouldBe 4.september(2020)
                it.toProsentAvHøyForMånedAsDouble shouldBe 418.91746666666666
            }
        }

        @Test
        fun `ordinær - mai 2021`() {
            satsFactoryTestPåDato(påDato = 1.juni(2022)).ordinærUføre(mai(2021)).let {
                it shouldBe FullSupplerendeStønadForMåned.Uføre(
                    måned = mai(2021),
                    satskategori = Satskategori.ORDINÆR,
                    grunnbeløp = GrunnbeløpForMåned(
                        måned = mai(2021),
                        grunnbeløpPerÅr = 106399,
                        ikrafttredelse = 21.mai(2021),
                        virkningstidspunkt = 1.mai(2021),
                    ),
                    minsteÅrligYtelseForUføretrygdede = MinsteÅrligYtelseForUføretrygdedeForMåned(
                        faktor = Faktor(2.28),
                        satsKategori = Satskategori.ORDINÆR,
                        ikrafttredelse = 1.januar(2015),
                        virkningstidspunkt = 1.januar(2015),
                        måned = mai(2021),
                    ),
                    // 2.48 * 106399 * 0.02 / 12
                    toProsentAvHøyForMåned = BigDecimal("439.7825333333333333333333333333333"),
                )
                it.satsPerÅr shouldBe BigDecimal("242589.72") // 2.28 * 106399
                it.satsForMåned shouldBe BigDecimal("20215.81") // 2.28 * 106399 / 12
                it.satsForMånedAvrundet shouldBe 20216
                it.satsForMånedAsDouble shouldBe 20215.81
                it.ikrafttredelse shouldBe 21.mai(2021)
                it.toProsentAvHøyForMånedAsDouble shouldBe 439.78253333333333
            }
        }

        @Test
        fun `høy - mai 2021`() {
            satsFactoryTestPåDato(påDato = 1.juni(2021)).høyUføre(mai(2021)).let {
                it shouldBe FullSupplerendeStønadForMåned.Uføre(
                    måned = mai(2021),
                    satskategori = Satskategori.HØY,
                    grunnbeløp = GrunnbeløpForMåned(
                        måned = mai(2021),
                        grunnbeløpPerÅr = 106399,
                        ikrafttredelse = 21.mai(2021),
                        virkningstidspunkt = 1.mai(2021),
                    ),
                    minsteÅrligYtelseForUføretrygdede = MinsteÅrligYtelseForUføretrygdedeForMåned(
                        faktor = Faktor(2.48),
                        satsKategori = Satskategori.HØY,
                        ikrafttredelse = 1.januar(2015),
                        virkningstidspunkt = 1.januar(2015),
                        måned = mai(2021),
                    ),
                    // 2.48 * 106399 * 0.02 / 12
                    toProsentAvHøyForMåned = BigDecimal("439.7825333333333333333333333333333"),
                )
                it.satsPerÅr shouldBe BigDecimal("263869.52") // 2.48 * 106399
                it.satsForMåned.scaleTo4() shouldBe BigDecimal("21989.1267") // 2.48 * 106399 / 12
                it.satsForMånedAvrundet shouldBe 21989
                it.satsForMånedAsDouble shouldBe 21989.126666666667
                it.ikrafttredelse shouldBe 21.mai(2021)
                it.toProsentAvHøyForMånedAsDouble shouldBe 439.78253333333333
            }
        }

        @Test
        fun `ordinær - mai 2022`() {
            satsFactoryTestPåDato(påDato = 1.juni(2022)).ordinærUføre(mai(2022)).let {
                it shouldBe FullSupplerendeStønadForMåned.Uføre(
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
                        virkningstidspunkt = 1.januar(2015),
                        måned = mai(2022),
                    ),
                    // 2.48 * G2022-5 * 0.02 / 12
                    toProsentAvHøyForMåned = BigDecimal("460.7716"),
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
            satsFactoryTestPåDato(påDato = 1.juni(2022)).høyUføre(mai(2022)).let {
                it shouldBe FullSupplerendeStønadForMåned.Uføre(
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
                        virkningstidspunkt = 1.januar(2015),
                        måned = mai(2022),
                    ),
                    // 2.48 * G2022-5 * 0.02 / 12
                    toProsentAvHøyForMåned = BigDecimal("460.7716"),
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
        fun `ordinær - mai 2023`() {
            satsFactoryTestPåDato(påDato = 26.mai(2023)).ordinærUføre(mai(2023)).let {
                it shouldBe FullSupplerendeStønadForMåned.Uføre(
                    måned = mai(2023),
                    satskategori = Satskategori.ORDINÆR,
                    grunnbeløp = GrunnbeløpForMåned(
                        måned = mai(2023),
                        grunnbeløpPerÅr = 118620,
                        ikrafttredelse = 26.mai(2023),
                        virkningstidspunkt = 1.mai(2023),
                    ),
                    minsteÅrligYtelseForUføretrygdede = MinsteÅrligYtelseForUføretrygdedeForMåned(
                        faktor = Faktor(2.28),
                        satsKategori = Satskategori.ORDINÆR,
                        ikrafttredelse = 1.januar(2015),
                        virkningstidspunkt = 1.januar(2015),
                        måned = mai(2023),
                    ),
                    // 2.48 * G2023-5 * 0.02 / 12
                    toProsentAvHøyForMåned = BigDecimal("490.2960"),
                )
                it.satsPerÅr shouldBe BigDecimal("270453.60") // 2.28 * G2023-5
                it.satsForMåned shouldBe BigDecimal("22537.80") // 2.28 * G2023-5 / 12
                it.satsForMånedAvrundet shouldBe 22538
                it.satsForMånedAsDouble shouldBe 22537.8
                it.ikrafttredelse shouldBe 26.mai(2023)
                it.toProsentAvHøyForMånedAsDouble shouldBe 490.296
            }
        }

        @Test
        fun `høy - mai 2023`() {
            satsFactoryTestPåDato(påDato = 1.juni(2023)).høyUføre(mai(2023)).let {
                it shouldBe FullSupplerendeStønadForMåned.Uføre(
                    måned = mai(2023),
                    satskategori = Satskategori.HØY,
                    grunnbeløp = GrunnbeløpForMåned(
                        måned = mai(2023),
                        grunnbeløpPerÅr = 118620,
                        ikrafttredelse = 26.mai(2023),
                        virkningstidspunkt = 1.mai(2023),
                    ),
                    minsteÅrligYtelseForUføretrygdede = MinsteÅrligYtelseForUføretrygdedeForMåned(
                        faktor = Faktor(2.48),
                        satsKategori = Satskategori.HØY,
                        ikrafttredelse = 1.januar(2015),
                        virkningstidspunkt = 1.januar(2015),
                        måned = mai(2023),
                    ),
                    // 2.48 * G2023-5 * 0.02 / 12
                    toProsentAvHøyForMåned = BigDecimal("490.2960"),
                )
                it.satsPerÅr shouldBe BigDecimal("294177.60") // 2.48 * G2023-5
                it.satsForMåned.scaleTo4() shouldBe BigDecimal("24514.8000") // 2.48 * G2023-5 / 12
                it.satsForMånedAvrundet shouldBe 24515
                it.satsForMånedAsDouble shouldBe 24514.8
                it.ikrafttredelse shouldBe 26.mai(2023)
                it.toProsentAvHøyForMånedAsDouble shouldBe 490.296
            }
        }

        @Test
        fun `finn siste g-endringsdato for 2021-04-30`() {
            val expectedIkrafttredelse = 4.september(2020)
            satsFactoryTestPåDato(påDato = fixedLocalDate).høyUføre(april(2021)).let {
                it.grunnbeløp.ikrafttredelse shouldBe expectedIkrafttredelse
                it.ikrafttredelse shouldBe expectedIkrafttredelse
            }
        }

        @Test
        fun `finn siste g-endringsdato for 2021-05-01`() {
            val expectedIkrafttredelse = LocalDate.of(2021, Month.MAY, 21)
            satsFactoryTestPåDato(påDato = 1.juni(2022)).høyUføre(mai(2021)).let {
                it.grunnbeløp.ikrafttredelse shouldBe expectedIkrafttredelse
                it.ikrafttredelse shouldBe expectedIkrafttredelse
            }
        }

        @Test
        fun `historisk med dagens dato er lik seg selv`() {
            satsFactoryTestPåDato(påDato = fixedLocalDate).forSatskategoriUføre(
                mai(2022),
                Satskategori.HØY,
            ) shouldBe
                satsFactoryTestPåDato(påDato = fixedLocalDate).forSatskategoriUføre(mai(2022), Satskategori.HØY)
        }

        @Test
        fun `verdi for mai 2022 i januar 2020`() {
            satsFactoryTestPåDato(påDato = 1.januar(2020)).forSatskategoriUføre(mai(2022), Satskategori.HØY)
                .let {
                    it shouldBe FullSupplerendeStønadForMåned.Uføre(
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
                            virkningstidspunkt = 1.januar(2015),
                            måned = mai(2022),
                        ),
                        // 2.48 * G2022-5 * 0.02 / 12
                        toProsentAvHøyForMåned = BigDecimal("412.7464"),
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
        fun `klokke påvirker hvilke satser som er tilgjengelig`() {
            val etterDato = 1.juni(2021)
            val førDate = 1.januar(2021)
            val forMåned = mai(2021)

            satsFactoryTest.gjeldende(etterDato).høyUføre(forMåned).grunnbeløp shouldBe GrunnbeløpForMåned(
                måned = mai(2021),
                grunnbeløpPerÅr = 106399,
                ikrafttredelse = 21.mai(2021),
                virkningstidspunkt = 1.mai(2021),
            )
            val gammelMai = satsFactoryTest.gjeldende(førDate).høyUføre(forMåned).grunnbeløp

            gammelMai shouldBe GrunnbeløpForMåned(
                måned = mai(2021),
                grunnbeløpPerÅr = 101351,
                ikrafttredelse = 4.september(2020),
                virkningstidspunkt = 1.mai(2020),
            )

            satsFactoryTest.gjeldende(LocalDate.now(fixedClock)).høyUføre(forMåned).grunnbeløp shouldBe gammelMai
        }

        @Test
        fun `factory for allerede utregnede verdier caches`() {
            // Trenger en egen her siden vi asserter på cachen (hvis ikke kan de andre testene forstyrre)
            val satsFactoryTest = SatsFactoryForSupplerendeStønad()

            val verdier = (januar(2020)..april(2022))
                .måneder()
                .map { satsFactoryTest.gjeldende(it.fraOgMed) }
                .distinct()

            verdier shouldHaveSize 3

            val factoryMai19TilSeptember20 = verdier[0]
            val factorySeptember20TilMai21 = verdier[1]
            val factoryMai21TilMai22 = verdier[2]

            satsFactoryTest.gjeldende(1.mai(2019)) shouldBeSameInstanceAs factoryMai19TilSeptember20
            satsFactoryTest.gjeldende(3.september(2020)) shouldBeSameInstanceAs factoryMai19TilSeptember20
            satsFactoryTest.gjeldende(4.september(2020)) shouldBeSameInstanceAs factorySeptember20TilMai21
            satsFactoryTest.gjeldende(20.mai(2021)) shouldBeSameInstanceAs factorySeptember20TilMai21
            satsFactoryTest.gjeldende(21.mai(2021)) shouldBeSameInstanceAs factoryMai21TilMai22
            satsFactoryTest.gjeldende(19.mai(2022)) shouldBeSameInstanceAs factoryMai21TilMai22
            satsFactoryTest.gjeldende(19.mai(2022)) shouldBeSameInstanceAs factoryMai21TilMai22

            factoryMai19TilSeptember20.høyUføre(januar(2022)).grunnbeløp shouldBe GrunnbeløpForMåned(
                måned = januar(2022),
                grunnbeløpPerÅr = 99858,
                ikrafttredelse = 1.mai(2019),
                virkningstidspunkt = 1.mai(2019),
            )
            factorySeptember20TilMai21.høyUføre(januar(2022)).grunnbeløp shouldBe GrunnbeløpForMåned(
                måned = januar(2022),
                grunnbeløpPerÅr = 101351,
                ikrafttredelse = 4.september(2020),
                virkningstidspunkt = 1.mai(2020),
            )
            factoryMai21TilMai22.høyUføre(januar(2022)).grunnbeløp shouldBe GrunnbeløpForMåned(
                måned = januar(2022),
                grunnbeløpPerÅr = 106399,
                ikrafttredelse = 21.mai(2021),
                virkningstidspunkt = 1.mai(2021),
            )
        }

        @Test
        fun `Cache tar høyde for knekkpunkter på tvers av satser og grunnbeløps ikraftredelser`() {
            val satsFactory = SatsFactoryForSupplerendeStønad(
                tidligsteTilgjengeligeMåned = november(2021),
                garantipensjonsendringerOrdinær = nonEmptyListOf(
                    GarantipensjonFactory.Garantipensjonsendring(1.november(2021), 1.november(2021), 0),
                    GarantipensjonFactory.Garantipensjonsendring(1.januar(2022), 1.januar(2022), 5),
                    GarantipensjonFactory.Garantipensjonsendring(1.mai(2022), 1.mai(2022), 10),
                ),
                garantipensjonsendringerHøy = nonEmptyListOf(
                    GarantipensjonFactory.Garantipensjonsendring(1.november(2021), 1.november(2021), 0),
                    GarantipensjonFactory.Garantipensjonsendring(1.januar(2022), 1.januar(2022), 10),
                    GarantipensjonFactory.Garantipensjonsendring(1.mai(2022), 1.mai(2022), 20),
                ),
                grunnbeløpsendringer = nonEmptyListOf(
                    Grunnbeløpsendring(1.november(2021), 30.desember(2021), 10),
                    Grunnbeløpsendring(1.desember(2021), 31.desember(2021), 20),
                    Grunnbeløpsendring(1.februar(2022), 2.januar(2022), 30),
                ),
                minsteÅrligYtelseForUføretrygdedeOrdinær = nonEmptyListOf(
                    MinsteÅrligYtelseForUføretrygdedeFactory.MinsteÅrligYtelseForUføretrygdedeEndring(
                        1.november(2021),
                        16.november(2021),
                        Faktor(1.0),
                    ),
                    MinsteÅrligYtelseForUføretrygdedeFactory.MinsteÅrligYtelseForUføretrygdedeEndring(
                        1.desember(2021),
                        18.desember(2021),
                        Faktor(2.0),
                    ),
                    MinsteÅrligYtelseForUføretrygdedeFactory.MinsteÅrligYtelseForUføretrygdedeEndring(
                        1.januar(2022),
                        2.januar(2022),
                        Faktor(3.0),
                    ),
                ),
                minsteÅrligYtelseForUføretrygdedeHøy = nonEmptyListOf(
                    MinsteÅrligYtelseForUføretrygdedeFactory.MinsteÅrligYtelseForUføretrygdedeEndring(
                        1.november(2021),
                        16.november(2021),
                        Faktor(4.0),
                    ),
                    MinsteÅrligYtelseForUføretrygdedeFactory.MinsteÅrligYtelseForUføretrygdedeEndring(
                        1.desember(2021),
                        18.desember(2021),
                        Faktor(5.0),
                    ),
                    MinsteÅrligYtelseForUføretrygdedeFactory.MinsteÅrligYtelseForUføretrygdedeEndring(
                        1.januar(2022),
                        2.januar(2022),
                        Faktor(6.0),
                    ),
                ),
            )
            satsFactory.gjeldende(1.januar(2022)).let {
                it.høyAlder(januar(2022)) shouldBe FullSupplerendeStønadForMåned.Alder(
                    måned = januar(2022),
                    satskategori = Satskategori.HØY,
                    garantipensjonForMåned = GarantipensjonForMåned(
                        måned = januar(2022),
                        satsKategori = Satskategori.HØY,
                        garantipensjonPerÅr = 10,
                        ikrafttredelse = 1.januar(2022),
                        virkningstidspunkt = 1.januar(2022),
                    ),
                    toProsentAvHøyForMåned = BigDecimal("0.01666666666666666666666666666666667"),
                )
                it.grunnbeløp(januar(2022)) shouldBe GrunnbeløpForMåned(
                    måned = januar(2022),
                    grunnbeløpPerÅr = 20,
                    ikrafttredelse = 31.desember(2021),
                    virkningstidspunkt = 1.desember(2021),
                )
                it.høyUføre(november(2021)) shouldBe FullSupplerendeStønadForMåned.Uføre(
                    måned = november(2021),
                    satskategori = Satskategori.HØY,
                    grunnbeløp = GrunnbeløpForMåned(
                        måned = november(2021),
                        grunnbeløpPerÅr = 10,
                        ikrafttredelse = 30.desember(2021),
                        virkningstidspunkt = 1.november(2021),
                    ),
                    minsteÅrligYtelseForUføretrygdede = MinsteÅrligYtelseForUføretrygdedeForMåned(
                        faktor = Faktor(4.0),
                        satsKategori = Satskategori.HØY,
                        ikrafttredelse = 16.november(2021),
                        virkningstidspunkt = 1.november(2021),
                        måned = november(2021),
                    ),
                    toProsentAvHøyForMåned = BigDecimal("0.06666666666666666666666666666666667"),
                )
            }
        }
    }
}
