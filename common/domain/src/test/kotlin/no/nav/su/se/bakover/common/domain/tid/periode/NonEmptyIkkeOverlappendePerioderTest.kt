package no.nav.su.se.bakover.common.domain.tid.periode

import arrow.core.nonEmptyListOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.common.tid.periode.år
import org.junit.jupiter.api.Test

internal class NonEmptyIkkeOverlappendePerioderTest {
    private val perioderJanOgFeb = nonEmptyListOf(januar(2021), februar(2021))

    @Test
    fun `kaster hvis vi har overlappende perioder`() {
        shouldThrow<IllegalArgumentException> {
            NonEmptyIkkeOverlappendePerioder.create(januar(2021), januar(2021), februar(2021))
        }.message shouldBe "Periodene skal ikke overlappe, men var: NonEmptyList(2021-01, 2021-01, 2021-02). Bruk heller NonEmptyOverlappendePerioder"

        shouldThrow<IllegalArgumentException> {
            NonEmptyIkkeOverlappendePerioder.create(januar(2021)..februar(2021), februar(2021))
        }.message shouldBe "Periodene skal ikke overlappe, men var: NonEmptyList(Periode(fraOgMed=2021-01-01, tilOgMed=2021-02-28), 2021-02). Bruk heller NonEmptyOverlappendePerioder"

        shouldThrow<IllegalArgumentException> {
            NonEmptyIkkeOverlappendePerioder.create(januar(2021)..februar(2021), februar(2021)..mars(2021))
        }.message shouldBe "Periodene skal ikke overlappe, men var: NonEmptyList(Periode(fraOgMed=2021-01-01, tilOgMed=2021-02-28), Periode(fraOgMed=2021-02-01, tilOgMed=2021-03-31)). Bruk heller NonEmptyOverlappendePerioder"
    }

    @Test
    fun `gir NonEmptySlåttSammenzkkeOverlappendePerioder dersom periodene allerede er slått sammen`() {
        NonEmptyIkkeOverlappendePerioder.create(januar(2021)..februar(2021)) shouldBe
            NonEmptySlåttSammenIkkeOverlappendePerioder.create(Periode.create(1.januar(2021), 28.februar(2021)))
    }

    @Test
    fun `kaster hvis periodene ikke er sortert`() {
        shouldThrow<IllegalArgumentException> {
            NonEmptyIkkeOverlappendePerioder.create(februar(2021), januar(2021))
        }.message shouldBe "Periodene skal være sortert på fraOgMed, men var NonEmptyList(2021-02, 2021-01)"
    }

    @Test
    fun `equals - to like NonEmptyIkkeOverlappendePerioder`() {
        NonEmptyIkkeOverlappendePerioder.create(perioderJanOgFeb) shouldBe
            NonEmptyIkkeOverlappendePerioder.create(perioderJanOgFeb)

        NonEmptyIkkeOverlappendePerioder.create(
            desember(2020),
            år(2021),
        ) shouldBe NonEmptyIkkeOverlappendePerioder.create(
            desember(2020),
            år(2021),
        )
    }

    @Test
    fun `equals - NonEmptyIkkeOverlappendePerioder og List Perioder`() {
        NonEmptyIkkeOverlappendePerioder.create(perioderJanOgFeb) shouldBe perioderJanOgFeb
        NonEmptyIkkeOverlappendePerioder.create(perioderJanOgFeb) shouldBe listOf<Periode>().plus(perioderJanOgFeb)

        NonEmptyIkkeOverlappendePerioder.create(
            Periode.create(1.januar(2021), 31.januar(2021)),
            februar(2021),
        ) shouldBe listOf(januar(2021), februar(2021))
    }

    @Test
    fun `not equals - NonEmptyIkkeOverlappendePerioder og NonEmptyOverlappendePerioder`() {
        NonEmptyIkkeOverlappendePerioder.create(
            januar(2021),
            februar(2021),
        ) shouldNotBe NonEmptyOverlappendePerioder.create(
            nonEmptyListOf(januar(2021), januar(2021)),
        )
    }

    @Test
    fun `not equals - NonEmptyIkkeOverlappendePerioder og EmptyPerioder`() {
        NonEmptyIkkeOverlappendePerioder.create(januar(2021), februar(2021)) shouldNotBe EmptyPerioder
    }
}
