package no.nav.su.se.bakover.common.domain.tid.periode

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.tid.april
import no.nav.su.se.bakover.common.domain.tid.august
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.juli
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.domain.tid.mars
import no.nav.su.se.bakover.common.domain.tid.oktober
import no.nav.su.se.bakover.common.domain.tid.september
import no.nav.su.se.bakover.common.tid.periode.DatoIntervall
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.inneholder
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.juli
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.common.tid.periode.oktober
import no.nav.su.se.bakover.common.tid.periode.år
import org.junit.jupiter.api.Test

internal class DatoIntervallTest {

    @Test
    fun inneholder() {
        DatoIntervall(
            1.oktober(2022),
            10.oktober(2022),
        ) inneholder DatoIntervall(
            1.oktober(2022),
            10.oktober(2022),
        ) shouldBe true

        DatoIntervall(
            1.oktober(2022),
            10.oktober(2022),
        ) inneholder DatoIntervall(
            2.oktober(2022),
            10.oktober(2022),
        ) shouldBe true
    }

    @Test
    fun `periode inneholder en annen periode`() {
        år(2021) inneholder januar(2021) shouldBe true
        år(2021) inneholder år(2021) shouldBe true
        år(2021) inneholder desember(2021) shouldBe true
        år(2021) inneholder Periode.create(
            1.juli(2021),
            31.august(2021),
        ) shouldBe true
        år(2022) inneholder Periode.create(
            1.juli(2021),
            31.august(2021),
        ) shouldBe false
        år(2021) inneholder Periode.create(
            1.juli(2022),
            31.august(2022),
        ) shouldBe false
    }

    @Test
    fun `inneholder alle`() {
        år(2021).inneholder(
            listOf(
                januar(2021),
                februar(2021),
                Periode.create(1.mars(2021), 31.desember(2021)),
            ),
        ) shouldBe true

        år(2021).inneholder(
            listOf(
                januar(2021),
                februar(2021),
                Periode.create(1.mars(2021), 31.desember(2021)),
                januar(2022),
            ),
        ) shouldBe false

        år(2021).inneholder(
            emptyList(),
        ) shouldBe true

        listOf(
            januar(2021),
        ).inneholder(
            listOf(
                januar(2021),
                januar(2021),
            ),
        ) shouldBe true

        listOf(
            år(2021),
        ).inneholder(
            listOf(
                år(2021),
                år(2021),
            ),
        ) shouldBe true

        listOf(
            år(2021),
            år(2021),
        ).inneholder(
            listOf(
                år(2021),
            ),
        ) shouldBe true

        emptyList<Periode>().inneholder(
            listOf(
                år(2021),
            ),
        ) shouldBe false

        listOf(
            Periode.create(1.mai(2021), 31.januar(2022)),
            Periode.create(1.februar(2022), 30.april(2022)),
        ).inneholder(
            listOf(
                Periode.create(1.mai(2021), 31.desember(2021)),
                Periode.create(1.mars(2022), 30.april(2022)),
            ),
        ) shouldBe true
    }

    @Test
    fun `Liste med perioder inneholder annen liste med perioder`() {
        emptyList<DatoIntervall>().let { it inneholder it } shouldBe true
        listOf(januar(2021)) inneholder listOf(januar(2021)) shouldBe true
        listOf(januar(2021)) inneholder listOf(februar(2021)) shouldBe false
        listOf(februar(2021)) inneholder listOf(januar(2021)) shouldBe false
        listOf(januar(2021)..februar(2021)) inneholder listOf(januar(2021)..februar(2021)) shouldBe true
        listOf(januar(2021)) inneholder listOf(januar(2021)..februar(2021)) shouldBe false

        listOf(
            januar(2021)..juni(2021),
            juli(2021)..desember(2021),
        ) inneholder listOf(
            januar(2021)..februar(2021),
            januar(2021)..oktober(2021),
            juli(2021)..desember(2021),
        ) shouldBe true
    }

    @Test
    fun `periode inneholder dato`() {
        januar(2021) inneholder 15.januar(2021) shouldBe true
        januar(2021) inneholder 15.desember(2021) shouldBe false
        år(2021) inneholder 15.januar(2021) shouldBe true
        år(2021) inneholder 15.desember(2021) shouldBe true
    }

    @Test
    fun overlapper() {
        DatoIntervall(
            1.oktober(2022),
            10.oktober(2022),
        ) overlapper DatoIntervall(
            1.oktober(2022),
            10.oktober(2022),
        ) shouldBe true

        DatoIntervall(
            1.oktober(2022),
            10.oktober(2022),
        ) overlapper DatoIntervall(
            2.oktober(2022),
            10.oktober(2022),
        ) shouldBe true
    }

    @Test
    fun overlapperExcludingEndDate() {
        DatoIntervall(
            1.oktober(2022),
            1.oktober(2022),
        ) overlapperExcludingEndDate DatoIntervall(
            1.oktober(2022),
            1.oktober(2022),
        ) shouldBe false

        DatoIntervall(
            1.oktober(2022),
            2.oktober(2022),
        ) overlapperExcludingEndDate DatoIntervall(
            1.oktober(2022),
            2.oktober(2022),
        ) shouldBe true

        DatoIntervall(
            1.oktober(2022),
            2.oktober(2022),
        ) overlapperExcludingEndDate DatoIntervall(
            2.oktober(2022),
            3.oktober(2022),
        ) shouldBe false

        DatoIntervall(
            2.oktober(2022),
            3.oktober(2022),

        ) overlapperExcludingEndDate DatoIntervall(
            1.oktober(2022),
            2.oktober(2022),
        ) shouldBe false

        DatoIntervall(
            2.oktober(2022),
            3.oktober(2022),
        ) overlapperExcludingEndDate DatoIntervall(
            2.oktober(2022),
            2.oktober(2022),
        ) shouldBe false

        DatoIntervall(
            1.oktober(2022),
            1.oktober(2022),
        ) overlapperExcludingEndDate DatoIntervall(
            1.oktober(2022),
            2.oktober(2022),
        ) shouldBe false

        DatoIntervall(
            1.oktober(2022),
            2.oktober(2022),
        ) overlapperExcludingEndDate DatoIntervall(
            1.oktober(2022),
            1.oktober(2022),
        ) shouldBe false

        DatoIntervall(
            1.oktober(2022),
            3.oktober(2022),
        ) overlapperExcludingEndDate DatoIntervall(
            1.oktober(2022),
            2.oktober(2022),
        ) shouldBe true

        DatoIntervall(
            1.oktober(2022),
            2.oktober(2022),
        ) overlapperExcludingEndDate DatoIntervall(
            1.oktober(2022),
            3.oktober(2022),
        ) shouldBe true

        DatoIntervall(
            1.oktober(2022),
            1.oktober(2022),
        ) overlapperExcludingEndDate DatoIntervall(
            2.oktober(2022),
            2.oktober(2022),
        ) shouldBe false

        DatoIntervall(
            2.oktober(2022),
            2.oktober(2022),
        ) overlapperExcludingEndDate DatoIntervall(
            1.oktober(2022),
            1.oktober(2022),
        ) shouldBe false

        DatoIntervall(
            1.oktober(2022),
            1.oktober(2022),
        ) overlapperExcludingEndDate DatoIntervall(
            30.september(2022),
            2.oktober(2022),
        ) shouldBe true
    }
}
