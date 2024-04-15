package no.nav.su.se.bakover.common.domain.tid.periode

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.tid.periode.januar
import org.junit.jupiter.api.Test

internal class SlåttSammenIkkeOverlappendePerioderTest {

    @Test
    fun `equals - tom liste gir EmptyPerioder`() {
        SlåttSammenIkkeOverlappendePerioder.create(emptyList()) shouldBe EmptyPerioder
    }

    @Test
    fun `equals - 1 element gir NonEmptySlåttSammenIkkeOverlappendePerioder`() {
        SlåttSammenIkkeOverlappendePerioder.create(nonEmptyListOf(januar(2021))) shouldBe NonEmptySlåttSammenIkkeOverlappendePerioder.create(
            nonEmptyListOf(januar(2021)),
        )
    }
}
