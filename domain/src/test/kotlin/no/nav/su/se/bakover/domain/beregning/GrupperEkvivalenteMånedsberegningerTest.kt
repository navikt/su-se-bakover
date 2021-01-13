package no.nav.su.se.bakover.domain.beregning

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import org.junit.jupiter.api.Test

internal class GrupperEkvivalenteMånedsberegningerTest {
    @Test
    fun `tilstøtende månedsberegninger hvor alt er likt bortsett fra dato grupperes sammen`() {
        val januar = MånedsberegningFactory.ny(
            periode = Periode(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
            sats = Sats.HØY,
            fradrag = listOf()
        )

        val februar = MånedsberegningFactory.ny(
            periode = Periode(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
            sats = Sats.HØY,
            fradrag = listOf()
        )

        GrupperEkvivalenteMånedsberegninger(listOf(januar, februar)).grupper shouldBe listOf(
            GrupperEkvivalenteMånedsberegninger.GrupperteMånedsberegninger(listOf(januar, februar)).also {
                it.periode.getFraOgMed() shouldBe januar.getPeriode().getFraOgMed()
                it.periode.getTilOgMed() shouldBe februar.getPeriode().getTilOgMed()
            }
        )
    }

    @Test
    fun `tilstøtende månedsberegninger som har forskjellige fradrag grupperes hver for seg`() {
        val januar = MånedsberegningFactory.ny(
            periode = Periode(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
            sats = Sats.HØY,
            fradrag = listOf()
        )

        val februar = MånedsberegningFactory.ny(
            periode = Periode(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Sosialstønad,
                    månedsbeløp = 2000.0,
                    periode = Periode(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                )
            )
        )

        GrupperEkvivalenteMånedsberegninger(listOf(januar, februar)).grupper shouldBe listOf(
            GrupperEkvivalenteMånedsberegninger.GrupperteMånedsberegninger(listOf(januar)).also {
                it.periode.getFraOgMed() shouldBe januar.getPeriode().getFraOgMed()
                it.periode.getTilOgMed() shouldBe januar.getPeriode().getTilOgMed()
            },
            GrupperEkvivalenteMånedsberegninger.GrupperteMånedsberegninger(listOf(februar)).also {
                it.periode.getFraOgMed() shouldBe februar.getPeriode().getFraOgMed()
                it.periode.getTilOgMed() shouldBe februar.getPeriode().getTilOgMed()
            },
        )
    }

    @Test
    fun `like månedsberegninger som ikke tilstøter hverandre grupperes hver for seg`() {
        val januar = MånedsberegningFactory.ny(
            periode = Periode(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
            sats = Sats.HØY,
            fradrag = listOf()
        )

        val februar = MånedsberegningFactory.ny(
            periode = Periode(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
            sats = Sats.ORDINÆR,
            fradrag = listOf()
        )

        val mars = MånedsberegningFactory.ny(
            periode = Periode(fraOgMed = 1.mars(2021), tilOgMed = 31.mars(2021)),
            sats = Sats.HØY,
            fradrag = listOf()
        )

        val april = MånedsberegningFactory.ny(
            periode = Periode(fraOgMed = 1.april(2021), tilOgMed = 30.april(2021)),
            sats = Sats.ORDINÆR,
            fradrag = listOf()
        )

        GrupperEkvivalenteMånedsberegninger(listOf(januar, februar, mars, april)).grupper shouldBe listOf(
            GrupperEkvivalenteMånedsberegninger.GrupperteMånedsberegninger(listOf(januar)),
            GrupperEkvivalenteMånedsberegninger.GrupperteMånedsberegninger(listOf(februar)),
            GrupperEkvivalenteMånedsberegninger.GrupperteMånedsberegninger(listOf(mars)),
            GrupperEkvivalenteMånedsberegninger.GrupperteMånedsberegninger(listOf(april)),
        )
    }

    @Test
    fun `månedsberegninger som har forskjellig antall fradrag grupperes hver for seg`() {
        val januar = MånedsberegningFactory.ny(
            periode = Periode(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Sosialstønad,
                    månedsbeløp = 1000.0,
                    periode = Periode(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                )
            )
        )

        val februar = MånedsberegningFactory.ny(
            periode = Periode(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Sosialstønad,
                    månedsbeløp = 500.0,
                    periode = Periode(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                ),
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 500.0,
                    periode = Periode(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                )
            )
        )

        GrupperEkvivalenteMånedsberegninger(listOf(januar, februar)).grupper shouldBe listOf(
            GrupperEkvivalenteMånedsberegninger.GrupperteMånedsberegninger(listOf(januar)),
            GrupperEkvivalenteMånedsberegninger.GrupperteMånedsberegninger(listOf(februar)),
        )
    }

    @Test
    fun `månedsberegninger med flere fradrag av samme type grupperes sammen`() {
        val januar = MånedsberegningFactory.ny(
            periode = Periode(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Sosialstønad,
                    månedsbeløp = 1000.0,
                    periode = Periode(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                ),
                FradragFactory.ny(
                    type = Fradragstype.Sosialstønad,
                    månedsbeløp = 1000.0,
                    periode = Periode(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                )
            )
        )

        val februar = MånedsberegningFactory.ny(
            periode = Periode(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Sosialstønad,
                    månedsbeløp = 1000.0,
                    periode = Periode(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                ),
                FradragFactory.ny(
                    type = Fradragstype.Sosialstønad,
                    månedsbeløp = 1000.0,
                    periode = Periode(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                )
            )
        )

        GrupperEkvivalenteMånedsberegninger(listOf(januar, februar)).grupper shouldBe listOf(
            GrupperEkvivalenteMånedsberegninger.GrupperteMånedsberegninger(listOf(januar, februar)),
        )
    }

    @Test
    fun `grupperer like månedsberegninger sammen selv om fradragene i utgangspunktet ikke ligger på nøyaktig samme indeks`() {
        val januar = MånedsberegningFactory.ny(
            periode = Periode(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Sosialstønad,
                    månedsbeløp = 1000.0,
                    periode = Periode(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                ),
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 1000.0,
                    periode = Periode(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                ),
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 1000.0,
                    periode = Periode(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS
                ),
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 2000.0,
                    periode = Periode(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                )
            )
        )

        val februar = MånedsberegningFactory.ny(
            periode = Periode(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 1000.0,
                    periode = Periode(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS
                ),
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 2000.0,
                    periode = Periode(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                ),
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 1000.0,
                    periode = Periode(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                ),
                FradragFactory.ny(
                    type = Fradragstype.Sosialstønad,
                    månedsbeløp = 1000.0,
                    periode = Periode(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                )
            )
        )

        GrupperEkvivalenteMånedsberegninger(listOf(januar, februar)).grupper shouldBe listOf(
            GrupperEkvivalenteMånedsberegninger.GrupperteMånedsberegninger(listOf(januar, februar))
        )
    }
}
