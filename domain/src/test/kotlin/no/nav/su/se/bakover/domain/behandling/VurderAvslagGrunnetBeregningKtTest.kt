package no.nav.su.se.bakover.domain.behandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.behandling.VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class VurderAvslagGrunnetBeregningKtTest {

    @Test
    fun `beregning som ikke eksisterer kan ikke gi avslag`() {
        vurderAvslagGrunnetBeregning(null) shouldBe AvslagGrunnetBeregning.Nei
    }

    @Test
    fun `beregning med sum ytelse nøyaktig lik null skal gi avslag`() {
        val periode = 1.januar(2021)
        val fradrag = lagFradrag(Sats.HØY.månedsbeløp(periode), periode)
        val beregning = lagBeregningMedFradrag(fradrag)

        beregning.getSumYtelse() shouldBe 0
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            grunn = AvslagGrunnetBeregning.Grunn.FOR_HØY_INNTEKT,
        )
    }

    @Test
    fun `beregning med potentiellt negativ sum ytelse skal gi avslag`() {
        val januar = lagFradrag(1000000.0, 1.januar(2021))
        val desember = lagFradrag(100000.0, 31.desember(2021))

        val beregning = lagBeregningMedFradrag(januar, desember)

        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            grunn = AvslagGrunnetBeregning.Grunn.FOR_HØY_INNTEKT,
        )
    }

    @Test
    fun `beregning med alle måneder under minstebeløp skal gi avslag`() {
        val januar = lagFradrag(20500.0, 1.januar(2021))
        val desember = lagFradrag(21800.0, 31.desember(2021))

        val beregning = lagBeregningMedFradrag(januar, desember)

        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            grunn = AvslagGrunnetBeregning.Grunn.SU_UNDER_MINSTEGRENSE,
        )
    }

    @Test
    fun `sjekker avslag for beløp under minstegrense før beløp lik 0`() {
        val januar = lagFradrag(20500.0, 1.januar(2021))
        val januarUnderMinsteNivå = lagFradrag(Sats.HØY.månedsbeløp(1.januar(2021)) - 20500.0, 1.januar(2021), Fradragstype.UnderMinstenivå)
        val desember = lagFradrag(2500.0, 31.desember(2021))

        val beregning = lagBeregningMedFradrag(januar, januarUnderMinsteNivå, desember)

        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            grunn = AvslagGrunnetBeregning.Grunn.SU_UNDER_MINSTEGRENSE,
        )
    }

    @Test
    fun `beregning med alle måneder over minstebeløp skal ikke gi avslag`() {
        val januar = lagFradrag(18500.0, 1.januar(2021))
        val desember = lagFradrag(16400.0, 31.desember(2021))

        val beregning = lagBeregningMedFradrag(januar, desember)

        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Nei
    }

    @Test
    fun `avslag dersom det eksisterer 1 måned med beløp lik 0`() {
        val januar = lagFradrag(30000.0, 1.januar(2021))
        val desember = lagFradrag(2500.0, 31.desember(2021))

        val beregning = lagBeregningMedFradrag(januar, desember)

        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            grunn = AvslagGrunnetBeregning.Grunn.FOR_HØY_INNTEKT,
        )
    }

    @Test
    fun `ikke avslag dersom det eksisterer 1 måned med beløp lik 0 pågrunn av sosialstønad`() {
        val januar = lagFradrag(30000.0, 1.januar(2021), Fradragstype.Sosialstønad)
        val desember = lagFradrag(2500.0, 31.desember(2021))

        val beregning = lagBeregningMedFradrag(januar, desember)

        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Nei
    }

    @Test
    fun `avslag dersom det eksisterer 1 måned med beløp under minstegrense`() {
        val januar = lagFradrag(2500.0, 1.januar(2021))
        val juni = lagFradrag(21900.0, 1.juni(2021))
        val desember = lagFradrag(2500.0, 1.desember(2021))

        val beregning = lagBeregningMedFradrag(januar, juni, desember)
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            grunn = AvslagGrunnetBeregning.Grunn.SU_UNDER_MINSTEGRENSE,
        )
    }

    @Test
    fun `ikke avslag dersom det eksisterer 1 måned med beløp under minstegrensen pågrunn av sosialstønad`() {
        val januar = lagFradrag(2500.0, 1.januar(2021))
        val juni = lagFradrag(21900.0, 1.juni(2021), Fradragstype.Sosialstønad)
        val desember = lagFradrag(2500.0, 1.desember(2021))

        val beregning = lagBeregningMedFradrag(januar, juni, desember)
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Nei
    }

    @Test
    fun `ikke avslag dersom beløp er under minstegresen pågrunn av EPS sosialstønad`() {
        val januar = lagFradrag(2500.0, 1.januar(2021))
        val juni = lagFradrag(21900.0, 1.juni(2021), Fradragstype.Sosialstønad, FradragTilhører.EPS)
        val desember = lagFradrag(2500.0, 1.desember(2021))

        val beregning = lagBeregningMedFradrag(januar, juni, desember)
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Nei
    }

    @Test
    fun `ikke avslag dersom beløp er under minstegresen pågrunn av EPS og brukers sosialstønad`() {
        val januar = lagFradrag(2500.0, 1.januar(2021))
        val juni = lagFradrag(10950.0, 1.juni(2021), Fradragstype.Sosialstønad, FradragTilhører.BRUKER)
        val juniEPS = lagFradrag(10950.0, 1.juni(2021), Fradragstype.Sosialstønad, FradragTilhører.EPS)
        val desember = lagFradrag(2500.0, 1.desember(2021))

        val beregning = lagBeregningMedFradrag(januar, juni, juniEPS, desember)
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Nei
    }

    private fun lagBeregningMedFradrag(vararg fradrag: Fradrag): Beregning {
        val periode = Periode.create(fradrag.minOf { it.periode.fraOgMed }, fradrag.maxOf { it.periode.tilOgMed })
        return BeregningFactory.ny(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                *fradrag,
                FradragFactory.ny(
                    Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
            begrunnelse = null,
        )
    }

    private fun lagFradrag(beløp: Double, localDate: LocalDate, fradragstype: Fradragstype? = null, tilhører: FradragTilhører? = null) = FradragFactory.ny(
        type = fradragstype ?: Fradragstype.Kapitalinntekt,
        månedsbeløp = beløp,
        periode = Periode.create(localDate.startOfMonth(), localDate.endOfMonth()),
        utenlandskInntekt = null,
        tilhører = tilhører ?: FradragTilhører.BRUKER,
    )
}
