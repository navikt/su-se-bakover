package no.nav.su.se.bakover.domain.regulering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.test.avsluttetRegulering
import no.nav.su.se.bakover.test.iverksattAutomatiskRegulering
import no.nav.su.se.bakover.test.opprettetRegulering
import org.junit.jupiter.api.Test

internal class ReguleringTest {

    internal class erÅpen {
        @Test
        fun `opprettet skal være åpen`() {
            opprettetRegulering().erÅpen() shouldBe true
        }

        @Test
        fun `iverksatt skal ikke være åpen`() {
            iverksattAutomatiskRegulering().erÅpen() shouldBe false
        }

        @Test
        fun `avsluttet skal ikke være åpen`() {
            avsluttetRegulering().erÅpen() shouldBe false
        }
    }
}
