package no.nav.su.se.bakover.common.domain.tid.periode

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.common.tid.periode.år
import org.junit.jupiter.api.Test

internal class PerioderTest {

    @Test
    fun `create - tom gir EmptyPerioder`() {
        Perioder.create(emptyList()) shouldBe EmptyPerioder
    }

    @Test
    fun `create - 1 element gir NonEmptySlåttSammenIkkeOverlappendePerioder`() {
        Perioder.create(nonEmptyListOf(januar(2021))) shouldBe NonEmptySlåttSammenIkkeOverlappendePerioder.create(
            nonEmptyListOf(januar(2021)),
        )
        Perioder.create(nonEmptyListOf(januar(2021))) shouldBe NonEmptySlåttSammenIkkeOverlappendePerioder.create(
            nonEmptyListOf(Periode.create(1.januar(2021), 31.januar(2021))),
        )
    }

    @Test
    fun `create - 2 overlappende elementer gir NonEmptyOverlappendePerioder`() {
        Perioder.create(nonEmptyListOf(januar(2021), januar(2021))) shouldBe NonEmptyOverlappendePerioder.create(
            nonEmptyListOf(januar(2021), januar(2021)),
        )
        Perioder.create(nonEmptyListOf(januar(2021), januar(2021))) shouldBe NonEmptyOverlappendePerioder.create(
            nonEmptyListOf(Periode.create(1.januar(2021), 31.januar(2021)), januar(2021)),
        )
    }

    @Test
    fun `create - 2 ikke overlappende tilstøtende elementer gir NonEmptyIkkeOverlappendePerioder`() {
        Perioder.create(nonEmptyListOf(januar(2021), februar(2021))) shouldBe NonEmptyIkkeOverlappendePerioder.create(
            nonEmptyListOf(januar(2021), februar(2021)),
        )
        Perioder.create(nonEmptyListOf(januar(2021), februar(2021))) shouldBe NonEmptyIkkeOverlappendePerioder.create(
            nonEmptyListOf(Periode.create(1.januar(2021), 31.januar(2021)), februar(2021)),
        )
    }

    @Test
    fun `create - 2 ikke overlappende ikke tilstøtende elementer gir NonEmptySlåttSammenIkkeOverlappendePerioder`() {
        Perioder.create(nonEmptyListOf(januar(2021))) shouldBe NonEmptySlåttSammenIkkeOverlappendePerioder.create(
            nonEmptyListOf(januar(2021)),
        )
        Perioder.create(nonEmptyListOf(januar(2021))) shouldBe NonEmptySlåttSammenIkkeOverlappendePerioder.create(
            nonEmptyListOf(Periode.create(1.januar(2021), 31.januar(2021))),
        )
        Perioder.create(nonEmptyListOf(januar(2021), mars(2021))) shouldBe NonEmptySlåttSammenIkkeOverlappendePerioder.create(
            nonEmptyListOf(Periode.create(1.januar(2021), 31.januar(2021)), mars(2021)),
        )
        Perioder.create(nonEmptyListOf(år(2021))) shouldBe NonEmptySlåttSammenIkkeOverlappendePerioder.create(
            nonEmptyListOf(år(2021)),
        )
    }
}
