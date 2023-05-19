package no.nav.su.se.bakover.domain

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.juli
import no.nav.su.se.bakover.common.extensions.mars
import no.nav.su.se.bakover.common.extensions.oktober
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.juli
import no.nav.su.se.bakover.common.tid.periode.år
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
