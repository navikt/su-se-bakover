package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.august
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.juli
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.common.tid.periode.september
import no.nav.su.se.bakover.test.utbetaling.utbetalingslinjePåTidslinjeNy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import økonomi.domain.utbetaling.UtbetalingslinjePåTidslinje
import økonomi.domain.utbetaling.ekvivalentMedInnenforPeriode
import økonomi.domain.utbetaling.krympTilPeriode

internal class UtbetalingslinjePåTidslinjeKtTest {

    @Nested
    inner class KrympTilPeriode {
        @Test
        fun `splitter periode riktig`() {
            listOf(
                utbetalingslinjePåTidslinjeNy(periode = januar(2021)..februar(2021)),
                utbetalingslinjePåTidslinjeNy(periode = mars(2021)..april(2021)),
            ).krympTilPeriode(februar(2021)..mars(2021)).map { it.periode } shouldBe listOf(
                februar(2021),
                mars(2021),
            )
        }

        @Test
        fun `beholder singel originale`() {
            listOf(
                utbetalingslinjePåTidslinjeNy(periode = januar(2021)..februar(2021)),
            ).krympTilPeriode(januar(2021)..april(2021)).map { it.periode } shouldBe listOf(
                januar(2021)..februar(2021),
            )
        }

        @Test
        fun `beholder flere originale`() {
            listOf(
                utbetalingslinjePåTidslinjeNy(periode = januar(2021)..februar(2021)),
                utbetalingslinjePåTidslinjeNy(periode = mars(2021)..april(2021)),
            ).krympTilPeriode(januar(2021)..april(2021)).map { it.periode } shouldBe listOf(
                januar(2021)..februar(2021),
                mars(2021)..april(2021),
            )
        }

        @Test
        fun `fjerner hele før og etter`() {
            listOf(
                utbetalingslinjePåTidslinjeNy(periode = januar(2021)..februar(2021)),
                utbetalingslinjePåTidslinjeNy(periode = mars(2021)..april(2021)),
                utbetalingslinjePåTidslinjeNy(periode = mai(2021)..juni(2021)),
            ).krympTilPeriode(mars(2021)..april(2021))
                .map { it.periode } shouldBe listOf(
                mars(2021)..april(2021),
            )
        }

        @Test
        fun `periode før gir tom liste`() {
            listOf(
                utbetalingslinjePåTidslinjeNy(periode = februar(2021)),
            ).krympTilPeriode(januar(2021))
                .map { it.periode } shouldBe listOf()
        }

        @Test
        fun `periode etter gir tom liste`() {
            listOf(
                utbetalingslinjePåTidslinjeNy(periode = februar(2021)),
            ).krympTilPeriode(mars(2021))
                .map { it.periode } shouldBe listOf()
        }

        @Test
        fun `støtter hull`() {
            listOf(
                utbetalingslinjePåTidslinjeNy(periode = januar(2021)..februar(2021)),
                utbetalingslinjePåTidslinjeNy(periode = mars(2021)..april(2021)),
                utbetalingslinjePåTidslinjeNy(periode = juli(2021)..august(2021)),
                utbetalingslinjePåTidslinjeNy(periode = september(2021)..desember(2021)),
            ).krympTilPeriode(januar(2021)..desember(2021)).map { it.periode } shouldBe listOf(
                januar(2021)..februar(2021),
                mars(2021)..april(2021),
                juli(2021)..august(2021),
                september(2021)..desember(2021),
            )
        }

        @Test
        fun `støtter overlapp`() {
            listOf(
                utbetalingslinjePåTidslinjeNy(periode = januar(2021)..februar(2021)),
                utbetalingslinjePåTidslinjeNy(periode = februar(2021)..mars(2021)),
            ).krympTilPeriode(januar(2021)..mars(2021)).map { it.periode } shouldBe listOf(
                januar(2021)..februar(2021),
                februar(2021)..mars(2021),
            )
        }

        @Test
        fun `støtter usortert periode`() {
            listOf(
                utbetalingslinjePåTidslinjeNy(periode = februar(2021)..mars(2021)),
                utbetalingslinjePåTidslinjeNy(periode = januar(2021)),
            ).krympTilPeriode(januar(2021)..februar(2021)).map { it.periode } shouldBe listOf(
                februar(2021),
                januar(2021),
            )
        }
    }

    @Nested
    inner class EkvivalentMedInnenforPeriode {
        @Test
        fun `samme liste for full periode gir true`() {
            val linjer = listOf(
                utbetalingslinjePåTidslinjeNy(periode = januar(2021)),
                utbetalingslinjePåTidslinjeNy(periode = februar(2021)..mars(2021)),
            )
            linjer.ekvivalentMedInnenforPeriode(
                other = linjer,
                periode = januar(2021)..mars(2021),
            ) shouldBe true
        }

        @Test
        fun `samme liste for større periode gir true`() {
            val linjer = listOf(
                utbetalingslinjePåTidslinjeNy(periode = januar(2021)),
                utbetalingslinjePåTidslinjeNy(periode = februar(2021)..mars(2021)),
            )
            linjer.ekvivalentMedInnenforPeriode(
                other = linjer,
                periode = desember(2020)..april(2021),
            ) shouldBe true
        }

        @Test
        fun `samme liste for mindre periode gir true`() {
            val linjer = listOf(
                utbetalingslinjePåTidslinjeNy(periode = januar(2021)),
                utbetalingslinjePåTidslinjeNy(periode = februar(2021)..mars(2021)),
            )
            linjer.ekvivalentMedInnenforPeriode(
                other = linjer,
                periode = februar(2021),
            ) shouldBe true
        }

        @Test
        fun `samme liste for mindre periode utenfor gir true`() {
            val linjer = listOf(
                utbetalingslinjePåTidslinjeNy(periode = januar(2021)),
                utbetalingslinjePåTidslinjeNy(periode = februar(2021)..mars(2021)),
            )
            linjer.ekvivalentMedInnenforPeriode(
                other = linjer,
                periode = februar(2021),
            ) shouldBe true
        }

        @Test
        fun `tomme lister gir false`() {
            val linjer = emptyList<UtbetalingslinjePåTidslinje>()
            linjer.ekvivalentMedInnenforPeriode(
                other = linjer,
                periode = januar(2021),
            ) shouldBe false
        }

        @Test
        fun `ulike for første periode gir false`() {
            listOf(
                utbetalingslinjePåTidslinjeNy(periode = januar(2021)),
            ).ekvivalentMedInnenforPeriode(
                other = listOf(
                    utbetalingslinjePåTidslinjeNy(periode = februar(2021)),
                ),
                periode = januar(2021),
            ) shouldBe false
        }

        @Test
        fun `ulike for andre periode gir false`() {
            listOf(
                utbetalingslinjePåTidslinjeNy(periode = januar(2021)),
            ).ekvivalentMedInnenforPeriode(
                other = listOf(
                    utbetalingslinjePåTidslinjeNy(periode = februar(2021)),
                ),
                periode = januar(2021),
            ) shouldBe false
        }

        @Test
        fun `ulike for periode utenfor begge gir false`() {
            listOf(
                utbetalingslinjePåTidslinjeNy(periode = januar(2021)),
            ).ekvivalentMedInnenforPeriode(
                other = listOf(
                    utbetalingslinjePåTidslinjeNy(periode = februar(2021)),
                ),
                periode = mars(2021),
            ) shouldBe false
        }
    }
}
