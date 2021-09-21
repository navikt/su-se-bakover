package no.nav.su.se.bakover.domain.beregning

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioderTest {
    @Nested
    inner class Utbetaling {
        private val uføregrunnlagListe = listOf(
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(), opprettet = fixedTidspunkt,
                periode = Periode.create(1.januar(2021), 31.desember(2021)),
                uføregrad = Uføregrad.parse(55), forventetInntekt = 200,
            ),
        )

        @Test
        fun `tilstøtende månedsberegninger hvor alt er likt bortsett fra dato grupperes sammen`() {
            val januar = MånedsberegningFactory.ny(
                periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                sats = Sats.HØY,
                fradrag = listOf(),
            )

            val februar = MånedsberegningFactory.ny(
                periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                sats = Sats.HØY,
                fradrag = listOf(),
            )

            SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder.Utbetaling(
                listOf(januar, februar),
                uføregrunnlagListe,
            ).beregningsperioder shouldBe listOf(
                EkvivalenteMånedsberegningerOgUføre(
                    listOf(
                        MånedsberegningOgTilhørendeUføregrunnlag(januar, uføregrunnlagListe.first()),
                        MånedsberegningOgTilhørendeUføregrunnlag(februar, uføregrunnlagListe.first()),
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
                periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                sats = Sats.HØY,
                fradrag = listOf(),
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
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            )

            SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder.Utbetaling(
                listOf(januar, februar),
                uføregrunnlagListe,
            ).beregningsperioder shouldBe listOf(
                EkvivalenteMånedsberegningerOgUføre(
                    listOf(
                        MånedsberegningOgTilhørendeUføregrunnlag(januar, uføregrunnlagListe.first()),
                    ),
                ).also {
                    it.periode.fraOgMed shouldBe januar.periode.fraOgMed
                    it.periode.tilOgMed shouldBe januar.periode.tilOgMed
                },
                EkvivalenteMånedsberegningerOgUføre(
                    listOf(
                        MånedsberegningOgTilhørendeUføregrunnlag(februar, uføregrunnlagListe.first()),
                    ),
                ).also {
                    it.periode.fraOgMed shouldBe februar.periode.fraOgMed
                    it.periode.tilOgMed shouldBe februar.periode.tilOgMed
                },
            )
        }

        @Test
        fun `like månedsberegninger som ikke tilstøter hverandre grupperes hver for seg`() {
            val januar = MånedsberegningFactory.ny(
                periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                sats = Sats.HØY,
                fradrag = listOf(),
            )

            val februar = MånedsberegningFactory.ny(
                periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                sats = Sats.ORDINÆR,
                fradrag = listOf(),
            )

            val mars = MånedsberegningFactory.ny(
                periode = Periode.create(fraOgMed = 1.mars(2021), tilOgMed = 31.mars(2021)),
                sats = Sats.HØY,
                fradrag = listOf(),
            )

            val april = MånedsberegningFactory.ny(
                periode = Periode.create(fraOgMed = 1.april(2021), tilOgMed = 30.april(2021)),
                sats = Sats.ORDINÆR,
                fradrag = listOf(),
            )

            SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder.Utbetaling(
                listOf(januar, februar, mars, april),
                uføregrunnlagListe,
            ).beregningsperioder shouldBe listOf(
                EkvivalenteMånedsberegningerOgUføre(
                    listOf(
                        MånedsberegningOgTilhørendeUføregrunnlag(januar, uføregrunnlagListe.first()),
                    ),
                ),
                EkvivalenteMånedsberegningerOgUføre(
                    listOf(
                        MånedsberegningOgTilhørendeUføregrunnlag(februar, uføregrunnlagListe.first()),
                    ),
                ),
                EkvivalenteMånedsberegningerOgUføre(
                    listOf(
                        MånedsberegningOgTilhørendeUføregrunnlag(mars, uføregrunnlagListe.first()),
                    ),
                ),
                EkvivalenteMånedsberegningerOgUføre(
                    listOf(
                        MånedsberegningOgTilhørendeUføregrunnlag(april, uføregrunnlagListe.first()),
                    ),
                ),
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
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
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
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 500.0,
                        periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            )

            SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder.Utbetaling(
                listOf(januar, februar),
                uføregrunnlagListe,
            ).beregningsperioder shouldBe listOf(
                EkvivalenteMånedsberegningerOgUføre(
                    listOf(
                        MånedsberegningOgTilhørendeUføregrunnlag(januar, uføregrunnlagListe.first()),
                    ),
                ),
                EkvivalenteMånedsberegningerOgUføre(
                    listOf(
                        MånedsberegningOgTilhørendeUføregrunnlag(februar, uføregrunnlagListe.first()),
                    ),
                ),
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
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    FradragFactory.ny(
                        type = Fradragstype.Sosialstønad,
                        månedsbeløp = 1000.0,
                        periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
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
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    FradragFactory.ny(
                        type = Fradragstype.Sosialstønad,
                        månedsbeløp = 1000.0,
                        periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            )

            SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder.Utbetaling(
                listOf(januar, februar),
                uføregrunnlagListe,
            ).beregningsperioder shouldBe listOf(
                EkvivalenteMånedsberegningerOgUføre(
                    listOf(
                        MånedsberegningOgTilhørendeUføregrunnlag(januar, uføregrunnlagListe.first()),
                        MånedsberegningOgTilhørendeUføregrunnlag(februar, uføregrunnlagListe.first()),
                    ),
                ),
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
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 1000.0,
                        periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 1000.0,
                        periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.EPS,
                    ),
                    FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 2000.0,
                        periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
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
                        tilhører = FradragTilhører.EPS,
                    ),
                    FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 2000.0,
                        periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 1000.0,
                        periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    FradragFactory.ny(
                        type = Fradragstype.Sosialstønad,
                        månedsbeløp = 1000.0,
                        periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
            )

            SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder.Utbetaling(
                listOf(januar, februar),
                uføregrunnlagListe,
            ).beregningsperioder shouldBe listOf(
                EkvivalenteMånedsberegningerOgUføre(
                    listOf(
                        MånedsberegningOgTilhørendeUføregrunnlag(januar, uføregrunnlagListe.first()),
                        MånedsberegningOgTilhørendeUføregrunnlag(februar, uføregrunnlagListe.first()),
                    ),
                ),
            )
        }

        @Test
        fun `like månedsberegninger som ikke er tilstøtende grupperes hver for seg`() {
            val januar = MånedsberegningFactory.ny(
                periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                sats = Sats.HØY,
                fradrag = listOf(),
            )

            val februar = MånedsberegningFactory.ny(
                periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                sats = Sats.HØY,
                fradrag = listOf(),
            )

            val april = MånedsberegningFactory.ny(
                periode = Periode.create(fraOgMed = 1.april(2021), tilOgMed = 30.april(2021)),
                sats = Sats.HØY,
                fradrag = listOf(),
            )

            val desember = MånedsberegningFactory.ny(
                periode = Periode.create(fraOgMed = 1.desember(2021), tilOgMed = 31.desember(2021)),
                sats = Sats.HØY,
                fradrag = listOf(),
            )

            SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder.Utbetaling(
                listOf(januar, februar, april, desember),
                uføregrunnlagListe,
            ).beregningsperioder shouldBe listOf(
                EkvivalenteMånedsberegningerOgUføre(
                    listOf(
                        MånedsberegningOgTilhørendeUføregrunnlag(januar, uføregrunnlagListe.first()),
                        MånedsberegningOgTilhørendeUføregrunnlag(februar, uføregrunnlagListe.first()),
                    ),
                ),
                EkvivalenteMånedsberegningerOgUføre(
                    listOf(
                        MånedsberegningOgTilhørendeUføregrunnlag(april, uføregrunnlagListe.first()),
                    ),
                ),
                EkvivalenteMånedsberegningerOgUføre(
                    listOf(
                        MånedsberegningOgTilhørendeUføregrunnlag(desember, uføregrunnlagListe.first()),
                    ),
                ),
            )
        }

        @Test
        fun `tilhørende uføregrunnlag er alltid den sist opprettet for månedsberegningens periode`() {
            val uføregrunnlagListe = listOf(
                Grunnlag.Uføregrunnlag(
                    id = UUID.randomUUID(), opprettet = fixedTidspunkt,
                    periode = Periode.create(1.januar(2021), 31.desember(2021)),
                    uføregrad = Uføregrad.parse(55), forventetInntekt = 200,
                ),
                Grunnlag.Uføregrunnlag(
                    id = UUID.randomUUID(), opprettet = Tidspunkt.now(),
                    periode = Periode.create(1.januar(2021), 31.desember(2021)),
                    uføregrad = Uføregrad.parse(70), forventetInntekt = 3000,
                ),
            )
            val januar = MånedsberegningFactory.ny(
                periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                sats = Sats.HØY, fradrag = listOf(),
            )
            val februar = MånedsberegningFactory.ny(
                periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                sats = Sats.HØY, fradrag = listOf(),
            )
            val desember = MånedsberegningFactory.ny(
                periode = Periode.create(fraOgMed = 1.desember(2021), tilOgMed = 31.desember(2021)),
                sats = Sats.HØY, fradrag = listOf(),
            )

            val actual = SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder.Utbetaling(
                listOf(januar, februar, desember), uføregrunnlagListe,
            ).beregningsperioder

            actual shouldBe listOf(
                EkvivalenteMånedsberegningerOgUføre(
                    listOf(
                        MånedsberegningOgTilhørendeUføregrunnlag(januar, uføregrunnlagListe.last()),
                        MånedsberegningOgTilhørendeUføregrunnlag(februar, uføregrunnlagListe.last()),
                    ),
                ),
                EkvivalenteMånedsberegningerOgUføre(
                    listOf(
                        MånedsberegningOgTilhørendeUføregrunnlag(desember, uføregrunnlagListe.last()),
                    ),
                ),
            )
        }

        @Test
        fun `ekvivalente månedsberegninger skiller på uføregrad`() {
            val uføregrunnlagListe = listOf(
                Grunnlag.Uføregrunnlag(
                    id = UUID.randomUUID(), opprettet = fixedTidspunkt,
                    periode = Periode.create(1.januar(2021), 31.januar(2021)),
                    uføregrad = Uføregrad.parse(55), forventetInntekt = 200,
                ),
                Grunnlag.Uføregrunnlag(
                    id = UUID.randomUUID(), opprettet = Tidspunkt.now(),
                    periode = Periode.create(1.februar(2021), 31.desember(2021)),
                    uføregrad = Uføregrad.parse(70), forventetInntekt = 3000,
                ),
            )
            val januar = MånedsberegningFactory.ny(
                periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                sats = Sats.HØY, fradrag = listOf(),
            )
            val februar = MånedsberegningFactory.ny(
                periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                sats = Sats.HØY, fradrag = listOf(),
            )

            val actual = SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder.Utbetaling(
                listOf(januar, februar), uføregrunnlagListe,
            ).beregningsperioder

            actual shouldBe listOf(
                EkvivalenteMånedsberegningerOgUføre(
                    listOf(
                        MånedsberegningOgTilhørendeUføregrunnlag(januar, uføregrunnlagListe.first()),
                    ),
                ),
                EkvivalenteMånedsberegningerOgUføre(
                    listOf(
                        MånedsberegningOgTilhørendeUføregrunnlag(februar, uføregrunnlagListe.last()),
                    ),
                ),
            )
        }

        @Test
        fun `kaster exception hvis det finnes en månedsberegning som ikke har en tilhørende uføregrunnlag`() {
            val januar = MånedsberegningFactory.ny(
                periode = Periode.create(fraOgMed = 1.januar(2022), tilOgMed = 31.januar(2022)),
                sats = Sats.HØY, fradrag = listOf(),
            )

            assertThrows<IllegalStateException> {
                SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder.Utbetaling(
                    listOf(januar), uføregrunnlagListe,
                ).beregningsperioder
            }
        }
    }

    @Nested
    inner class Brev {
        @Test
        fun `tilstøtende månedsberegninger hvor alt er likt bortsett fra dato grupperes sammen`() {
            val januar = MånedsberegningFactory.ny(
                periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                sats = Sats.HØY,
                fradrag = listOf(),
            )

            val februar = MånedsberegningFactory.ny(
                periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                sats = Sats.HØY,
                fradrag = listOf(),
            )

            SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder.Brev(
                listOf(januar, februar),
            ).beregningsperioder shouldBe listOf(
                EkvivalenteMånedsberegninger(
                    listOf(januar, februar),
                ).also {
                    it.periode.fraOgMed shouldBe januar.periode.fraOgMed
                    it.periode.tilOgMed shouldBe februar.periode.tilOgMed
                },
            )
        }

        @Test
        fun `like månedsberegninger som ikke tilstøter hverandre grupperes hver for seg`() {
            val januar = MånedsberegningFactory.ny(
                periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
                sats = Sats.HØY,
                fradrag = listOf(),
            )

            val februar = MånedsberegningFactory.ny(
                periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021)),
                sats = Sats.ORDINÆR,
                fradrag = listOf(),
            )

            val mars = MånedsberegningFactory.ny(
                periode = Periode.create(fraOgMed = 1.mars(2021), tilOgMed = 31.mars(2021)),
                sats = Sats.HØY,
                fradrag = listOf(),
            )

            val april = MånedsberegningFactory.ny(
                periode = Periode.create(fraOgMed = 1.april(2021), tilOgMed = 30.april(2021)),
                sats = Sats.ORDINÆR,
                fradrag = listOf(),
            )

            SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder.Brev(
                listOf(januar, februar, mars, april),
            ).beregningsperioder shouldBe listOf(
                EkvivalenteMånedsberegninger(listOf(januar)),
                EkvivalenteMånedsberegninger(listOf(februar)),
                EkvivalenteMånedsberegninger(listOf(mars)),
                EkvivalenteMånedsberegninger(listOf(april)),
            )
        }
    }
}
