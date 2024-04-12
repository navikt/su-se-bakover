package no.nav.su.se.bakover.common.domain.tid.periode

import arrow.core.nonEmptyListOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.common.tid.periode.år
import org.junit.jupiter.api.Test

internal class NonEmptySlåttSammenIkkeOverlappendePerioderTest {
    private val periodeJan = Periode.create(1.januar(2021), 31.januar(2021))
    private val periodeJanTilFeb = januar(2021)..februar(2021)
    private val perioderJanOgFeb = nonEmptyListOf(januar(2021), februar(2021))

    @Test
    fun `kaster hvis vi har overlappende perioder`() {
        shouldThrow<IllegalArgumentException> {
            NonEmptySlåttSammenIkkeOverlappendePerioder.create(januar(2021), januar(2021))
        }.message shouldBe "Periodene skal ikke overlappe, men var: NonEmptyList(2021-01, 2021-01)"

        shouldThrow<IllegalArgumentException> {
            NonEmptySlåttSammenIkkeOverlappendePerioder.create(januar(2021)..februar(2021), februar(2021))
        }.message shouldBe "Periodene skal ikke overlappe, men var: NonEmptyList(Periode(fraOgMed=2021-01-01, tilOgMed=2021-02-28), 2021-02)"

        shouldThrow<IllegalArgumentException> {
            NonEmptySlåttSammenIkkeOverlappendePerioder.create(januar(2021)..februar(2021), februar(2021)..mars(2021))
        }.message shouldBe "Periodene skal ikke overlappe, men var: NonEmptyList(Periode(fraOgMed=2021-01-01, tilOgMed=2021-02-28), Periode(fraOgMed=2021-02-01, tilOgMed=2021-03-31))"
    }

    @Test
    fun `kaster hvis periodene ikke er slått sammen`() {
        shouldThrow<IllegalArgumentException> {
            NonEmptySlåttSammenIkkeOverlappendePerioder.create(februar(2021), januar(2021))
        }.message shouldBe "Tilstøtende perioder skal være slått sammen, men var: NonEmptyList(2021-02, 2021-01)"
    }

    @Test
    fun `kaster hvis periodene ikke er sortert`() {
        shouldThrow<IllegalArgumentException> {
            NonEmptySlåttSammenIkkeOverlappendePerioder.create(mars(2021), januar(2021))
        }.message shouldBe "Periodene skal være sortert på fraOgMed, men var NonEmptyList(2021-03, 2021-01)"
    }

    @Test
    fun `equals - to like NonEmptySlåttSammenIkkeOverlappendePerioder`() {
        NonEmptySlåttSammenIkkeOverlappendePerioder.create(januar(2021)) shouldBe
            NonEmptySlåttSammenIkkeOverlappendePerioder.create((januar(2021)))

        NonEmptySlåttSammenIkkeOverlappendePerioder.create(periodeJanTilFeb) shouldBe NonEmptySlåttSammenIkkeOverlappendePerioder.create(
            periodeJanTilFeb,
        )

        NonEmptySlåttSammenIkkeOverlappendePerioder.create(periodeJan) shouldBe
            NonEmptySlåttSammenIkkeOverlappendePerioder.create(januar(2021))

        NonEmptySlåttSammenIkkeOverlappendePerioder.create(januar(2021)) shouldBe
            NonEmptySlåttSammenIkkeOverlappendePerioder.create(periodeJan)

        NonEmptySlåttSammenIkkeOverlappendePerioder.create(år(2021)) shouldBe
            NonEmptySlåttSammenIkkeOverlappendePerioder.create(år(2021))

        NonEmptySlåttSammenIkkeOverlappendePerioder.create(år(2021)) shouldBe
            NonEmptySlåttSammenIkkeOverlappendePerioder.create(januar(2021)..desember(2021))

        NonEmptySlåttSammenIkkeOverlappendePerioder.create(januar(2021)..desember(2021)) shouldBe
            NonEmptySlåttSammenIkkeOverlappendePerioder.create(januar(2021)..desember(2021))
    }

    @Test
    fun `equals - NonEmptySlåttSammenIkkeOverlappendePerioder og List Perioder`() {
        NonEmptySlåttSammenIkkeOverlappendePerioder.create(januar(2021)) shouldBe nonEmptyListOf(januar(2021))
        NonEmptySlåttSammenIkkeOverlappendePerioder.create(januar(2021)) shouldBe listOf(januar(2021))

        NonEmptySlåttSammenIkkeOverlappendePerioder.create(periodeJan) shouldBe nonEmptyListOf(periodeJan)
        NonEmptySlåttSammenIkkeOverlappendePerioder.create(periodeJan) shouldBe listOf(periodeJan)

        NonEmptySlåttSammenIkkeOverlappendePerioder.create(januar(2021)) shouldBe nonEmptyListOf(periodeJan)
        NonEmptySlåttSammenIkkeOverlappendePerioder.create(januar(2021)) shouldBe listOf(periodeJan)

        nonEmptyListOf(periodeJan) shouldBe NonEmptySlåttSammenIkkeOverlappendePerioder.create(januar(2021))
        listOf(periodeJan) shouldBe NonEmptySlåttSammenIkkeOverlappendePerioder.create(januar(2021))

        NonEmptySlåttSammenIkkeOverlappendePerioder.create(periodeJan) shouldBe nonEmptyListOf(januar(2021))
        NonEmptySlåttSammenIkkeOverlappendePerioder.create(periodeJan) shouldBe listOf(januar(2021))

        nonEmptyListOf(januar(2021)) shouldBe NonEmptySlåttSammenIkkeOverlappendePerioder.create(periodeJan)
        listOf(januar(2021)) shouldBe NonEmptySlåttSammenIkkeOverlappendePerioder.create(periodeJan)

        NonEmptySlåttSammenIkkeOverlappendePerioder.create(år(2021)) shouldBe listOf(år(2021))
        NonEmptySlåttSammenIkkeOverlappendePerioder.create(år(2021)) shouldBe nonEmptyListOf(år(2021))
    }

    @Test
    fun `not equals - NonEmptySlåttSammenIkkeOverlappendePerioder og NonEmptyIkkeOverlappendePerioder`() {
        NonEmptySlåttSammenIkkeOverlappendePerioder.create(periodeJanTilFeb) shouldNotBe
            NonEmptyIkkeOverlappendePerioder.create(perioderJanOgFeb)
    }

    @Test
    fun `not equals - NonEmptySlåttSammenIkkeOverlappendePerioder og NonEmptyOverlappendePerioder`() {
        NonEmptySlåttSammenIkkeOverlappendePerioder.create(januar(2021)) shouldNotBe NonEmptyOverlappendePerioder.create(
            nonEmptyListOf(januar(2021), januar(2021)),
        )
    }

    @Test
    fun `not equals - NonEmptySlåttSammenIkkeOverlappendePerioder og EmptyPerioder`() {
        NonEmptySlåttSammenIkkeOverlappendePerioder.create(januar(2021)) shouldNotBe EmptyPerioder
    }
}
