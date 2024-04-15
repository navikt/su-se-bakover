package no.nav.su.se.bakover.common.domain.tid.periode

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.tid.periode.Periode
import org.junit.jupiter.api.Test

internal class IkkeOverlappendePerioderTest {
    @Test
    fun `create - tom gir EmptyPerioder`() {
        IkkeOverlappendePerioder.create(emptyList()) shouldBe EmptyPerioder
    }

    @Test
    fun `create - ikke tom gir IkkeOverlappendePerioder`() {
        IkkeOverlappendePerioder.create(listOf(Periode.create(1.januar(2021), 31.januar(2021)))).shouldBeInstanceOf<IkkeOverlappendePerioder>()
    }
}
