package no.nav.su.se.bakover.domain.beregning

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragskategori
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragskategoriWrapper
import no.nav.su.se.bakover.test.månedsperiodeApril2021
import no.nav.su.se.bakover.test.månedsperiodeDesember2021
import no.nav.su.se.bakover.test.månedsperiodeFebruar2021
import no.nav.su.se.bakover.test.månedsperiodeJanuar2021
import no.nav.su.se.bakover.test.månedsperiodeMars2021
import org.junit.jupiter.api.Test

internal class SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioderTest {
    @Test
    fun `tilstøtende månedsberegninger hvor alt er likt bortsett fra dato grupperes sammen`() {
        val januar = MånedsberegningFactory.ny(
            måned = månedsperiodeJanuar2021,
            sats = Sats.HØY,
            fradrag = listOf()
        )

        val februar = MånedsberegningFactory.ny(
            måned = månedsperiodeFebruar2021,
            sats = Sats.HØY,
            fradrag = listOf()
        )

        SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder(listOf(januar, februar)).beregningsperioder shouldBe listOf(
            EkvivalenteMånedsberegninger(listOf(januar, februar)).also {
                it.periode.fraOgMed shouldBe januar.periode.fraOgMed
                it.periode.tilOgMed shouldBe februar.periode.tilOgMed
            }
        )
    }

