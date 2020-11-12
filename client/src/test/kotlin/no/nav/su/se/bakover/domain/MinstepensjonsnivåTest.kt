package no.nav.su.se.bakover.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.september
import org.junit.jupiter.api.Test

internal class MinstepensjonsnivåTest {
    @Test
    fun `ordinært minstepensjonsnivå før sept 2019 skal være udefinert`() {
        shouldThrow<RuntimeException> {
            Minstepensjonsnivå.Ordinær
                .forDato(1.januar(2019))
        }
    }

    @Test
    fun `ordinært minstepensjonsnivå mellom sept 2019 og mai 2020 skal være 181908`() {
        Minstepensjonsnivå.Ordinær
            .forDato(1.januar(2020)) shouldBe 181908

        Minstepensjonsnivå.Ordinær
            .forDato(1.september(2019)) shouldBe 181908

        Minstepensjonsnivå.Ordinær
            .forDato(30.april(2020)) shouldBe 181908
    }

    @Test
    fun `ordinært minstepensjonsnivå etter mai 2020 skal være 183587`() {
        Minstepensjonsnivå.Ordinær
            .forDato(1.mai(2020)) shouldBe 183587

        Minstepensjonsnivå.Ordinær
            .forDato(1.juli(2020)) shouldBe 183587
    }
}
