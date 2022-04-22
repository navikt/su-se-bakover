package no.nav.su.se.bakover.domain.brev.beregning

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragskategori
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragskategoriWrapper
import no.nav.su.se.bakover.test.månedsperiodeJanuar2020
import org.junit.jupiter.api.Test

internal class FradragsMapperTest {

    @Test
    fun `Inneholder bare fradrag for aktuell bruker`() {
        val periode = Periode.create(fraOgMed = 1.januar(2020), tilOgMed = 31.desember(2020))
        val fradragForEps = FradragFactory.ny(
            type = FradragskategoriWrapper(Fradragskategori.BidragEtterEkteskapsloven),
            månedsbeløp = 3000.0,
            periode = periode,
            utenlandskInntekt = null,
            tilhører = FradragTilhører.EPS,
        )
        val fradrag = listOf(
            FradragFactory.ny(
                type = FradragskategoriWrapper(Fradragskategori.Kapitalinntekt),
                månedsbeløp = 5000.0,
                periode = periode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
            fradragForEps,
        )

        BrukerFradragBenyttetIBeregningsperiode(fradrag).fradrag shouldBe listOf(
            Månedsfradrag(
                type = FradragskategoriWrapper(Fradragskategori.Kapitalinntekt).toReadableTypeName(false),
                beløp = 5000,
                utenlandskInntekt = null,
            ),
        )

        EpsFradragFraSaksbehandlerIBeregningsperiode(
            fradragFraSaksbehandler = fradrag,
            beregningsperiode = periode
        ).fradrag shouldBe listOf(
            Månedsfradrag(
                type = FradragskategoriWrapper(Fradragskategori.BidragEtterEkteskapsloven).toReadableTypeName(false),
                beløp = 3000,
                utenlandskInntekt = null,
            ),
        )
    }

    @Test
    fun `BrukerFradrag inneholder bare fradrag med beløp større enn 0`() {
        val periode = Periode.create(fraOgMed = 1.januar(2020), tilOgMed = 31.desember(2020))
        val fradrag = listOf(
            FradragFactory.ny(
                type = FradragskategoriWrapper(Fradragskategori.Kapitalinntekt),
                månedsbeløp = 3337.0,
                periode = periode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
            FradragFactory.ny(
                type = FradragskategoriWrapper(Fradragskategori.ForventetInntekt),
                månedsbeløp = 0.0,
                periode = periode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )

        BrukerFradragBenyttetIBeregningsperiode(fradrag).fradrag shouldBe listOf(
            Månedsfradrag(
                type = FradragskategoriWrapper(Fradragskategori.Kapitalinntekt).toReadableTypeName(false),
                beløp = 3337,
                utenlandskInntekt = null,
            ),
        )
    }

    @Test
    fun `EpsFradrag inneholder bare fradrag for aktuell beregningsperiode`() {
        val periode = Periode.create(fraOgMed = 1.januar(2020), tilOgMed = 31.desember(2020))
        val fradrag = listOf(
            FradragFactory.ny(
                type = FradragskategoriWrapper(Fradragskategori.Kapitalinntekt),
                månedsbeløp = 3337.0,
                periode = periode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.EPS,
            ),
            FradragFactory.ny(
                type = FradragskategoriWrapper(Fradragskategori.ForventetInntekt),
                månedsbeløp = 10000.0,
                periode = Periode.create(fraOgMed = 1.juni(2020), tilOgMed = 31.august(2020)),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.EPS,
            ),
            FradragFactory.ny(
                type = FradragskategoriWrapper(Fradragskategori.Arbeidsinntekt),
                månedsbeløp = 10000.0,
                periode = månedsperiodeJanuar2020,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.EPS,
            ),
        )

        EpsFradragFraSaksbehandlerIBeregningsperiode(
            fradrag,
            månedsperiodeJanuar2020
        ).fradrag shouldBe listOf(
            Månedsfradrag(
                type = FradragskategoriWrapper(Fradragskategori.Arbeidsinntekt).toReadableTypeName(false),
                beløp = 10000,
                utenlandskInntekt = null,
            ),
            Månedsfradrag(
                type = FradragskategoriWrapper(Fradragskategori.Kapitalinntekt).toReadableTypeName(false),
                beløp = 3337,
                utenlandskInntekt = null,
            ),
        )

        EpsFradragFraSaksbehandlerIBeregningsperiode(
            fradrag,
            periode
        ).fradrag shouldBe listOf(
            Månedsfradrag(
                type = FradragskategoriWrapper(Fradragskategori.Kapitalinntekt).toReadableTypeName(false),
                beløp = 3337,
                utenlandskInntekt = null,
            ),
        )
    }
}
