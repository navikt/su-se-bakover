package no.nav.su.se.bakover.common.periode

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.september
import org.junit.jupiter.api.Test

internal class DatoIntervallTest {
    @Test
    fun inneholder() {
        DatoIntervall(
            1.oktober(2022),
            10.oktober(2022),
        ) inneholder DatoIntervall(
            1.oktober(2022),
            10.oktober(2022),
        ) shouldBe true

        DatoIntervall(
            1.oktober(2022),
            10.oktober(2022),
        ) inneholder DatoIntervall(
            2.oktober(2022),
            10.oktober(2022),
        ) shouldBe true
    }

    @Test
    fun overlapper() {
        DatoIntervall(
            1.oktober(2022),
            10.oktober(2022),
        ) overlapper DatoIntervall(
            1.oktober(2022),
            10.oktober(2022),
        ) shouldBe true

        DatoIntervall(
            1.oktober(2022),
            10.oktober(2022),
        ) overlapper DatoIntervall(
            2.oktober(2022),
            10.oktober(2022),
        ) shouldBe true
    }

    @Test
    fun overlapperExcludingEndDate() {
        DatoIntervall(
            1.oktober(2022),
            1.oktober(2022),
        ) overlapperExcludingEndDate DatoIntervall(
            1.oktober(2022),
            1.oktober(2022),
        ) shouldBe false

        DatoIntervall(
            1.oktober(2022),
            2.oktober(2022),
        ) overlapperExcludingEndDate DatoIntervall(
            1.oktober(2022),
            2.oktober(2022),
        ) shouldBe true

        DatoIntervall(
            1.oktober(2022),
            2.oktober(2022),
        ) overlapperExcludingEndDate DatoIntervall(
            2.oktober(2022),
            3.oktober(2022),
        ) shouldBe false

        DatoIntervall(
            2.oktober(2022),
            3.oktober(2022),

        ) overlapperExcludingEndDate DatoIntervall(
            1.oktober(2022),
            2.oktober(2022),
        ) shouldBe false

        DatoIntervall(
            2.oktober(2022),
            3.oktober(2022),
        ) overlapperExcludingEndDate DatoIntervall(
            2.oktober(2022),
            2.oktober(2022),
        ) shouldBe false

        DatoIntervall(
            1.oktober(2022),
            1.oktober(2022),
        ) overlapperExcludingEndDate DatoIntervall(
            1.oktober(2022),
            2.oktober(2022),
        ) shouldBe false

        DatoIntervall(
            1.oktober(2022),
            2.oktober(2022),
        ) overlapperExcludingEndDate DatoIntervall(
            1.oktober(2022),
            1.oktober(2022),
        ) shouldBe false

        DatoIntervall(
            1.oktober(2022),
            3.oktober(2022),
        ) overlapperExcludingEndDate DatoIntervall(
            1.oktober(2022),
            2.oktober(2022),
        ) shouldBe true

        DatoIntervall(
            1.oktober(2022),
            2.oktober(2022),
        ) overlapperExcludingEndDate DatoIntervall(
            1.oktober(2022),
            3.oktober(2022),
        ) shouldBe true

        DatoIntervall(
            1.oktober(2022),
            1.oktober(2022),
        ) overlapperExcludingEndDate DatoIntervall(
            2.oktober(2022),
            2.oktober(2022),
        ) shouldBe false

        DatoIntervall(
            2.oktober(2022),
            2.oktober(2022),
        ) overlapperExcludingEndDate DatoIntervall(
            1.oktober(2022),
            1.oktober(2022),
        ) shouldBe false

        DatoIntervall(
            1.oktober(2022),
            1.oktober(2022),
        ) overlapperExcludingEndDate DatoIntervall(
            30.september(2022),
            2.oktober(2022),
        ) shouldBe true
    }
}
