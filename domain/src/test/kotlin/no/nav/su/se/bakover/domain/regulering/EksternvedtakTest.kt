package no.nav.su.se.bakover.domain.regulering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.tid.april
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.domain.regulering.supplement.overlapper
import no.nav.su.se.bakover.test.nyEksternvedtakEndring
import no.nav.su.se.bakover.test.nyEksternvedtakRegulering
import no.nav.su.se.bakover.test.nyFradragperiodeEndring
import no.nav.su.se.bakover.test.nyFradragperiodeRegulering
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class EksternvedtakTest {

    @Nested
    inner class requires {
        @Test
        fun `alle fradragene må ha samme fraOgMed`() {
            assertThrows<IllegalArgumentException> {
                nyEksternvedtakRegulering(
                    fradrag = listOf(
                        nyFradragperiodeRegulering(fraOgMed = 1.januar(2021)),
                        nyFradragperiodeRegulering(fraOgMed = 1.februar(2021)),
                    ),
                )
            }

            assertThrows<IllegalArgumentException> {
                nyEksternvedtakEndring(
                    fradrag = listOf(
                        nyFradragperiodeEndring(fraOgMed = 1.januar(2021)),
                        nyFradragperiodeEndring(fraOgMed = 1.februar(2021)),
                    ),
                )
            }

            assertDoesNotThrow {
                nyEksternvedtakRegulering(fradrag = listOf(nyFradragperiodeRegulering(), nyFradragperiodeRegulering()))
                nyEksternvedtakEndring(fradrag = listOf(nyFradragperiodeEndring(), nyFradragperiodeEndring()))
            }
        }

        @Test
        fun `alle fradragene må ha samme tilOgMed`() {
            assertThrows<IllegalArgumentException> {
                nyEksternvedtakEndring(
                    fradrag = listOf(
                        nyFradragperiodeEndring(tilOgMed = 30.april(2021)),
                        nyFradragperiodeEndring(tilOgMed = 29.april(2021)),
                    ),
                )
            }

            assertDoesNotThrow {
                nyEksternvedtakEndring(
                    fradrag = listOf(
                        nyFradragperiodeEndring(tilOgMed = 30.april(2021)),
                        nyFradragperiodeEndring(tilOgMed = 30.april(2021)),
                    ),
                )
            }
        }

        @Test
        fun `alle fradragene må ha samme beløp`() {
            assertThrows<IllegalArgumentException> {
                nyEksternvedtakRegulering(
                    fradrag = listOf(nyFradragperiodeRegulering(), nyFradragperiodeRegulering(beløp = 2000)),
                )
            }

            assertThrows<IllegalArgumentException> {
                nyEksternvedtakEndring(
                    fradrag = listOf(nyFradragperiodeEndring(), nyFradragperiodeEndring(beløp = 2000)),
                )
            }

            assertDoesNotThrow {
                nyEksternvedtakRegulering(fradrag = listOf(nyFradragperiodeRegulering(), nyFradragperiodeRegulering()))
                nyEksternvedtakEndring(fradrag = listOf(nyFradragperiodeEndring(), nyFradragperiodeEndring()))
            }
        }

        @Test
        fun `alle fradragene må ha samme vedtakstype`() {
            assertThrows<IllegalArgumentException> {
                nyEksternvedtakRegulering(fradrag = listOf(nyFradragperiodeEndring(), nyFradragperiodeRegulering()))
            }
            assertThrows<IllegalArgumentException> {
                nyEksternvedtakEndring(fradrag = listOf(nyFradragperiodeEndring(), nyFradragperiodeRegulering()))
            }
            assertDoesNotThrow {
                nyEksternvedtakRegulering(fradrag = listOf(nyFradragperiodeRegulering(), nyFradragperiodeRegulering()))
                nyEksternvedtakEndring(fradrag = listOf(nyFradragperiodeEndring(), nyFradragperiodeEndring()))
            }
        }
    }

    @Test
    fun `overlapper (ikke) med et annet ekstern vedtak`() {
        val v1 = nyEksternvedtakEndring()
        val v2 = nyEksternvedtakEndring()
        val v3 = nyEksternvedtakEndring(måned = april(2022))
        v1.overlapper(v2) shouldBe true
        v1.overlapper(v3) shouldBe false
    }

    @Test
    fun `enkelt vedtak overlapper (ikke) mot liste av eksterne vedtak`() {
        val v1 = nyEksternvedtakEndring()
        val v2 = nyEksternvedtakEndring()
        val v3 = nyEksternvedtakEndring(måned = april(2022))

        v1.overlapper(listOf(v2, v3)) shouldBe true
        v1.overlapper(listOf(v3)) shouldBe false
    }

    @Test
    fun `sjekker om listen av eksterne vedtak inneholder overlapp`() {
        val v1 = nyEksternvedtakEndring()
        val v2 = nyEksternvedtakEndring()
        val v3 = nyEksternvedtakEndring(måned = april(2022))

        listOf(v1).overlapper() shouldBe false
        listOf(v1, v2).overlapper() shouldBe true
        listOf(v1, v3).overlapper() shouldBe false
    }
}
