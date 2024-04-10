package no.nav.su.se.bakover.domain.beregning

import beregning.domain.BeregningStrategy
import beregning.domain.EkvivalenteMånedsberegninger
import beregning.domain.MånedsberegningFactory
import beregning.domain.SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.domain.beregning.fradrag.lagFradrag
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import vilkår.inntekt.domain.grunnlag.FradragFactory
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype

internal class SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioderTest {

    private val forventetInntekt = lagFradrag(
        type = Fradragstype.ForventetInntekt,
        beløp = 0.0,
        periode = Periode.create(1.januar(2020), 31.desember(2022)),
        tilhører = FradragTilhører.BRUKER,
    )

    @Test
    fun `tilstøtende månedsberegninger hvor alt er likt bortsett fra dato grupperes sammen`() {
        val januar = MånedsberegningFactory.ny(
            måned = januar(2021),
            strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
            fradrag = listOf(forventetInntekt),
        )

        val februar = MånedsberegningFactory.ny(
            måned = februar(2021),
            strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
            fradrag = listOf(forventetInntekt),
        )

        SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder(
            listOf(
                januar,
                februar,
            ),
        ).beregningsperioder shouldBe listOf(
            EkvivalenteMånedsberegninger(
                listOf(
                    januar,
                    februar,
                ),
            ).also {
                it.periode.fraOgMed shouldBe januar.periode.fraOgMed
                it.periode.tilOgMed shouldBe februar.periode.tilOgMed
            },
        )
    }

