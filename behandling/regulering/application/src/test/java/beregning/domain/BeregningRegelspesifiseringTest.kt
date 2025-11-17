package beregning.domain

import io.kotest.assertions.failure
import io.kotest.matchers.collections.shouldContainAllIgnoringFields
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifiseringer
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifsering
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.FradragFactory
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.time.YearMonth
import java.util.UUID

class BeregningRegelspesifiseringTest {

    @Test
    fun `uføre uten eps`() {
        val periode = YearMonth.of(2025, 1).let {
            Periode.create(it.atDay(1), it.atEndOfMonth())
        }
        val strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE)

        val result = BeregningFactory(clock = fixedClock).ny(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            fradrag = listOf(
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.ForventetInntekt,
                    månedsbeløp = 55000.0,
                    utenlandskInntekt = null,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.Annet("vant på flaxlodd"),
                    månedsbeløp = 1000.0,
                    utenlandskInntekt = null,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            begrunnelse = "begrunnelse",
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = periode,
                    strategy = strategy,
                ),
            ),
        )

        with(result.getMånedsberegninger().single()) {
            val faktisk = getBenyttetRegler()
            val forventet = listOf(
                Regelspesifiseringer.REGEL_SOSIALSTØNAD_UNDER_2_PROSENT.benyttRegelspesifisering(),
                Regelspesifiseringer.REGEL_MINDRE_ENN_2_PROSENT.benyttRegelspesifisering(),
                Regelspesifiseringer.REGEL_MÅNEDSBEREGNING.benyttRegelspesifisering(),
                Regelspesifiseringer.REGEL_BEREGN_SATS_UFØRE_MÅNED.benyttRegelspesifisering(),
                Regelspesifiseringer.REGEL_UFØRE_FAKTOR.benyttRegelspesifisering(),
            )

            faktisk.forMangeRegler(forventet)
            faktisk.shouldContainAllIgnoringFields(forventet, Regelspesifsering::benyttetTidspunkt)
        }
    }

    // TODO en test for alle permuteringer??
    // TODO egen for sats endringsknekkpunkt? må skille på grunnlag

    private fun List<Regelspesifsering>.forMangeRegler(forventet: List<Regelspesifsering>) {
        if (size > forventet.size) {
            throw failure("Månedsberegning har for mange benytta regelspesifiseringer")
        }
    }
}
