package no.nav.su.se.bakover.common.domain.tid.periode

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.juli
import no.nav.su.se.bakover.common.domain.tid.mars
import no.nav.su.se.bakover.common.domain.tid.september
import no.nav.su.se.bakover.common.tid.periode.Periode
import org.junit.jupiter.api.Test

class PeriodeMedOptionalTilOgMedTest {

    @Test
    fun `overlapper med en basic periode`() {
        val p1 = PeriodeMedOptionalTilOgMed(1.januar(2021), 31.desember(2021))
        val p2 = Periode.create(1.mars(2021), 31.juli(2021))
        val p3 = Periode.create(1.september(2022), 31.desember(2022))

        p1.overlapper(p2) shouldBe true
        p1.overlapper(p3) shouldBe false
    }

    @Test
    fun `overlapper med en basic liste av perioder`() {
        val p1 = PeriodeMedOptionalTilOgMed(1.januar(2021), 31.desember(2021))
        val listeAvPerioder = listOf(
            Periode.create(1.januar(2021), 31.januar(2021)),
            Periode.create(1.mars(2021), 31.juli(2021)),
            Periode.create(1.september(2022), 31.desember(2022)),
        )

        p1.overlapper(listeAvPerioder) shouldBe true
    }

    @Test
    fun `overlapper med en optionalTilOgMed periode`() {
        val p1 = PeriodeMedOptionalTilOgMed(1.januar(2021), 31.desember(2021))
        val p2 = PeriodeMedOptionalTilOgMed(1.mars(2021), 31.juli(2021))
        val p3 = PeriodeMedOptionalTilOgMed(1.september(2022), 31.desember(2022))
        val p4 = PeriodeMedOptionalTilOgMed(1.januar(2021), null)
        val p5 = PeriodeMedOptionalTilOgMed(1.januar(2022), null)
        p1.overlapper(p2) shouldBe true
        p1.overlapper(p3) shouldBe false
        p1.overlapper(p4) shouldBe true
        p1.overlapper(p5) shouldBe false
    }

    @Test
    fun `overlapper med en basic liste av optionalTilOgMed`() {
        val p1 = PeriodeMedOptionalTilOgMed(1.januar(2021), 31.desember(2021))
        val listeAvPerioder = listOf(
            PeriodeMedOptionalTilOgMed(1.mars(2021), 31.juli(2021)),
            PeriodeMedOptionalTilOgMed(1.september(2022), 31.desember(2022)),
            PeriodeMedOptionalTilOgMed(1.januar(2021), null),
            PeriodeMedOptionalTilOgMed(1.januar(2022), null),
        )

        p1.overlapper(listeAvPerioder) shouldBe true
    }
}
