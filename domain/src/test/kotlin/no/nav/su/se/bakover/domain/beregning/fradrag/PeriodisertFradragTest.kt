package no.nav.su.se.bakover.domain.beregning.fradrag

import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class PeriodisertFradragTest {
    @Test
    fun `periodiserte fradrag kan kun opprettes for en enkelt måned`() {
        assertThrows<IllegalArgumentException> {
            PeriodisertFradrag(
                type = Fradragstype.Arbeidsinntekt,
                månedsbeløp = 2000.0,
                periode = Periode(1.januar(2020), 30.april(2002)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER
            )
        }
    }
}
