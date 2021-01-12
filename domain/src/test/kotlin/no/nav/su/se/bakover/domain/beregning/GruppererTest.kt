package no.nav.su.se.bakover.domain.beregning

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Grupperer.likehetUtenDato
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import org.junit.jupiter.api.Test

internal class GruppererTest {
    @Test
    fun `grupper 1`() {
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

        Grupperer.grupper(listOf(januar, februar)) shouldBe mapOf(
            Periode(1.januar(2021), 28.februar(2021)) to listOf(
                januar,
                februar
            )
        )
    }

    @Test
    fun `grupper 2`() {
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

        Grupperer.grupper(listOf(januar, februar)) shouldBe mapOf(
            Periode(1.januar(2021), 31.januar(2021)) to listOf(januar),
            Periode(1.februar(2021), 28.februar(2021)) to listOf(februar),
        )
    }

    @Test
    fun `grupper 3`() {
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

        Grupperer.grupper(listOf(januar, februar, mars, april)) shouldBe mapOf(
            Periode(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)) to listOf(januar),
            Periode(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)) to listOf(februar),
            Periode(fraOgMed = 1.mars(2021), tilOgMed = 31.mars(2021)) to listOf(mars),
            Periode(fraOgMed = 1.april(2021), tilOgMed = 30.april(2021)) to listOf(april),
        )
    }

    @Test
    fun `likhet uten dato`() {
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

        januar likehetUtenDato februar shouldBe true
    }

    @Test
    fun `likhet uten dato 1`() {
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
            fradrag = listOf()
        )

        januar likehetUtenDato februar shouldBe false
    }

    @Test
    fun `likhet uten dato 3`() {
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
                    månedsbeløp = 1000.0,
                    periode = Periode(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                )
            )
        )

        januar likehetUtenDato februar shouldBe true
    }

    @Test
    fun `likhet uten dato 4`() {
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
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 1000.0,
                    periode = Periode(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                )
            )
        )

        januar likehetUtenDato februar shouldBe false
    }

    @Test
    fun `likhet uten dato 5`() {
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
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 1000.0,
                    periode = Periode(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                )
            )
        )

        januar likehetUtenDato februar shouldBe false
    }
}
