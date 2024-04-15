package no.nav.su.se.bakover.common.domain.tid.periode

import arrow.core.nonEmptyListOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import org.junit.jupiter.api.Test

internal class NonEmptyOverlappendePerioderTest {
    private val periodeJan = Periode.create(1.januar(2021), 31.januar(2021))
    private val perioderJanOgJan = nonEmptyListOf(januar(2021), januar(2021))

    @Test
    fun `kan ikke lage NonEmptyOverlappendePerioder med mindre enn to perioder`() {
        shouldThrow<IllegalArgumentException> {
            NonEmptyOverlappendePerioder.create(nonEmptyListOf(januar(2021)))
        }.message shouldBe "NonEmptyOverlappendePerioder krever minst to perioder, men var: NonEmptyList(2021-01)"
    }

    @Test
    fun `kan ikke lage NonEmptyOverlappendePerioder dersom de ikke overlapper`() {
        shouldThrow<IllegalArgumentException> {
            NonEmptyOverlappendePerioder.create(nonEmptyListOf(januar(2021)))
        }.message shouldBe "NonEmptyOverlappendePerioder krever minst to perioder, men var: NonEmptyList(2021-01)"
    }

    @Test
    fun `equals - to like NonEmptyOverlappendePerioder`() {
        NonEmptyOverlappendePerioder.create(perioderJanOgJan) shouldBe
            NonEmptyOverlappendePerioder.create(perioderJanOgJan)

        NonEmptyOverlappendePerioder.create(perioderJanOgJan) shouldBe
            NonEmptyOverlappendePerioder.create(nonEmptyListOf(periodeJan, januar(2021)))

        NonEmptyOverlappendePerioder.create(perioderJanOgJan) shouldNotBe
            NonEmptyOverlappendePerioder.create(nonEmptyListOf(januar(2021), januar(2021), februar(2021)))
    }

    @Test
    fun `equals - NonEmptyOverlappendePerioder og List Perioder`() {
        NonEmptyOverlappendePerioder.create(perioderJanOgJan) shouldBe perioderJanOgJan
        NonEmptyOverlappendePerioder.create(perioderJanOgJan) shouldBe nonEmptyListOf(januar(2021), periodeJan)
        NonEmptyOverlappendePerioder.create(perioderJanOgJan) shouldNotBe nonEmptyListOf(februar(2021), periodeJan)
    }

    @Test
    fun `not equals - NonEmptyOverlappendePerioder og EmptyPerioder`() {
        NonEmptyOverlappendePerioder.create(nonEmptyListOf(januar(2021), januar(2021))) shouldNotBe EmptyPerioder
    }
}
