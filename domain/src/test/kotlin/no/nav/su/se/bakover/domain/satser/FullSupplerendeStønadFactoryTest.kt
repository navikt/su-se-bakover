package no.nav.su.se.bakover.domain.satser

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.april
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.scaleTo4
import no.nav.su.se.bakover.domain.grunnbeløp.GrunnbeløpForMåned
import no.nav.su.se.bakover.test.satsFactoryTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Month

internal class FullSupplerendeStønadFactoryTest {

    @Nested
    inner class UførFlyktning {

        // TODO jah: På et tidspunkt kan [satsFactoryTest] slutte å peke på produksjonsdata og da vil den ikke endre seg lenger.
        //  Alternativt kan test og prod peke på et felles sett. Også kan prod peke på en kopi hvor den har custom ting for preprod (regulerings-testdata)
        private val ordinær = satsFactoryTest.fullSupplerendeStønadOrdinær()
        private val høy = satsFactoryTest.fullSupplerendeStønadHøy()

        @Test
        fun `ordinær - desember 2014 er ikke tilgjengelig`() {
            shouldThrow<IllegalStateException> {
                ordinær.forMåned(desember(2014))
            }.message shouldBe "Kan ikke avgjøre full supplerende stønad for måned: Måned(årOgMåned=2014-12). Vi har bare data for perioden: Periode(fraOgMed=2015-01-01, tilOgMed=2029-12-31)"
        }

        @Test
        fun `høy - desember 2014 er ikke tilgjengelig`() {
            shouldThrow<IllegalStateException> {
                høy.forMåned(desember(2014))
            }.message shouldBe "Kan ikke avgjøre full supplerende stønad for måned: Måned(årOgMåned=2014-12). Vi har bare data for perioden: Periode(fraOgMed=2015-01-01, tilOgMed=2029-12-31)"
        }

        @Test
        fun `ordinær - januar 2021`() {
            ordinær.forMåned(januar(2021)).let {
                it shouldBe FullSupplerendeStønadForMåned(
                    måned = januar(2021),
                    satskategori = Satskategori.ORDINÆR,
                    grunnbeløp = GrunnbeløpForMåned(
                        måned = januar(2021),
                        grunnbeløpPerÅr = 101351,
                        ikrafttredelse = 1.mai(2020),
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
                it.toProsentAvHøyForMånedAvrundet shouldBe 419
            }
        }

        @Test
        fun `høy - januar 2021`() {
            høy.forMåned(januar(2021)).let {
                it shouldBe FullSupplerendeStønadForMåned(
                    måned = januar(2021),
                    satskategori = Satskategori.HØY,
                    grunnbeløp = GrunnbeløpForMåned(
                        måned = januar(2021),
                        grunnbeløpPerÅr = 101351,
                        ikrafttredelse = 1.mai(2020),
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
                it.toProsentAvHøyForMånedAvrundet shouldBe 419
            }
        }

        @Test
        fun `ordinær - mai 2021`() {
            ordinær.forMåned(mai(2021)).let {
                it shouldBe FullSupplerendeStønadForMåned(
                    måned = mai(2021),
                    satskategori = Satskategori.ORDINÆR,
                    grunnbeløp = GrunnbeløpForMåned(
                        måned = mai(2021),
                        grunnbeløpPerÅr = 106399,
                        ikrafttredelse = 1.mai(2021),
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
                it.toProsentAvHøyForMånedAvrundet shouldBe 440
            }
        }

        @Test
        fun `høy - mai 2021`() {
            høy.forMåned(mai(2021)).let {
                it shouldBe FullSupplerendeStønadForMåned(
                    måned = mai(2021),
                    satskategori = Satskategori.HØY,
                    grunnbeløp = GrunnbeløpForMåned(
                        måned = mai(2021),
                        grunnbeløpPerÅr = 106399,
                        ikrafttredelse = 1.mai(2021),
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
                it.toProsentAvHøyForMånedAvrundet shouldBe 440
            }
        }

        @Test
        fun `ordinær - mai 2022`() {
            ordinær.forMåned(mai(2022)).let {
                it shouldBe FullSupplerendeStønadForMåned(
                    måned = mai(2022),
                    satskategori = Satskategori.ORDINÆR,
                    grunnbeløp = GrunnbeløpForMåned(
                        måned = mai(2022),
                        grunnbeløpPerÅr = 107099,
                        ikrafttredelse = 1.mai(2022),
                    ),
                    minsteÅrligYtelseForUføretrygdede = MinsteÅrligYtelseForUføretrygdedeForMåned(
                        faktor = Faktor(2.28),
                        satsKategori = Satskategori.ORDINÆR,
                        ikrafttredelse = 1.januar(2015),
                        måned = mai(2022),
                    ),
                    toProsentAvHøyForMåned = BigDecimal("442.6758666666666666666666666666667"), // 2.48 * G2022-5 * 0.02 / 12
                )
                it.satsPerÅr shouldBe BigDecimal("244185.72") // 2.28 * G2022-5
                it.satsForMåned shouldBe BigDecimal("20348.81") // 2.28 * G2022-5 / 12
                it.satsForMånedAvrundet shouldBe 20349
                it.satsForMånedAsDouble shouldBe 20348.81
                it.ikrafttredelse shouldBe 1.mai(2022)
                it.toProsentAvHøyForMånedAvrundet shouldBe 443
            }
        }

        @Test
        fun `høy - mai 2022`() {
            høy.forMåned(mai(2022)).let {
                it shouldBe FullSupplerendeStønadForMåned(
                    måned = mai(2022),
                    satskategori = Satskategori.HØY,
                    grunnbeløp = GrunnbeløpForMåned(
                        måned = mai(2022),
                        grunnbeløpPerÅr = 107099,
                        ikrafttredelse = 1.mai(2022),
                    ),
                    minsteÅrligYtelseForUføretrygdede = MinsteÅrligYtelseForUføretrygdedeForMåned(
                        faktor = Faktor(2.48),
                        satsKategori = Satskategori.HØY,
                        ikrafttredelse = 1.januar(2015),
                        måned = mai(2022),
                    ),
                    toProsentAvHøyForMåned = BigDecimal("442.6758666666666666666666666666667"), // 2.48 * G2022-5 * 0.02 / 12
                )
                it.satsPerÅr shouldBe BigDecimal("265605.52") // 2.48 * G2022-5
                it.satsForMåned.scaleTo4() shouldBe BigDecimal("22133.7933") // 2.48 * G2022-5 / 12
                it.satsForMånedAvrundet shouldBe 22134
                it.satsForMånedAsDouble shouldBe 22133.793333333335
                it.ikrafttredelse shouldBe 1.mai(2022)
                it.toProsentAvHøyForMånedAvrundet shouldBe 443
            }
        }

        @Test
        fun `finn siste g-endringsdato for 2021-04-30`() {
            val expectedIkrafttredelse = 1.mai(2020)
            høy.forMåned(
                april(2021),
            ).let {
                it.grunnbeløp.ikrafttredelse shouldBe expectedIkrafttredelse
                it.ikrafttredelse shouldBe expectedIkrafttredelse
            }
        }

        @Test
        fun `finn siste g-endringsdato for 2021-05-01`() {
            val expectedIkrafttredelse = LocalDate.of(2021, Month.MAY, 1)
            høy.forMåned(
                mai(2021),
            ).let {
                it.grunnbeløp.ikrafttredelse shouldBe expectedIkrafttredelse
                it.ikrafttredelse shouldBe expectedIkrafttredelse
            }
        }
    }
}
