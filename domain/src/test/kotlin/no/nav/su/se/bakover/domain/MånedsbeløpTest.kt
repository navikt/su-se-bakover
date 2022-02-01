package no.nav.su.se.bakover.domain

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.januar
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class MånedsbeløpTest {
    @Test
    fun `bruker absoluttverdien på beløp`() {
        Månedsbeløp(
            listOf(
                MånedBeløp(januar(2021), Beløp(-5000)),
                MånedBeløp(februar(2021), Beløp(1000)),
            ),
        ).sum() shouldBe 6000
    }

    @Test
    fun `kan bare inneholde hver enkelt måned en gang`() {
        assertThrows<IllegalArgumentException> {
            Månedsbeløp(
                listOf(
                    MånedBeløp(januar(2021), Beløp(-5000)),
                    MånedBeløp(januar(2021), Beløp(1000)),
                ),
            )
        }

        assertThrows<IllegalArgumentException> {
            Månedsbeløp(
                listOf(
                    MånedBeløp(januar(2021), Beløp(-5000)),
                    MånedBeløp(Periode.create(1.januar(2021), 31.desember(2021)), Beløp(1000)),
                ),
            )
        }
    }
}
