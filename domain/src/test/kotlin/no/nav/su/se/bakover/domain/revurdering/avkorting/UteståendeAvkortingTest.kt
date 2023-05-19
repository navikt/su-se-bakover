package no.nav.su.se.bakover.domain.revurdering.avkorting

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.test.uhåndtertUteståendeAvkorting
import org.junit.jupiter.api.Test

class UteståendeAvkortingTest {

    @Test
    fun `Revurderingsperiode jan, utestående periode jan-feb forventer left`() {
        val avkorting = uhåndtertUteståendeAvkorting(januar(2021), februar(2021))
        kontrollerAtUteståendeAvkortingRevurderes(januar(2021), avkorting).shouldBeLeft()
    }

    @Test
    fun `Revurderingsperiode feb, utestående periode jan-feb forventer left`() {
        val avkorting = uhåndtertUteståendeAvkorting(januar(2021), februar(2021))
        kontrollerAtUteståendeAvkortingRevurderes(februar(2021), avkorting).shouldBeLeft()
    }

    @Test
    fun `Revurderingsperiode jan-feb, utestående periode jan-feb forventer right`() {
        val avkorting = uhåndtertUteståendeAvkorting(januar(2021), februar(2021))
        kontrollerAtUteståendeAvkortingRevurderes(januar(2021)..mars(2021), avkorting).shouldBeRight()
    }

    @Test
    fun `Revurderingsperiode des-mars, utestående periode jan-feb forventer right`() {
        val avkorting = uhåndtertUteståendeAvkorting(januar(2021), februar(2021))
        kontrollerAtUteståendeAvkortingRevurderes(desember(2020)..mars(2021), avkorting).shouldBeRight()
    }

    @Test
    fun `Revurderingsperiode mars, utestående periode jan-feb forventer right`() {
        val avkorting = uhåndtertUteståendeAvkorting(januar(2021), februar(2021))
        kontrollerAtUteståendeAvkortingRevurderes(mars(2021), avkorting).shouldBeRight()
    }
}