    @Test
    fun `tilstøtende månedsberegninger som har forskjellige fradrag grupperes hver for seg`() {
        val januar = MånedsberegningFactory.ny(
            måned = januar(2021),
            strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
            fradrag = listOf(forventetInntekt),
        )

        val februar = MånedsberegningFactory.ny(
            måned = februar(2021),
            strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
            fradrag = listOf(
                forventetInntekt,
                FradragFactory.nyMånedsperiode(
                    fradragstype = Fradragstype.Sosialstønad,
                    månedsbeløp = 2000.0,
                    måned = februar(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder(
            listOf(
                januar,
                februar,
            ),
        ).beregningsperioder shouldBe listOf(
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
            strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
            fradrag = listOf(forventetInntekt),
        )

        val februar = MånedsberegningFactory.ny(
            måned = februar(2021),
            strategy = BeregningStrategy.BorMedVoksne(satsFactoryTestPåDato(), Sakstype.UFØRE),
            fradrag = listOf(forventetInntekt),
        )

        val mars = MånedsberegningFactory.ny(
            måned = mars(2021),
            strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
            fradrag = listOf(forventetInntekt),
        )

        val april = MånedsberegningFactory.ny(
            måned = april(2021),
            strategy = BeregningStrategy.BorMedVoksne(satsFactoryTestPåDato(), Sakstype.UFØRE),
            fradrag = listOf(forventetInntekt),
        )

        SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder(
            listOf(
                januar,
                februar,
                mars,
                april,
            ),
        ).beregningsperioder shouldBe listOf(
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
            strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
            fradrag = listOf(
                forventetInntekt,
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
            strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
            fradrag = listOf(
                forventetInntekt,
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

        SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder(
            listOf(
                januar,
                februar,
            ),
        ).beregningsperioder shouldBe listOf(
            EkvivalenteMånedsberegninger(listOf(januar)),
            EkvivalenteMånedsberegninger(listOf(februar)),
        )
    }

    @Test
    fun `månedsberegninger med flere fradrag av samme type grupperes sammen`() {
        val januar = MånedsberegningFactory.ny(
            måned = januar(2021),
            strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
            fradrag = listOf(
                forventetInntekt,
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
            strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
            fradrag = listOf(
                forventetInntekt,
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

        SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder(
            listOf(
                januar,
                februar,
            ),
        ).beregningsperioder shouldBe listOf(
            EkvivalenteMånedsberegninger(listOf(januar, februar)),
        )
    }

    @Test
    fun `grupperer like månedsberegninger sammen selv om fradragene i utgangspunktet ikke ligger på nøyaktig samme indeks`() {
        val januar = MånedsberegningFactory.ny(
            måned = januar(2021),
            strategy = BeregningStrategy.EpsUnder67År(satsFactoryTestPåDato(), Sakstype.UFØRE),
            fradrag = listOf(
                forventetInntekt,
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
            strategy = BeregningStrategy.EpsUnder67År(satsFactoryTestPåDato(), Sakstype.UFØRE),
            fradrag = listOf(
                forventetInntekt,
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

        SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder(
            listOf(
                januar,
                februar,
            ),
        ).beregningsperioder shouldBe listOf(
            EkvivalenteMånedsberegninger(listOf(januar, februar)),
        )
    }

    @Test
    fun `like månedsberegninger som ikke er tilstøtende grupperes hver for seg`() {
        val januar = MånedsberegningFactory.ny(
            måned = januar(2021),
            strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
            fradrag = listOf(forventetInntekt),
        )

        val februar = MånedsberegningFactory.ny(
            måned = februar(2021),
            strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
            fradrag = listOf(forventetInntekt),
        )

        val april = MånedsberegningFactory.ny(
            måned = april(2021),
            strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
            fradrag = listOf(forventetInntekt),
        )

        val desember = MånedsberegningFactory.ny(
            måned = desember(2021),
            strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
            fradrag = listOf(forventetInntekt),
        )

        SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder(
            listOf(
                januar,
                februar,
                april,
                desember,
            ),
        ).beregningsperioder shouldBe listOf(
            EkvivalenteMånedsberegninger(listOf(januar, februar)),
            EkvivalenteMånedsberegninger(listOf(april)),
            EkvivalenteMånedsberegninger(listOf(desember)),
        )
    }

    @Test
    fun `kaster for utrygge operasjoner`() {
        assertThrows<EkvivalenteMånedsberegninger.UtryggOperasjonException> {
            SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder(
                listOf(
                    MånedsberegningFactory.ny(
                        måned = januar(2021),
                        strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
                        fradrag = listOf(forventetInntekt),
                    ),
                ),
            ).beregningsperioder.first().måned
        }

        assertThrows<EkvivalenteMånedsberegninger.UtryggOperasjonException> {
            SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder(
                listOf(
                    MånedsberegningFactory.ny(
                        måned = januar(2021),
                        strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
                        fradrag = listOf(forventetInntekt),
                    ),
                ),
            ).beregningsperioder.first().fullSupplerendeStønadForMåned
        }

        assertThrows<EkvivalenteMånedsberegninger.UtryggOperasjonException> {
            SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder(
                listOf(
                    MånedsberegningFactory.ny(
                        måned = januar(2021),
                        strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
                        fradrag = listOf(forventetInntekt),
                    ),
                ),
            ).beregningsperioder.first().getFradrag()
        }
    }

    @Test
    fun `tryner hvis måendene ikke er ekvivalent`() {
        assertThrows<IllegalArgumentException> {
            EkvivalenteMånedsberegninger(
                listOf(
                    MånedsberegningFactory.ny(
                        måned = januar(2021),
                        strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
                        fradrag = listOf(forventetInntekt),
                    ),
                    MånedsberegningFactory.ny(
                        måned = januar(2021),
                        strategy = BeregningStrategy.BorMedVoksne(satsFactoryTestPåDato(), Sakstype.UFØRE),
                        fradrag = listOf(forventetInntekt),
                    ),
                ),
            )
        }

        assertThrows<IllegalArgumentException> {
            EkvivalenteMånedsberegninger(
                listOf(
                    MånedsberegningFactory.ny(
                        måned = januar(2021),
                        strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
                        fradrag = listOf(forventetInntekt),
                    ),
                    MånedsberegningFactory.ny(
                        måned = februar(2021),
                        strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
                        fradrag = listOf(
                            forventetInntekt,
                            FradragFactory.nyMånedsperiode(
                                fradragstype = Fradragstype.Sosialstønad,
                                månedsbeløp = 1000.0,
                                måned = februar(2021),
                                utenlandskInntekt = null,
                                tilhører = FradragTilhører.BRUKER,
                            ),
                        ),
                    ),
                ),
            )
        }
    }
}
