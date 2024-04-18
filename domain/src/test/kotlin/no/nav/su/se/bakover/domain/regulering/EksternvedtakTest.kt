package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.domain.tid.april
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.domain.tid.januar
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
}
