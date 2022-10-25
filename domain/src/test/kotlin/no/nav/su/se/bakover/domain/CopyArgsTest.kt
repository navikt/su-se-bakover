package no.nav.su.se.bakover.domain

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.application.CopyArgs
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.juli
import no.nav.su.se.bakover.common.periode.år
import org.junit.jupiter.api.Test

internal class CopyArgsTest {
    @Test
    fun `bevarer original periode dersom maks inneholder original`() {
        CopyArgs.Snitt(år(2021))
            .snittFor(juli(2021)) shouldBe juli(2021)
    }

    @Test
    fun `justerer original periode dersom original inneholder maks`() {
        CopyArgs.Snitt(juli(2021))
            .snittFor(år(2021)) shouldBe juli(2021)
    }

    @Test
    fun `returnerer ingenting hvis det ikke er overlapp mellom maks og original`() {
        CopyArgs.Snitt(juli(2021))
            .snittFor(desember(2021)) shouldBe null
    }

    @Test
    fun `justerer fraOgMed hvis original starter før maks`() {
        CopyArgs.Snitt(Periode.create(1.juli(2021), 31.desember(2021)))
            .snittFor(år(2021)) shouldBe Periode.create(1.juli(2021), 31.desember(2021))
    }

    @Test
    fun `justerer tilOgMed hvis original slutter etter maks`() {
        CopyArgs.Snitt(Periode.create(1.januar(2021), 31.juli(2021)))
            .snittFor(år(2021)) shouldBe Periode.create(1.januar(2021), 31.juli(2021))
    }

    @Test
    fun `justerer fraOgMed hvis original starter før og slutter før maks`() {
        CopyArgs.Snitt(Periode.create(1.juli(2021), 31.desember(2021)))
            .snittFor(Periode.create(1.januar(2021), 31.oktober(2021))) shouldBe Periode.create(1.juli(2021), 31.oktober(2021))
    }

    @Test
    fun `justerer tilOgMed hvis original starter seneere enn og slutter etter maks`() {
        CopyArgs.Snitt(Periode.create(1.januar(2021), 31.juli(2021)))
            .snittFor(Periode.create(1.mars(2021), 31.desember(2021))) shouldBe Periode.create(1.mars(2021), 31.juli(2021))
    }
}
