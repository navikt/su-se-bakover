package no.nav.su.se.bakover.domain.beregning

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.IkkePeriodisertFradrag
import no.nav.su.se.bakover.domain.fixedTidspunkt
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class VurderOmBeregningHarEndringerIYtelseTest {
    private val tidligerePeriode = Periode.create(fraOgMed = 1.januar(2020), tilOgMed = 31.desember(2020))
    private val tidligereBeregning = (
        BeregningFactory.ny(
            periode = tidligerePeriode,
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    opprettet = fixedTidspunkt,
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = tidligerePeriode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
            begrunnelse = null,
        )
        ) as BeregningMedFradragBeregnetMånedsvis

    private val nyPeriode = Periode.create(1.juni(2020), 31.desember(2020))
    private val nyBeregning = (
        BeregningFactory.ny(
            periode = nyPeriode,
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    opprettet = fixedTidspunkt,
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = nyPeriode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
            begrunnelse = null,
        )
        ) as BeregningMedFradragBeregnetMånedsvis

    @Test
    fun `kaster exception hvis tidligereBeregning ikke inneholder nyBeregning`() {
        val beregningUtanforPeriode: Beregning = mock {
            on { periode } doReturn Periode.create(fraOgMed = 1.juni(2020), tilOgMed = 31.januar(2021))
        }

        assertThrows<AssertionError> {
            VurderOmBeregningHarEndringerIYtelse(
                tidligereBeregning,
                beregningUtanforPeriode,
            )
        }
    }

    @Test
    fun `skal bli true for to like beregninger`() {
        VurderOmBeregningHarEndringerIYtelse(tidligereBeregning, tidligereBeregning).resultat shouldBe false
    }

    @Test
    fun `skal bli false for en nyBeregning som er ett subset av en tidlegereBeregning`() {
        VurderOmBeregningHarEndringerIYtelse(tidligereBeregning, nyBeregning).resultat shouldBe false
    }

    @Test
    fun `skal bli true for en nyBeregning som ikke er ett subset av en tidlegereBeregning`() {
        val nyOgEndretBeregning = nyBeregning.copy(
            fradrag = nyBeregning.getFradrag()
                .map { (it as IkkePeriodisertFradrag).copy(månedsbeløp = it.månedsbeløp + 1) },
        )

        VurderOmBeregningHarEndringerIYtelse(tidligereBeregning, nyOgEndretBeregning).resultat shouldBe true
    }

    @Test
    fun `skal bli true for samme ytelseSum men forskjellige utbetalinger per måned`() {
        val periode1 = Periode.create(1.januar(2020), 31.januar(2020))
        val periode2 = Periode.create(1.februar(2020), 29.februar(2020))

        val b1 = BeregningFactory.ny(
            periode = Periode.create(periode1.fraOgMed, periode2.tilOgMed),
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    opprettet = fixedTidspunkt,
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 1000.0,
                    periode = periode1,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.ny(
                    opprettet = fixedTidspunkt,
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode2,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
            begrunnelse = null,
        )

        val b2 = BeregningFactory.ny(
            periode = Periode.create(periode1.fraOgMed, periode2.tilOgMed),
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    opprettet = fixedTidspunkt,
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode1,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.ny(
                    opprettet = fixedTidspunkt,
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 1000.0,
                    periode = periode2,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
            begrunnelse = null,
        )

        VurderOmBeregningHarEndringerIYtelse(b1, b2).resultat shouldBe true
    }
}
