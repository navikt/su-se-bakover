package no.nav.su.se.bakover.common.domain.tid.periode

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.mars
import no.nav.su.se.bakover.common.tid.periode.DatoIntervall
import no.nav.su.se.bakover.test.getOrFail
import org.junit.jupiter.api.Test

internal class DatoIntervall_slåSammenTest {

    @Test
    fun `1 day - DatoIntervall`() {
        val interval = DatoIntervall(1.januar(2022))

        val result = (interval slåSammen interval).getOrFail()

        result shouldBe interval
    }

    @Test
    fun `1 day - LocalDato`() {
        val interval = DatoIntervall(1.januar(2022))

        val result = (interval slåSammen 1.januar(2022)).getOrFail()

        result shouldBe interval
    }

    @Test
    fun `non-overlapping intervals return error - DatoIntervall`() {
        val interval1 = DatoIntervall(1.januar(2022), 31.januar(2022))
        val interval2 = DatoIntervall(1.mars(2022), 31.mars(2022))

        val result = interval1 slåSammen interval2

        result.isLeft() shouldBe true
    }

    @Test
    fun `non-overlapping intervals return error - LocalDate`() {
        val interval1 = DatoIntervall(1.januar(2022), 31.januar(2022))

        val result = interval1 slåSammen 3.mars(2022)

        result.isLeft() shouldBe true
    }

    @Test
    fun `adjacent intervals are merged - DatoIntervall`() {
        val interval1 = DatoIntervall(1.januar(2022), 31.januar(2022))
        val interval2 = DatoIntervall(1.februar(2022), 28.februar(2022))

        val result = (interval1 slåSammen interval2).getOrFail()

        result shouldBe DatoIntervall(1.januar(2022), 28.februar(2022))
    }

    @Test
    fun `adjacent intervals are merged - LocalDate - after`() {
        val interval1 = DatoIntervall(1.januar(2022), 31.januar(2022))

        val result = (interval1 slåSammen 1.februar(2022)).getOrFail()

        result shouldBe DatoIntervall(1.januar(2022), 1.februar(2022))
    }

    @Test
    fun `adjacent intervals are merged - LocalDate - before`() {
        val interval1 = DatoIntervall(1.januar(2022), 31.januar(2022))

        val result = (interval1 slåSammen 31.desember(2021)).getOrFail()

        result shouldBe DatoIntervall(31.desember(2021), 31.januar(2022))
    }

    @Test
    fun `partially overlapping intervals are merged`() {
        val interval1 = DatoIntervall(1.januar(2022), 15.februar(2022))
        val interval2 = DatoIntervall(10.februar(2022), 28.februar(2022))

        val result = (interval1 slåSammen interval2).getOrFail()

        result shouldBe DatoIntervall(1.januar(2022), 28.februar(2022))
    }

    @Test
    fun `fully overlapping intervals - DatoIntervall`() {
        val interval1 = DatoIntervall(1.januar(2022), 31.mars(2022))
        val interval2 = DatoIntervall(1.februar(2022), 28.februar(2022))

        val result = (interval1 slåSammen interval2).getOrFail()

        result shouldBe DatoIntervall(1.januar(2022), 31.mars(2022))
    }

    @Test
    fun `fully overlapping intervals - LocalDate`() {
        val interval1 = DatoIntervall(1.januar(2022), 31.mars(2022))

        val result = (interval1 slåSammen 30.mars(2022)).getOrFail()

        result shouldBe DatoIntervall(1.januar(2022), 31.mars(2022))
    }
}
