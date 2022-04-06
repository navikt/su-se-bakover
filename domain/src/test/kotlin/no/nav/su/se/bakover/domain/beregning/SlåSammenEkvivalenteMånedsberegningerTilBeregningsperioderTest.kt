package no.nav.su.se.bakover.domain.beregning

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.periode.april
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.test.fullSupplerendeStønadHøyTest
import no.nav.su.se.bakover.test.fullSupplerendeStønadOrdinærTest
import no.nav.su.se.bakover.test.satsFactoryTest
import org.junit.jupiter.api.Test

internal class SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioderTest {
    @Test
    fun `tilstøtende månedsberegninger hvor alt er likt bortsett fra dato grupperes sammen`() {
        val januar = MånedsberegningFactory.ny(
            måned = januar(2021),
            fullSupplerendeStønadForMåned = satsFactoryTest.fullSupplerendeStønadHøy().forMånedsperiode(januar(2021)),
            fradrag = listOf(),
        )

        val februar = MånedsberegningFactory.ny(
            måned = februar(2021),
            fullSupplerendeStønadForMåned = satsFactoryTest.fullSupplerendeStønadHøy().forMånedsperiode(februar(2021)),
            fradrag = listOf(),
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
            måned = januar(2021),
            fullSupplerendeStønadForMåned = satsFactoryTest.fullSupplerendeStønadHøy().forMånedsperiode(januar(2021)),
            fradrag = listOf(),
        )

        val februar = MånedsberegningFactory.ny(
            måned = februar(2021),
            fullSupplerendeStønadForMåned = satsFactoryTest.fullSupplerendeStønadHøy().forMånedsperiode(februar(2021)),
            fradrag = listOf(
                FradragFactory.nyMånedsperiode(
                    fradragstype = Fradragstype.Sosialstønad,
                    månedsbeløp = 2000.0,
                    måned = februar(2021),
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
            måned = januar(2021),
            fullSupplerendeStønadForMåned = fullSupplerendeStønadHøyTest.forMånedsperiode(januar(2021)),
            fradrag = listOf(),
        )

        val februar = MånedsberegningFactory.ny(
            måned = februar(2021),
            fullSupplerendeStønadForMåned = fullSupplerendeStønadOrdinærTest.forMånedsperiode(februar(2021)),
            fradrag = listOf(),
        )

        val mars = MånedsberegningFactory.ny(
            måned = mars(2021),
            fullSupplerendeStønadForMåned = fullSupplerendeStønadHøyTest.forMånedsperiode(mars(2021)),
            fradrag = listOf(),
        )

        val april = MånedsberegningFactory.ny(
            måned = april(2021),
            fullSupplerendeStønadForMåned = fullSupplerendeStønadOrdinærTest.forMånedsperiode(april(2021)),
            fradrag = listOf(),
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
            måned = januar(2021),
            fullSupplerendeStønadForMåned = fullSupplerendeStønadHøyTest.forMånedsperiode(januar(2021)),
            fradrag = listOf(
                FradragFactory.nyMånedsperiode(
                    fradragstype = Fradragstype.Sosialstønad,
                    månedsbeløp = 1000.0,
                    måned = januar(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        val februar = MånedsberegningFactory.ny(
            måned = februar(2021),
            fullSupplerendeStønadForMåned = fullSupplerendeStønadHøyTest.forMånedsperiode(februar(2021)),
            fradrag = listOf(
                FradragFactory.nyMånedsperiode(
                    fradragstype = Fradragstype.Sosialstønad,
                    månedsbeløp = 500.0,
                    måned = februar(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyMånedsperiode(
                    fradragstype = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 500.0,
                    måned = februar(2021),
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
            måned = januar(2021),
            fullSupplerendeStønadForMåned = fullSupplerendeStønadHøyTest.forMånedsperiode(januar(2021)),
            fradrag = listOf(
                FradragFactory.nyMånedsperiode(
                    fradragstype = Fradragstype.Sosialstønad,
                    månedsbeløp = 1000.0,
                    måned = januar(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyMånedsperiode(
                    fradragstype = Fradragstype.Sosialstønad,
                    månedsbeløp = 1000.0,
                    måned = januar(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        val februar = MånedsberegningFactory.ny(
            måned = februar(2021),
            fullSupplerendeStønadForMåned = fullSupplerendeStønadHøyTest.forMånedsperiode(februar(2021)),
            fradrag = listOf(
                FradragFactory.nyMånedsperiode(
                    fradragstype = Fradragstype.Sosialstønad,
                    månedsbeløp = 1000.0,
                    måned = februar(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyMånedsperiode(
                    fradragstype = Fradragstype.Sosialstønad,
                    månedsbeløp = 1000.0,
                    måned = februar(2021),
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
            måned = januar(2021),
            fullSupplerendeStønadForMåned = fullSupplerendeStønadHøyTest.forMånedsperiode(januar(2021)),
            fradrag = listOf(
                FradragFactory.nyMånedsperiode(
                    fradragstype = Fradragstype.Sosialstønad,
                    månedsbeløp = 1000.0,
                    måned = januar(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyMånedsperiode(
                    fradragstype = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 1000.0,
                    måned = januar(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyMånedsperiode(
                    fradragstype = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 1000.0,
                    måned = januar(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
                FradragFactory.nyMånedsperiode(
                    fradragstype = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 2000.0,
                    måned = januar(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        val februar = MånedsberegningFactory.ny(
            måned = februar(2021),
            fullSupplerendeStønadForMåned = fullSupplerendeStønadHøyTest.forMånedsperiode(februar(2021)),
            fradrag = listOf(
                FradragFactory.nyMånedsperiode(
                    fradragstype = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 1000.0,
                    måned = februar(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
                FradragFactory.nyMånedsperiode(
                    fradragstype = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 2000.0,
                    måned = februar(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyMånedsperiode(
                    fradragstype = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 1000.0,
                    måned = februar(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyMånedsperiode(
                    fradragstype = Fradragstype.Sosialstønad,
                    månedsbeløp = 1000.0,
                    måned = februar(2021),
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
            måned = januar(2021),
            fullSupplerendeStønadForMåned = fullSupplerendeStønadHøyTest.forMånedsperiode(januar(2021)),
            fradrag = listOf(),
        )

        val februar = MånedsberegningFactory.ny(
            måned = februar(2021),
            fullSupplerendeStønadForMåned = fullSupplerendeStønadHøyTest.forMånedsperiode(februar(2021)),
            fradrag = listOf(),
        )

        val april = MånedsberegningFactory.ny(
            måned = april(2021),
            fullSupplerendeStønadForMåned = fullSupplerendeStønadHøyTest.forMånedsperiode(april(2021)),
            fradrag = listOf(),
        )

        val desember = MånedsberegningFactory.ny(
            måned = desember(2021),
            fullSupplerendeStønadForMåned = fullSupplerendeStønadHøyTest.forMånedsperiode(desember(2021)),
            fradrag = listOf(),
        )

        SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder(listOf(januar, februar, april, desember)).beregningsperioder shouldBe listOf(
            EkvivalenteMånedsberegninger(listOf(januar, februar)),
            EkvivalenteMånedsberegninger(listOf(april)),
            EkvivalenteMånedsberegninger(listOf(desember))
        )
    }
}
