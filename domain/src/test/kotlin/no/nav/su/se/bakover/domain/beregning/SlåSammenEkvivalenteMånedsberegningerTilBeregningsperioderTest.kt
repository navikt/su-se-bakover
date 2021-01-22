package no.nav.su.se.bakover.domain.beregning

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import org.junit.jupiter.api.Test

internal class SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioderTest {
    @Test
    fun `tilstøtende månedsberegninger hvor alt er likt bortsett fra dato grupperes sammen`() {
        val januar = MånedsberegningFactory.ny(
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
            sats = Sats.HØY,
            fradrag = listOf()
        )

        val februar = MånedsberegningFactory.ny(
            periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
            sats = Sats.HØY,
            fradrag = listOf()
        )

        SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder(listOf(januar, februar)).beregningsperioder shouldBe listOf(
            EkvivalenteMånedsberegninger(listOf(januar, februar)).also {
                it.getPeriode().getFraOgMed() shouldBe januar.getPeriode().getFraOgMed()
                it.getPeriode().getTilOgMed() shouldBe februar.getPeriode().getTilOgMed()
            }
        )
    }

    @Test
    fun `tilstøtende månedsberegninger som har forskjellige fradrag grupperes hver for seg`() {
        val januar = MånedsberegningFactory.ny(
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
            sats = Sats.HØY,
            fradrag = listOf()
        )

        val februar = MånedsberegningFactory.ny(
            periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Sosialstønad,
                    månedsbeløp = 2000.0,
                    periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                )
            )
        )

        SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder(listOf(januar, februar)).beregningsperioder shouldBe listOf(
            EkvivalenteMånedsberegninger(listOf(januar)).also {
                it.getPeriode().getFraOgMed() shouldBe januar.getPeriode().getFraOgMed()
                it.getPeriode().getTilOgMed() shouldBe januar.getPeriode().getTilOgMed()
            },
            EkvivalenteMånedsberegninger(listOf(februar)).also {
                it.getPeriode().getFraOgMed() shouldBe februar.getPeriode().getFraOgMed()
                it.getPeriode().getTilOgMed() shouldBe februar.getPeriode().getTilOgMed()
            },
        )
    }

    @Test
    fun `like månedsberegninger som ikke tilstøter hverandre grupperes hver for seg`() {
        val januar = MånedsberegningFactory.ny(
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
            sats = Sats.HØY,
            fradrag = listOf()
        )

        val februar = MånedsberegningFactory.ny(
            periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
            sats = Sats.ORDINÆR,
            fradrag = listOf()
        )

        val mars = MånedsberegningFactory.ny(
            periode = Periode.create(fraOgMed = 1.mars(2021), tilOgMed = 31.mars(2021)),
            sats = Sats.HØY,
            fradrag = listOf()
        )

        val april = MånedsberegningFactory.ny(
            periode = Periode.create(fraOgMed = 1.april(2021), tilOgMed = 30.april(2021)),
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
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Sosialstønad,
                    månedsbeløp = 1000.0,
                    periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                )
            )
        )

        val februar = MånedsberegningFactory.ny(
            periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Sosialstønad,
                    månedsbeløp = 500.0,
                    periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                ),
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 500.0,
                    periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                )
            )
        )

        SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder(listOf(januar, februar)).beregningsperioder shouldBe listOf(
            EkvivalenteMånedsberegninger(listOf(januar)),
            EkvivalenteMånedsberegninger(listOf(februar)),
        )
    }

    @Test
    fun `månedsberegninger med flere fradrag av samme type grupperes sammen`() {
        val januar = MånedsberegningFactory.ny(
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Sosialstønad,
                    månedsbeløp = 1000.0,
                    periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                ),
                FradragFactory.ny(
                    type = Fradragstype.Sosialstønad,
                    månedsbeløp = 1000.0,
                    periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                )
            )
        )

        val februar = MånedsberegningFactory.ny(
            periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Sosialstønad,
                    månedsbeløp = 1000.0,
                    periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                ),
                FradragFactory.ny(
                    type = Fradragstype.Sosialstønad,
                    månedsbeløp = 1000.0,
                    periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                )
            )
        )

        SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder(listOf(januar, februar)).beregningsperioder shouldBe listOf(
            EkvivalenteMånedsberegninger(listOf(januar, februar)),
        )
    }

    @Test
    fun `grupperer like månedsberegninger sammen selv om fradragene i utgangspunktet ikke ligger på nøyaktig samme indeks`() {
        val januar = MånedsberegningFactory.ny(
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Sosialstønad,
                    månedsbeløp = 1000.0,
                    periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                ),
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 1000.0,
                    periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                ),
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 1000.0,
                    periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS
                ),
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 2000.0,
                    periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                )
            )
        )

        val februar = MånedsberegningFactory.ny(
            periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 1000.0,
                    periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS
                ),
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 2000.0,
                    periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                ),
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 1000.0,
                    periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                ),
                FradragFactory.ny(
                    type = Fradragstype.Sosialstønad,
                    månedsbeløp = 1000.0,
                    periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                )
            )
        )

        SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder(listOf(januar, februar)).beregningsperioder shouldBe listOf(
            EkvivalenteMånedsberegninger(listOf(januar, februar))
        )
    }

    @Test
    fun `like månedsberegninger som ikke er tilstøtende grupperes hver for seg`() {
        val januar = MånedsberegningFactory.ny(
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
            sats = Sats.HØY,
            fradrag = listOf()
        )

        val februar = MånedsberegningFactory.ny(
            periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
            sats = Sats.HØY,
            fradrag = listOf()
        )

        val april = MånedsberegningFactory.ny(
            periode = Periode.create(fraOgMed = 1.april(2021), tilOgMed = 30.april(2021)),
            sats = Sats.HØY,
            fradrag = listOf()
        )

        val desember = MånedsberegningFactory.ny(
            periode = Periode.create(fraOgMed = 1.desember(2021), tilOgMed = 31.desember(2021)),
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
