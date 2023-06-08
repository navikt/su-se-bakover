package no.nav.su.se.bakover.domain.oppdrag.simulering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Beløp
import no.nav.su.se.bakover.common.MånedBeløp
import no.nav.su.se.bakover.common.Månedsbeløp
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.test.simulering.simulering
import no.nav.su.se.bakover.test.simulering.simulertMåned
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class TolketSimuleringTest {

    @Nested
    inner class HentTotalUtbetaling {

        @Test
        fun `tomme måneder får beløp 0`() {
            TolketSimulering(
                simulering(
                    simulertePerioder = listOf(
                        simulertMåned(
                            måned = januar(2021),
                        ),
                        simulertMåned(
                            måned = februar(2021),
                            simulerteUtbetalinger = null,
                        ),
                    ),
                ),
            ).hentTotalUtbetaling() shouldBe Månedsbeløp(
                listOf(
                    MånedBeløp(
                        periode = januar(2021),
                        beløp = Beløp(15000),
                    ),

                    MånedBeløp(
                        periode = februar(2021),
                        beløp = Beløp(0),
                    ),
                ),
            )
        }
    }
}
