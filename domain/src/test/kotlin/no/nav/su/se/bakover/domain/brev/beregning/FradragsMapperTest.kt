package no.nav.su.se.bakover.domain.brev.beregning

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.tid.august
import no.nav.su.se.bakover.common.domain.tid.juni
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.år
import org.junit.jupiter.api.Test
import vilkår.inntekt.domain.grunnlag.FradragFactory
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype

internal class FradragsMapperTest {

    @Test
    fun `Inneholder bare fradrag for aktuell bruker`() {
        val periode = år(2020)
        val fradragForEps = FradragFactory.nyFradragsperiode(
            fradragstype = Fradragstype.BidragEtterEkteskapsloven,
            månedsbeløp = 3000.0,
            periode = periode,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.EPS,
        )
        val fradrag = listOf(
            FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.Kapitalinntekt,
                månedsbeløp = 5000.0,
                periode = periode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
            fradragForEps,
        )

        BrukerFradragBenyttetIBeregningsperiode(fradrag).fradrag shouldBe listOf(
            MånedsfradragForBrev(
                type = "Kapitalinntekt",
                beløp = 5000,
                utenlandskInntekt = null,
            ),
        )

        EpsFradragFraSaksbehandlerIBeregningsperiode(
            fradragFraSaksbehandler = fradrag,
            beregningsperiode = periode,
        ).fradrag shouldBe listOf(
            MånedsfradragForBrev(
                type = "Bidrag etter ekteskapsloven",
                beløp = 3000,
                utenlandskInntekt = null,
            ),
        )
    }

    @Test
    fun `BrukerFradrag inneholder bare fradrag med beløp større enn 0`() {
        val periode = år(2020)
        val fradrag = listOf(
            FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.Kapitalinntekt,
                månedsbeløp = 3337.0,
                periode = periode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
            FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.ForventetInntekt,
                månedsbeløp = 0.0,
                periode = periode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )

        BrukerFradragBenyttetIBeregningsperiode(fradrag).fradrag shouldBe listOf(
            MånedsfradragForBrev(
                type = "Kapitalinntekt",
                beløp = 3337,
                utenlandskInntekt = null,
            ),
        )
    }

    @Test
    fun `EpsFradrag inneholder bare fradrag for aktuell beregningsperiode`() {
        val periode = år(2020)
        val fradrag = listOf(
            FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.Kapitalinntekt,
                månedsbeløp = 3337.0,
                periode = periode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.EPS,
            ),
            FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.ForventetInntekt,
                månedsbeløp = 10000.0,
                periode = Periode.create(fraOgMed = 1.juni(2020), tilOgMed = 31.august(2020)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.EPS,
            ),
            FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.Arbeidsinntekt,
                månedsbeløp = 10000.0,
                periode = januar(2020),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.EPS,
            ),
        )

        EpsFradragFraSaksbehandlerIBeregningsperiode(
            fradrag,
            januar(2020),
        ).fradrag shouldBe listOf(
            MånedsfradragForBrev(
                type = "Arbeidsinntekt",
                beløp = 10000,
                utenlandskInntekt = null,
            ),
            MånedsfradragForBrev(
                type = "Kapitalinntekt",
                beløp = 3337,
                utenlandskInntekt = null,
            ),
        )

        EpsFradragFraSaksbehandlerIBeregningsperiode(
            fradrag,
            periode,
        ).fradrag shouldBe listOf(
            MånedsfradragForBrev(
                type = "Kapitalinntekt",
                beløp = 3337,
                utenlandskInntekt = null,
            ),
        )
    }
}
