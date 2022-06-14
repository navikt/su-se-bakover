package no.nav.su.se.bakover.domain.satser

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.periode.april
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.november
import no.nav.su.se.bakover.common.scaleTo4
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.domain.grunnbeløp.GrunnbeløpForMåned
import no.nav.su.se.bakover.domain.grunnbeløp.Grunnbeløpsendring
import no.nav.su.se.bakover.domain.satser.MinsteÅrligYtelseForUføretrygdedeFactory.MinsteÅrligYtelseForUføretrygdedeEndring
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.satsFactoryTest
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Month

internal class SatsFactoryForSupplerendeStønadUføreTest {

    @Nested
    inner class UførFlyktning {
        @Test
        fun `ordinær - desember 2019 er ikke tilgjengelig`() {
            shouldThrow<IllegalStateException> {
                satsFactoryTestPåDato(påDato = LocalDate.now()).ordinærUføre(desember(2019))
            }.message shouldBe "Kan ikke avgjøre full supplerende stønad for måned: Måned(årOgMåned=2019-12). Vi har bare data for perioden: Periode(fraOgMed=2020-01-01, tilOgMed=2029-12-31)"
        }

        @Test
        fun `høy - desember 2019 er ikke tilgjengelig`() {
            shouldThrow<IllegalStateException> {
                satsFactoryTestPåDato(påDato = LocalDate.now()).høyUføre(desember(2019))
            }.message shouldBe "Kan ikke avgjøre full supplerende stønad for måned: Måned(årOgMåned=2019-12). Vi har bare data for perioden: Periode(fraOgMed=2020-01-01, tilOgMed=2029-12-31)"
        }

        @Test
        fun `ordinær - januar 2020 er tilgjengelig`() {
            shouldNotThrowAny {
                satsFactoryTestPåDato(påDato = LocalDate.now()).ordinærUføre(januar(2020))
            }
        }

        @Test
        fun `høy - januar 2020 er tilgjengelig`() {
            shouldNotThrowAny {
                satsFactoryTestPåDato(påDato = LocalDate.now()).høyUføre(mai(2020))
            }
        }

        @Test
        fun `ordinær - januar 2021`() {
            satsFactoryTestPåDato(påDato = LocalDate.now()).ordinærUføre(januar(2021)).let {
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
                    toProsentAvHøyForMåned = BigDecimal("418.9174666666666666666666666666667"), // 2.48 * 106399 * 0.02 / 12
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
            satsFactoryTestPåDato(påDato = LocalDate.now()).høyUføre(januar(2021)).let {
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
                    toProsentAvHøyForMåned = BigDecimal("418.9174666666666666666666666666667"), // 2.48 * 101351 * 0.02 / 12
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
            satsFactoryTestPåDato(påDato = LocalDate.now()).ordinærUføre(mai(2021)).let {
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
                    toProsentAvHøyForMåned = BigDecimal("439.7825333333333333333333333333333"), // 2.48 * 106399 * 0.02 / 12
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
            satsFactoryTestPåDato(påDato = LocalDate.now()).høyUføre(mai(2021)).let {
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
                    toProsentAvHøyForMåned = BigDecimal("439.7825333333333333333333333333333"), // 2.48 * 106399 * 0.02 / 12
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
            satsFactoryTestPåDato(påDato = LocalDate.now()).ordinærUføre(mai(2022)).let {
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
            satsFactoryTestPåDato(påDato = LocalDate.now()).høyUføre(mai(2022)).let {
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
            val expectedIkrafttredelse = 4.september(2020)
            satsFactoryTestPåDato(påDato = LocalDate.now()).høyUføre(april(2021)).let {
                it.grunnbeløp.ikrafttredelse shouldBe expectedIkrafttredelse
                it.ikrafttredelse shouldBe expectedIkrafttredelse
            }
        }

        @Test
        fun `finn siste g-endringsdato for 2021-05-01`() {
            val expectedIkrafttredelse = LocalDate.of(2021, Month.MAY, 21)
            satsFactoryTestPåDato(påDato = LocalDate.now()).høyUføre(mai(2021)).let {
                it.grunnbeløp.ikrafttredelse shouldBe expectedIkrafttredelse
                it.ikrafttredelse shouldBe expectedIkrafttredelse
            }
        }

        @Test
        fun `historisk med dagens dato er lik seg selv`() {
            satsFactoryTestPåDato(påDato = LocalDate.now()).forSatskategoriUføre(
                mai(2022),
                Satskategori.HØY,
            ) shouldBe
                satsFactoryTestPåDato(påDato = LocalDate.now()).forSatskategoriUføre(mai(2022), Satskategori.HØY)
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
        fun `klokke påvirker hvilke satser som er tilgjengelig`() {
            val nåDate = LocalDate.now()
            val førDate = fixedLocalDate
            val forMåned = mai(2021)

            satsFactoryTest.gjeldende(nåDate).høyUføre(forMåned).grunnbeløp shouldBe GrunnbeløpForMåned(
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
                garantipensjonsendringerOrdinær = listOf(
                    GarantipensjonFactory.Garantipensjonsendring(1.mai(2021), 1.mai(2021), 0),
                    GarantipensjonFactory.Garantipensjonsendring(1.januar(2022), 1.januar(2022), 5),
                    GarantipensjonFactory.Garantipensjonsendring(1.mai(2022), 1.mai(2022), 10),
                ),
                garantipensjonsendringerHøy = listOf(
                    GarantipensjonFactory.Garantipensjonsendring(1.mai(2021), 1.mai(2021), 0),
                    GarantipensjonFactory.Garantipensjonsendring(1.januar(2022), 1.januar(2022), 10),
                    GarantipensjonFactory.Garantipensjonsendring(1.mai(2022), 1.mai(2022), 20),
                ),
                grunnbeløpsendringer = listOf(
                    // Kan slette den første her når vi har fått satt supplerendeStønadAlderFlyktningIkrafttredelse til 2021-01-01
                    Grunnbeløpsendring(1.januar(2020), 1.januar(2020), 5),
                    Grunnbeløpsendring(1.november(2021), 30.desember(2021), 10),
                    Grunnbeløpsendring(1.desember(2021), 31.desember(2021), 20),
                    Grunnbeløpsendring(1.februar(2022), 2.januar(2022), 30),
                ),
                minsteÅrligYtelseForUføretrygdedeOrdinær = listOf(
                    MinsteÅrligUføre(1.januar(2020), 1.januar(2020), Faktor(0.00001)),
                    MinsteÅrligUføre(1.november(2021), 16.november(2021), Faktor(1.0)),
                    MinsteÅrligUføre(1.desember(2021), 18.desember(2021), Faktor(2.0)),
                    MinsteÅrligUføre(1.januar(2022), 2.januar(2022), Faktor(3.0)),
                ),
                minsteÅrligYtelseForUføretrygdedeHøy = listOf(
                    MinsteÅrligUføre(1.januar(2020), 1.januar(2020), Faktor(0.00002)),
                    MinsteÅrligUføre(1.november(2021), 16.november(2021), Faktor(4.0)),
                    MinsteÅrligUføre(1.desember(2021), 18.desember(2021), Faktor(5.0)),
                    MinsteÅrligUføre(1.januar(2022), 2.januar(2022), Faktor(6.0)),
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
                    toProsentAvHøyForMåned = BigDecimal("0.8333333333333333333333333333333333"),
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

private typealias MinsteÅrligUføre = MinsteÅrligYtelseForUføretrygdedeEndring