    @Test
    fun `tilstøtende månedsberegninger som har forskjellige fradrag grupperes hver for seg`() {
        val januar = MånedsberegningFactory.ny(
            måned = månedsperiodeJanuar2021,
            sats = Sats.HØY,
            fradrag = listOf()
        )

        val februar = MånedsberegningFactory.ny(
            måned = månedsperiodeFebruar2021,
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = FradragskategoriWrapper(Fradragskategori.Sosialstønad),
                    månedsbeløp = 2000.0,
                    periode = månedsperiodeFebruar2021,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder(listOf(januar, februar)).beregningsperioder shouldBe listOf(
            EkvivalenteMånedsberegninger(listOf(januar)).also {
                it.periode.fraOgMed shouldBe januar.periode.fraOgMed
                it.periode.tilOgMed shouldBe januar.periode.tilOgMed
            },
            EkvivalenteMånedsberegninger(listOf(februar)).also {
                it.periode.fraOgMed shouldBe februar.periode.fraOgMed
                it.periode.tilOgMed shouldBe februar.periode.tilOgMed
            },
        )
    }

    @Test
    fun `like månedsberegninger som ikke tilstøter hverandre grupperes hver for seg`() {
        val januar = MånedsberegningFactory.ny(
            måned = månedsperiodeJanuar2021,
            sats = Sats.HØY,
            fradrag = listOf()
        )

        val februar = MånedsberegningFactory.ny(
            måned = månedsperiodeFebruar2021,
            sats = Sats.ORDINÆR,
            fradrag = listOf()
        )

        val mars = MånedsberegningFactory.ny(
            måned = månedsperiodeMars2021,
            sats = Sats.HØY,
            fradrag = listOf()
        )

        val april = MånedsberegningFactory.ny(
            måned = månedsperiodeApril2021,
            sats = Sats.ORDINÆR,
            fradrag = listOf()
        )

        SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder(listOf(januar, februar, mars, april)).beregningsperioder shouldBe listOf(
            EkvivalenteMånedsberegninger(listOf(januar)),
            EkvivalenteMånedsberegninger(listOf(februar)),
            EkvivalenteMånedsberegninger(listOf(mars)),
            EkvivalenteMånedsberegninger(listOf(april)),
        )
    }

    @Test
    fun `månedsberegninger som har forskjellig antall fradrag grupperes hver for seg`() {
        val januar = MånedsberegningFactory.ny(
            måned = månedsperiodeJanuar2021,
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = FradragskategoriWrapper(Fradragskategori.Sosialstønad),
                    månedsbeløp = 1000.0,
                    periode = månedsperiodeJanuar2021,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        val februar = MånedsberegningFactory.ny(
            måned = månedsperiodeFebruar2021,
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = FradragskategoriWrapper(Fradragskategori.Sosialstønad),
                    månedsbeløp = 500.0,
                    periode = månedsperiodeFebruar2021,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.ny(
                    type = FradragskategoriWrapper(Fradragskategori.Arbeidsinntekt),
                    månedsbeløp = 500.0,
                    periode = månedsperiodeFebruar2021,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder(listOf(januar, februar)).beregningsperioder shouldBe listOf(
            EkvivalenteMånedsberegninger(listOf(januar)),
            EkvivalenteMånedsberegninger(listOf(februar)),
        )
    }

    @Test
    fun `månedsberegninger med flere fradrag av samme type grupperes sammen`() {
        val januar = MånedsberegningFactory.ny(
            måned = månedsperiodeJanuar2021,
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = FradragskategoriWrapper(Fradragskategori.Sosialstønad),
                    månedsbeløp = 1000.0,
                    periode = månedsperiodeJanuar2021,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.ny(
                    type = FradragskategoriWrapper(Fradragskategori.Sosialstønad),
                    månedsbeløp = 1000.0,
                    periode = månedsperiodeJanuar2021,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        val februar = MånedsberegningFactory.ny(
            måned = månedsperiodeFebruar2021,
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = FradragskategoriWrapper(Fradragskategori.Sosialstønad),
                    månedsbeløp = 1000.0,
                    periode = månedsperiodeFebruar2021,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.ny(
                    type = FradragskategoriWrapper(Fradragskategori.Sosialstønad),
                    månedsbeløp = 1000.0,
                    periode = månedsperiodeFebruar2021,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder(listOf(januar, februar)).beregningsperioder shouldBe listOf(
            EkvivalenteMånedsberegninger(listOf(januar, februar)),
        )
    }

    @Test
    fun `grupperer like månedsberegninger sammen selv om fradragene i utgangspunktet ikke ligger på nøyaktig samme indeks`() {
        val januar = MånedsberegningFactory.ny(
            måned = månedsperiodeJanuar2021,
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = FradragskategoriWrapper(Fradragskategori.Sosialstønad),
                    månedsbeløp = 1000.0,
                    periode = månedsperiodeJanuar2021,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.ny(
                    type = FradragskategoriWrapper(Fradragskategori.Arbeidsinntekt),
                    månedsbeløp = 1000.0,
                    periode = månedsperiodeJanuar2021,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.ny(
                    type = FradragskategoriWrapper(Fradragskategori.Arbeidsinntekt),
                    månedsbeløp = 1000.0,
                    periode = månedsperiodeJanuar2021,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
                FradragFactory.ny(
                    type = FradragskategoriWrapper(Fradragskategori.Arbeidsinntekt),
                    månedsbeløp = 2000.0,
                    periode = månedsperiodeJanuar2021,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        val februar = MånedsberegningFactory.ny(
            måned = månedsperiodeFebruar2021,
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = FradragskategoriWrapper(Fradragskategori.Arbeidsinntekt),
                    månedsbeløp = 1000.0,
                    periode = månedsperiodeFebruar2021,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
                FradragFactory.ny(
                    type = FradragskategoriWrapper(Fradragskategori.Arbeidsinntekt),
                    månedsbeløp = 2000.0,
                    periode = månedsperiodeFebruar2021,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.ny(
                    type = FradragskategoriWrapper(Fradragskategori.Arbeidsinntekt),
                    månedsbeløp = 1000.0,
                    periode = månedsperiodeFebruar2021,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.ny(
                    type = FradragskategoriWrapper(Fradragskategori.Sosialstønad),
                    månedsbeløp = 1000.0,
                    periode = månedsperiodeFebruar2021,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder(listOf(januar, februar)).beregningsperioder shouldBe listOf(
            EkvivalenteMånedsberegninger(listOf(januar, februar))
        )
    }

    @Test
    fun `like månedsberegninger som ikke er tilstøtende grupperes hver for seg`() {
        val januar = MånedsberegningFactory.ny(
            måned = månedsperiodeJanuar2021,
            sats = Sats.HØY,
            fradrag = listOf()
        )

        val februar = MånedsberegningFactory.ny(
            måned = månedsperiodeFebruar2021,
            sats = Sats.HØY,
            fradrag = listOf()
        )

        val april = MånedsberegningFactory.ny(
            måned = månedsperiodeApril2021,
            sats = Sats.HØY,
            fradrag = listOf()
        )

        val desember = MånedsberegningFactory.ny(
            måned = månedsperiodeDesember2021,
            sats = Sats.HØY,
            fradrag = listOf()
        )

        SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder(listOf(januar, februar, april, desember)).beregningsperioder shouldBe listOf(
            EkvivalenteMånedsberegninger(listOf(januar, februar)),
            EkvivalenteMånedsberegninger(listOf(april)),
            EkvivalenteMånedsberegninger(listOf(desember))
        )
    }
}
