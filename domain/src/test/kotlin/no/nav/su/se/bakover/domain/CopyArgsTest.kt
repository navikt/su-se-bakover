package no.nav.su.se.bakover.domain

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.periode.Periode
import org.junit.jupiter.api.Test

internal class CopyArgsTest {
    @Test
    fun `bevarer original periode dersom maks inneholder original`() {
        CopyArgs.BegrensetTil(Periode.create(1.januar(2021), 31.desember(2021)))
            .begrensTil(Periode.create(1.juli(2021), 31.juli(2021))) shouldBe Periode.create(1.juli(2021), 31.juli(2021))
    }

    @Test
    fun `justerer original periode dersom original inneholder maks`() {
        CopyArgs.BegrensetTil(Periode.create(1.juli(2021), 31.juli(2021)))
            .begrensTil(Periode.create(1.januar(2021), 31.desember(2021))) shouldBe Periode.create(1.juli(2021), 31.juli(2021))
    }

    @Test
    fun `returnerer ingenting hvis det ikke er overlapp mellom maks og original`() {
        CopyArgs.BegrensetTil(Periode.create(1.juli(2021), 31.juli(2021)))
            .begrensTil(Periode.create(1.desember(2021), 31.desember(2021))) shouldBe null
    }

    @Test
    fun `justerer fraOgMed hvis original starter før maks`() {
        CopyArgs.BegrensetTil(Periode.create(1.juli(2021), 31.desember(2021)))
            .begrensTil(Periode.create(1.januar(2021), 31.desember(2021))) shouldBe Periode.create(1.juli(2021), 31.desember(2021))
    }

    @Test
    fun `justerer tilOgMed hvis original slutter etter maks`() {
        CopyArgs.BegrensetTil(Periode.create(1.januar(2021), 31.juli(2021)))
            .begrensTil(Periode.create(1.januar(2021), 31.desember(2021))) shouldBe Periode.create(1.januar(2021), 31.juli(2021))
    }

    @Test
    fun `justerer fraOgMed hvis original starter før og slutter før maks`() {
        CopyArgs.BegrensetTil(Periode.create(1.juli(2021), 31.desember(2021)))
            .begrensTil(Periode.create(1.januar(2021), 31.oktober(2021))) shouldBe Periode.create(1.juli(2021), 31.oktober(2021))
    }

    @Test
    fun `justerer tilOgMed hvis original starter seneere enn og slutter etter maks`() {
        CopyArgs.BegrensetTil(Periode.create(1.januar(2021), 31.juli(2021)))
            .begrensTil(Periode.create(1.mars(2021), 31.desember(2021))) shouldBe Periode.create(1.mars(2021), 31.juli(2021))
    }
}
