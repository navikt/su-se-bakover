package no.nav.su.se.bakover.common.domain.periode

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.extensions.april
import no.nav.su.se.bakover.common.extensions.august
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.juli
import no.nav.su.se.bakover.common.extensions.juni
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.extensions.mars
import no.nav.su.se.bakover.common.extensions.november
import no.nav.su.se.bakover.common.extensions.oktober
import no.nav.su.se.bakover.common.extensions.september
import no.nav.su.se.bakover.common.tid.periode.DatoIntervall
import no.nav.su.se.bakover.common.tid.periode.dager
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.år
import org.junit.jupiter.api.Test

internal class DatoIntervall_dagerTest {

    @Test
    fun `ingen dager`() {
        listOf<DatoIntervall>().dager() shouldBe emptyList()
    }

    @Test
    fun `1 dag`() {
        DatoIntervall(1.januar(2022), 1.januar(2022)).dager() shouldBe listOf(1.januar(2022))

        listOf(
            DatoIntervall(1.januar(2022), 1.januar(2022)),
            DatoIntervall(1.januar(2022), 1.januar(2022)),
        ).dager() shouldBe listOf(1.januar(2022))
    }

    @Test
    fun `2 dager`() {
        DatoIntervall(1.januar(2022), 2.januar(2022)).dager() shouldBe
            listOf(1.januar(2022), 2.januar(2022))

        listOf(
            DatoIntervall(1.januar(2022), 1.januar(2022)),
            DatoIntervall(1.januar(2022), 2.januar(2022)),
            DatoIntervall(2.januar(2022), 2.januar(2022)),
        ).dager() shouldBe listOf(1.januar(2022), 2.januar(2022))

        listOf(
            DatoIntervall(1.januar(2022), 1.januar(2022)),
            DatoIntervall(2.januar(2022), 2.januar(2022)),
        ).dager() shouldBe listOf(1.januar(2022), 2.januar(2022))
    }

    @Test
    fun `1 måned`() {
        januar(2022).dager() shouldBe
            (1..31).map { it.januar(2022) }
    }

    @Test
    fun `1 år`() {
        val expected = (1..31).map { it.januar(2022) } +
            (1..28).map { it.februar(2022) } +
            (1..31).map { it.mars(2022) } +
            (1..30).map { it.april(2022) } +
            (1..31).map { it.mai(2022) } +
            (1..30).map { it.juni(2022) } +
            (1..31).map { it.juli(2022) } +
            (1..31).map { it.august(2022) } +
            (1..30).map { it.september(2022) } +
            (1..31).map { it.oktober(2022) } +
            (1..30).map { it.november(2022) } +
            (1..31).map { it.desember(2022) }
        år(2022).dager() shouldBe expected
        listOf(
            år(2022),
            januar(2022),
            DatoIntervall(3.juli(2022), 3.juli(2022)),
            DatoIntervall(7.februar(2022), 18.august(2022)),
        ).dager() shouldBe expected
    }
}
