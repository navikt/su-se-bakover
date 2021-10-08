package no.nav.su.se.bakover.domain.beregning.beregning

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.april
import no.nav.su.se.bakover.common.periode.august
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.juli
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.common.periode.november
import no.nav.su.se.bakover.common.periode.oktober
import no.nav.su.se.bakover.common.periode.september
import no.nav.su.se.bakover.domain.beregning.BeregningMedVirkningstidspunkt
import no.nav.su.se.bakover.domain.beregning.Endring
import no.nav.su.se.bakover.domain.beregning.Merknad
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.MånedsberegningFactory
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.endringFra
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.IkkePeriodisertFradrag
import org.junit.jupiter.api.Test

internal class BeregningMedVirkningstidspunktTest {

    @Test
    fun `summer for enkel beregning med virk`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
            utgangspunkt = null,
        )
        beregning.getSumYtelse() shouldBe 250116
        beregning.getSumFradrag() shouldBe 0
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (20637 to 0.0)),
                (februar(2020) to (20637 to 0.0)),
                (mars(2020) to (20637 to 0.0)),
                (april(2020) to (20637 to 0.0)),
                (mai(2020) to (20946 to 0.0)),
                (juni(2020) to (20946 to 0.0)),
                (juli(2020) to (20946 to 0.0)),
                (august(2020) to (20946 to 0.0)),
                (september(2020) to (20946 to 0.0)),
                (oktober(2020) to (20946 to 0.0)),
                (november(2020) to (20946 to 0.0)),
                (desember(2020) to (20946 to 0.0)),
            ),
        )
        beregning.merknader() shouldBe listOf(
            Merknad.NyYtelse(
                benyttetBeregning = Merknad.MerknadMånedsberegning(
                    periode = Periode.create(
                        fraOgMed = 1.januar(2020),
                        tilOgMed = 31.januar(2020),
                    ),
                    sats = Sats.HØY,
                    grunnbeløp = 99858,
                    beløp = 20637,
                    fradrag = listOf(
                        Merknad.MerknadFradrag(
                            periode = Periode.create(
                                fraOgMed = 1.januar(2020),
                                tilOgMed = 31.januar(2020),
                            ),
                            fradragstype = Fradragstype.ForventetInntekt,
                            månedsbeløp = 0.0,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                    satsbeløp = Sats.HØY.månedsbeløp(1.januar(2020)),
                    fribeløpForEps = 0.0,
                ),
            ),
            Merknad.EndringGrunnbeløp(
                gammeltGrunnbeløp = Merknad.EndringGrunnbeløp.Detalj(
                    dato = 1.mai(2019),
                    grunnbeløp = 99858,
                ),
                nyttGrunnbeløp = Merknad.EndringGrunnbeløp.Detalj(
                    dato = 1.mai(2020),
                    grunnbeløp = 101351,
                ),
            ),
        )
    }

    @Test
    fun `flytter ikke virkningspunkt dersom det er 10 prosent økning i forhold til utgangspunkt`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))

        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 10000.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
            utgangspunkt = MånedsberegningFactory.ny(
                periode = desember(2019),
                sats = Sats.HØY,
                fradrag = listOf(
                    FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 15000.0,
                        periode = desember(2019),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
                fribeløpForEps = 0.0,
            ),
        )
        beregning.getSumYtelse() shouldBe 130116
        beregning.getSumFradrag() shouldBe 120000
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (10637 to 10000.0)),
                (februar(2020) to (10637 to 10000.0)),
                (mars(2020) to (10637 to 10000.0)),
                (april(2020) to (10637 to 10000.0)),
                (mai(2020) to (10946 to 10000.0)),
                (juni(2020) to (10946 to 10000.0)),
                (juli(2020) to (10946 to 10000.0)),
                (august(2020) to (10946 to 10000.0)),
                (september(2020) to (10946 to 10000.0)),
                (oktober(2020) to (10946 to 10000.0)),
                (november(2020) to (10946 to 10000.0)),
                (desember(2020) to (10946 to 10000.0)),
            ),
        )
    }

    @Test
    fun `flytt virkningspunkt hvis første har mer enn 10 prosent reduksjon i forhold til utgangspunkt`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))

        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 10000.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
            utgangspunkt = MånedsberegningFactory.ny(
                periode = desember(2019),
                sats = Sats.HØY,
                fradrag = emptyList(),
                fribeløpForEps = 0.0,
            ),
        )
        beregning.getSumYtelse() shouldBe 140116
        beregning.getSumFradrag() shouldBe 110000
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (20637 to 0.0)),
                (februar(2020) to (10637 to 10000.0)),
                (mars(2020) to (10637 to 10000.0)),
                (april(2020) to (10637 to 10000.0)),
                (mai(2020) to (10946 to 10000.0)),
                (juni(2020) to (10946 to 10000.0)),
                (juli(2020) to (10946 to 10000.0)),
                (august(2020) to (10946 to 10000.0)),
                (september(2020) to (10946 to 10000.0)),
                (oktober(2020) to (10946 to 10000.0)),
                (november(2020) to (10946 to 10000.0)),
                (desember(2020) to (10946 to 10000.0)),
            ),
        )
    }

    @Test
    fun `bruker utgangspunkt hvis det er mindre enn 10 prosent økning fra første måned`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))

        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 9500.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
            utgangspunkt = MånedsberegningFactory.ny(
                periode = desember(2019),
                sats = Sats.HØY,
                fradrag = listOf(
                    FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 10000.0,
                        periode = desember(2019),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
                fribeløpForEps = 0.0,
            ),
        )
        beregning.getSumYtelse() shouldBe 130116
        beregning.getSumFradrag() shouldBe 120000
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (10637 to 10000.0)),
                (februar(2020) to (10637 to 10000.0)),
                (mars(2020) to (10637 to 10000.0)),
                (april(2020) to (10637 to 10000.0)),
                (mai(2020) to (10946 to 10000.0)),
                (juni(2020) to (10946 to 10000.0)),
                (juli(2020) to (10946 to 10000.0)),
                (august(2020) to (10946 to 10000.0)),
                (september(2020) to (10946 to 10000.0)),
                (oktober(2020) to (10946 to 10000.0)),
                (november(2020) to (10946 to 10000.0)),
                (desember(2020) to (10946 to 10000.0)),
            ),
        )
        beregning.merknader().let {
            it.filterIsInstance<Merknad.ØktYtelse>() shouldHaveSize 0
            it.filterIsInstance<Merknad.RedusertYtelse>() shouldHaveSize 0
            it.filterIsInstance<Merknad.EndringUnderTiProsent>() shouldHaveSize 12
            it.filterIsInstance<Merknad.EndringUnderTiProsent>()[0] shouldBe Merknad.EndringUnderTiProsent(
                benyttetBeregning = Merknad.MerknadMånedsberegning(
                    periode = Periode.create(
                        fraOgMed = 1.januar(2020),
                        tilOgMed = 31.januar(2020),
                    ),
                    sats = Sats.HØY,
                    grunnbeløp = 99858,
                    beløp = 10637,
                    fradrag = listOf(
                        Merknad.MerknadFradrag(
                            periode = Periode.create(
                                fraOgMed = 1.januar(2020),
                                tilOgMed = 31.januar(2020),
                            ),
                            fradragstype = Fradragstype.Arbeidsinntekt,
                            månedsbeløp = 10000.0,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                    satsbeløp = Sats.HØY.månedsbeløp(1.januar(2020)),
                    fribeløpForEps = 0.0,
                ),
                forkastetBeregning = Merknad.MerknadMånedsberegning(
                    periode = Periode.create(
                        fraOgMed = 1.januar(2020),
                        tilOgMed = 31.januar(2020),
                    ),
                    sats = Sats.HØY,
                    grunnbeløp = 99858,
                    beløp = 11137,
                    fradrag = listOf(
                        Merknad.MerknadFradrag(
                            periode = Periode.create(
                                fraOgMed = 1.januar(2020),
                                tilOgMed = 31.januar(2020),
                            ),
                            fradragstype = Fradragstype.ForventetInntekt,
                            månedsbeløp = 9500.0,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                    satsbeløp = Sats.HØY.månedsbeløp(1.januar(2020)),
                    fribeløpForEps = 0.0,
                ),
            )
            it.filterIsInstance<Merknad.EndringGrunnbeløp>() shouldBe listOf(
                Merknad.EndringGrunnbeløp(
                    gammeltGrunnbeløp = Merknad.EndringGrunnbeløp.Detalj(
                        dato = 1.mai(2019),
                        grunnbeløp = 99858,
                    ),
                    nyttGrunnbeløp = Merknad.EndringGrunnbeløp.Detalj(
                        dato = 1.mai(2020),
                        grunnbeløp = 101351,
                    ),
                ),
            )
        }
    }

    @Test
    fun `bruker utgangspunkt hvis det er mindre enn 10 prosent reduksjon fra første måned`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))

        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 10500.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
            utgangspunkt = MånedsberegningFactory.ny(
                periode = desember(2019),
                sats = Sats.HØY,
                fradrag = listOf(
                    FradragFactory.ny(
                        type = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 10000.0,
                        periode = desember(2019),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
                fribeløpForEps = 0.0,
            ),
        )
        beregning.getSumYtelse() shouldBe 130116
        beregning.getSumFradrag() shouldBe 120000
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (10637 to 10000.0)),
                (februar(2020) to (10637 to 10000.0)),
                (mars(2020) to (10637 to 10000.0)),
                (april(2020) to (10637 to 10000.0)),
                (mai(2020) to (10946 to 10000.0)),
                (juni(2020) to (10946 to 10000.0)),
                (juli(2020) to (10946 to 10000.0)),
                (august(2020) to (10946 to 10000.0)),
                (september(2020) to (10946 to 10000.0)),
                (oktober(2020) to (10946 to 10000.0)),
                (november(2020) to (10946 to 10000.0)),
                (desember(2020) to (10946 to 10000.0)),
            ),
        )
        beregning.merknader().let {
            it.filterIsInstance<Merknad.EndringGrunnbeløp>() shouldHaveSize 1
            it.filterIsInstance<Merknad.ØktYtelse>() shouldHaveSize 0
            it.filterIsInstance<Merknad.RedusertYtelse>() shouldHaveSize 0
            it.filterIsInstance<Merknad.EndringUnderTiProsent>() shouldHaveSize 12
            it.filterIsInstance<Merknad.EndringUnderTiProsent>()[11] shouldBe Merknad.EndringUnderTiProsent(
                benyttetBeregning = Merknad.MerknadMånedsberegning(
                    periode = Periode.create(
                        fraOgMed = 1.desember(2020),
                        tilOgMed = 31.desember(2020),
                    ),
                    sats = Sats.HØY,
                    grunnbeløp = 101351,
                    beløp = 10946,
                    fradrag = listOf(
                        Merknad.MerknadFradrag(
                            periode = Periode.create(
                                fraOgMed = 1.desember(2020),
                                tilOgMed = 31.desember(2020),
                            ),
                            fradragstype = Fradragstype.Arbeidsinntekt,
                            månedsbeløp = 10000.0,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                    satsbeløp = Sats.HØY.månedsbeløp(1.desember(2020)),
                    fribeløpForEps = 0.0,
                ),
                forkastetBeregning = Merknad.MerknadMånedsberegning(
                    periode = Periode.create(
                        fraOgMed = 1.desember(2020),
                        tilOgMed = 31.desember(2020),
                    ),
                    sats = Sats.HØY,
                    grunnbeløp = 101351,
                    beløp = 10446,
                    fradrag = listOf(
                        Merknad.MerknadFradrag(
                            periode = Periode.create(
                                fraOgMed = 1.desember(2020),
                                tilOgMed = 31.desember(2020),
                            ),
                            fradragstype = Fradragstype.ForventetInntekt,
                            månedsbeløp = 10500.0,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                    satsbeløp = Sats.HØY.månedsbeløp(1.desember(2020)),
                    fribeløpForEps = 0.0,
                ),
            )
            it.filterIsInstance<Merknad.EndringGrunnbeløp>() shouldBe listOf(
                Merknad.EndringGrunnbeløp(
                    gammeltGrunnbeløp = Merknad.EndringGrunnbeløp.Detalj(
                        dato = 1.mai(2019),
                        grunnbeløp = 99858,
                    ),
                    nyttGrunnbeløp = Merknad.EndringGrunnbeløp.Detalj(
                        dato = 1.mai(2020),
                        grunnbeløp = 101351,
                    ),
                ),
            )
        }
    }

    @Test
    fun `kan ikke sende inn utgangspunkt som ikke er lik måneden før perioden som skal beregnes`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))

        shouldThrow<IllegalArgumentException> {
            BeregningMedVirkningstidspunkt(
                periode = periode,
                sats = Sats.HØY,
                fradrag = listOf(
                    FradragFactory.ny(
                        type = Fradragstype.ForventetInntekt,
                        månedsbeløp = 10000.0,
                        periode = periode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
                fradragStrategy = FradragStrategy.Enslig,
                utgangspunkt = MånedsberegningFactory.ny(
                    periode = januar(2021),
                    sats = Sats.HØY,
                    fradrag = emptyList(),
                    fribeløpForEps = 0.0,
                ),
            )
        }
    }

    @Test
    fun `fradrag for alle perioder med virk`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 1000.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
            utgangspunkt = null,
        )
        beregning.getSumYtelse() shouldBe 238116
        beregning.getSumFradrag() shouldBe 12000
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (19637 to 1000.0)),
                (februar(2020) to (19637 to 1000.0)),
                (mars(2020) to (19637 to 1000.0)),
                (april(2020) to (19637 to 1000.0)),
                (mai(2020) to (19946 to 1000.0)),
                (juni(2020) to (19946 to 1000.0)),
                (juli(2020) to (19946 to 1000.0)),
                (august(2020) to (19946 to 1000.0)),
                (september(2020) to (19946 to 1000.0)),
                (oktober(2020) to (19946 to 1000.0)),
                (november(2020) to (19946 to 1000.0)),
                (desember(2020) to (19946 to 1000.0)),
            ),
        )
    }

    @Test
    fun `g-regulering skal tre i kraft selv om reguleringsmåned ikke har 10 prosent endring`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.EpsOver67År,
            utgangspunkt = null,
        )
        beregning.getSumYtelse() shouldBe 250116
        beregning.getSumFradrag() shouldBe 0
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (20637 to 0.0)),
                (februar(2020) to (20637 to 0.0)),
                (mars(2020) to (20637 to 0.0)),
                (april(2020) to (20637 to 0.0)),
                (mai(2020) to (20946 to 0.0)),
                (juni(2020) to (20946 to 0.0)),
                (juli(2020) to (20946 to 0.0)),
                (august(2020) to (20946 to 0.0)),
                (september(2020) to (20946 to 0.0)),
                (oktober(2020) to (20946 to 0.0)),
                (november(2020) to (20946 to 0.0)),
                (desember(2020) to (20946 to 0.0)),
            ),
        )
        beregning.merknader() shouldBe listOf(
            Merknad.NyYtelse(
                benyttetBeregning = Merknad.MerknadMånedsberegning(
                    periode = Periode.create(
                        fraOgMed = 1.januar(2020),
                        tilOgMed = 31.januar(2020),
                    ),
                    sats = Sats.HØY,
                    grunnbeløp = 99858,
                    beløp = 20637,
                    fradrag = listOf(
                        Merknad.MerknadFradrag(
                            periode = Periode.create(
                                fraOgMed = 1.januar(2020),
                                tilOgMed = 31.januar(2020),
                            ),
                            fradragstype = Fradragstype.ForventetInntekt,
                            månedsbeløp = 0.0,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                    satsbeløp = Sats.HØY.månedsbeløp(1.januar(2020)),
                    fribeløpForEps = 14674.916666666666,
                ),
            ),
            Merknad.EndringGrunnbeløp(
                gammeltGrunnbeløp = Merknad.EndringGrunnbeløp.Detalj(
                    dato = 1.mai(2019),
                    grunnbeløp = 99858,
                ),
                nyttGrunnbeløp = Merknad.EndringGrunnbeløp.Detalj(
                    dato = 1.mai(2020),
                    grunnbeløp = 101351,
                ),
            ),
        )
    }

    @Test
    fun `g-regulering skal tre i kraft selv om reguleringsmåned har positiv 10 prosent endring`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.ny(
                    type = Fradragstype.PrivatPensjon,
                    månedsbeløp = 10000.0,
                    periode = Periode.create(1.januar(2020), 30.april(2020)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.ny(
                    type = Fradragstype.PrivatPensjon,
                    månedsbeløp = 7000.0,
                    periode = Periode.create(1.mai(2020), 31.desember(2020)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
            utgangspunkt = null,
        )
        beregning.getSumYtelse() shouldBe 154116
        beregning.getSumFradrag() shouldBe 96000
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (10637 to 10000.0)),
                (februar(2020) to (10637 to 10000.0)),
                (mars(2020) to (10637 to 10000.0)),
                (april(2020) to (10637 to 10000.0)),
                (mai(2020) to (13946 to 7000.0)),
                (juni(2020) to (13946 to 7000.0)),
                (juli(2020) to (13946 to 7000.0)),
                (august(2020) to (13946 to 7000.0)),
                (september(2020) to (13946 to 7000.0)),
                (oktober(2020) to (13946 to 7000.0)),
                (november(2020) to (13946 to 7000.0)),
                (desember(2020) to (13946 to 7000.0)),
            ),
        )
        beregning.merknader() shouldBe listOf(
            Merknad.NyYtelse(
                benyttetBeregning = Merknad.MerknadMånedsberegning(
                    periode = Periode.create(
                        fraOgMed = 1.januar(2020),
                        tilOgMed = 31.januar(2020),
                    ),
                    sats = Sats.HØY,
                    grunnbeløp = 99858,
                    beløp = 10637,
                    fradrag = listOf(
                        Merknad.MerknadFradrag(
                            periode = Periode.create(
                                fraOgMed = 1.januar(2020),
                                tilOgMed = 31.januar(2020),
                            ),
                            fradragstype = Fradragstype.ForventetInntekt,
                            månedsbeløp = 0.0,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                        Merknad.MerknadFradrag(
                            periode = Periode.create(
                                fraOgMed = 1.januar(2020),
                                tilOgMed = 31.januar(2020),
                            ),
                            fradragstype = Fradragstype.PrivatPensjon,
                            månedsbeløp = 10000.0,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                    satsbeløp = Sats.HØY.månedsbeløp(1.januar(2020)),
                    fribeløpForEps = 0.0,
                ),
            ),
            Merknad.EndringGrunnbeløp(
                gammeltGrunnbeløp = Merknad.EndringGrunnbeløp.Detalj(
                    dato = 1.mai(2019),
                    grunnbeløp = 99858,
                ),
                nyttGrunnbeløp = Merknad.EndringGrunnbeløp.Detalj(
                    dato = 1.mai(2020),
                    grunnbeløp = 101351,
                ),
            ),
            Merknad.ØktYtelse(
                benyttetBeregning = Merknad.MerknadMånedsberegning(
                    periode = Periode.create(
                        fraOgMed = 1.mai(2020),
                        tilOgMed = 31.mai(2020),
                    ),
                    sats = Sats.HØY,
                    grunnbeløp = 101351,
                    beløp = 13946,
                    fradrag = listOf(
                        Merknad.MerknadFradrag(
                            periode = Periode.create(
                                fraOgMed = 1.mai(2020),
                                tilOgMed = 31.mai(2020),
                            ),
                            fradragstype = Fradragstype.ForventetInntekt,
                            månedsbeløp = 0.0,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                        Merknad.MerknadFradrag(
                            periode = Periode.create(
                                fraOgMed = 1.mai(2020),
                                tilOgMed = 31.mai(2020),
                            ),
                            fradragstype = Fradragstype.PrivatPensjon,
                            månedsbeløp = 7000.0,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                    satsbeløp = Sats.HØY.månedsbeløp(1.mai(2020)),
                    fribeløpForEps = 0.0,
                ),
                forkastetBeregning = Merknad.MerknadMånedsberegning(
                    periode = Periode.create(
                        fraOgMed = 1.mai(2020),
                        tilOgMed = 31.mai(2020),
                    ),
                    sats = Sats.HØY,
                    grunnbeløp = 101351,
                    beløp = 10946,
                    fradrag = listOf(
                        Merknad.MerknadFradrag(
                            periode = Periode.create(
                                fraOgMed = 1.mai(2020),
                                tilOgMed = 31.mai(2020),
                            ),
                            fradragstype = Fradragstype.ForventetInntekt,
                            månedsbeløp = 0.0,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                        Merknad.MerknadFradrag(
                            periode = Periode.create(
                                fraOgMed = 1.mai(2020),
                                tilOgMed = 31.mai(2020),
                            ),
                            fradragstype = Fradragstype.PrivatPensjon,
                            månedsbeløp = 10000.0,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                    satsbeløp = Sats.HØY.månedsbeløp(1.mai(2020)),
                    fribeløpForEps = 0.0,
                ),
            ),
        )
    }

    @Test
    fun `g-regulering skal tre i kraft selv om reguleringsmåned har negativ 10 prosent endring`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 10000.0,
                    periode = Periode.create(1.januar(2020), 30.april(2020)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 13000.0,
                    periode = Periode.create(1.mai(2020), 31.desember(2020)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
            utgangspunkt = null,
        )
        beregning.getSumYtelse() shouldBe 109116
        beregning.getSumFradrag() shouldBe 141000
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (10637 to 10000.0)),
                (februar(2020) to (10637 to 10000.0)),
                (mars(2020) to (10637 to 10000.0)),
                (april(2020) to (10637 to 10000.0)),
                (mai(2020) to (10946 to 10000.0)),
                (juni(2020) to (7946 to 13000.0)),
                (juli(2020) to (7946 to 13000.0)),
                (august(2020) to (7946 to 13000.0)),
                (september(2020) to (7946 to 13000.0)),
                (oktober(2020) to (7946 to 13000.0)),
                (november(2020) to (7946 to 13000.0)),
                (desember(2020) to (7946 to 13000.0)),
            ),
        )
    }

    @Test
    fun `g-regulering skal tre i kraft selv om måneden forut for reguleringsmåned har negativ 10 prosent endring`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 10000.0,
                    periode = Periode.create(1.januar(2020), 31.mars(2020)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 13000.0,
                    periode = Periode.create(1.april(2020), 31.desember(2020)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
            utgangspunkt = null,
        )
        beregning.getSumYtelse() shouldBe 106116
        beregning.getSumFradrag() shouldBe 144000
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (10637 to 10000.0)),
                (februar(2020) to (10637 to 10000.0)),
                (mars(2020) to (10637 to 10000.0)),
                (april(2020) to (10637 to 10000.0)),
                (mai(2020) to (7946 to 13000.0)),
                (juni(2020) to (7946 to 13000.0)),
                (juli(2020) to (7946 to 13000.0)),
                (august(2020) to (7946 to 13000.0)),
                (september(2020) to (7946 to 13000.0)),
                (oktober(2020) to (7946 to 13000.0)),
                (november(2020) to (7946 to 13000.0)),
                (desember(2020) to (7946 to 13000.0)),
            ),
        )
    }

    @Test
    fun `fradrag for ensom måned med virk`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 5000.0,
                    periode = april(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
            utgangspunkt = null,
        )
        beregning.getSumYtelse() shouldBe 250116
        beregning.getSumFradrag() shouldBe 0
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (20637 to 0.0)),
                (februar(2020) to (20637 to 0.0)),
                (mars(2020) to (20637 to 0.0)),
                (april(2020) to (20637 to 0.0)),
                (mai(2020) to (20946 to 0.0)),
                (juni(2020) to (20946 to 0.0)),
                (juli(2020) to (20946 to 0.0)),
                (august(2020) to (20946 to 0.0)),
                (september(2020) to (20946 to 0.0)),
                (oktober(2020) to (20946 to 0.0)),
                (november(2020) to (20946 to 0.0)),
                (desember(2020) to (20946 to 0.0)),
            ),
        )
    }

    @Test
    fun `fradrag for to påfølgende måneder med virk`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 5000.0,
                    periode = Periode.create(1.mars(2020), 30.april(2020)),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
            utgangspunkt = null,
        )

        beregning.getSumYtelse() shouldBe 245116
        beregning.getSumFradrag() shouldBe 5000
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (20637 to 0.0)),
                (februar(2020) to (20637 to 0.0)),
                (mars(2020) to (20637 to 0.0)),
                (april(2020) to (15637 to 5000.0)),
                (mai(2020) to (20946 to 0.0)),
                (juni(2020) to (20946 to 0.0)),
                (juli(2020) to (20946 to 0.0)),
                (august(2020) to (20946 to 0.0)),
                (september(2020) to (20946 to 0.0)),
                (oktober(2020) to (20946 to 0.0)),
                (november(2020) to (20946 to 0.0)),
                (desember(2020) to (20946 to 0.0)),
            ),
        )
    }

    @Test
    fun `fradrag for annenhver måneder med virk`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 5000.0,
                    periode = februar(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 5000.0,
                    periode = april(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 5000.0,
                    periode = juni(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 5000.0,
                    periode = august(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 5000.0,
                    periode = oktober(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
            utgangspunkt = null,
        )

        beregning.getSumYtelse() shouldBe 250116
        beregning.getSumFradrag() shouldBe 0
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (20637 to 0.0)),
                (februar(2020) to (20637 to 0.0)),
                (mars(2020) to (20637 to 0.0)),
                (april(2020) to (20637 to 0.0)),
                (mai(2020) to (20946 to 0.0)),
                (juni(2020) to (20946 to 0.0)),
                (juli(2020) to (20946 to 0.0)),
                (august(2020) to (20946 to 0.0)),
                (september(2020) to (20946 to 0.0)),
                (oktober(2020) to (20946 to 0.0)),
                (november(2020) to (20946 to 0.0)),
                (desember(2020) to (20946 to 0.0)),
            ),
        )
    }

    @Test
    fun `inntekter øker i påfølgende`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 1000.0,
                    periode = mars(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 2000.0,
                    periode = april(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 3000.0,
                    periode = mai(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 4000.0,
                    periode = juni(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 5000.0,
                    periode = juli(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 6000.0,
                    periode = august(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 7000.0,
                    periode = september(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 8000.0,
                    periode = oktober(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
            utgangspunkt = null,
        )

        beregning.getSumYtelse() shouldBe 227116
        beregning.getSumFradrag() shouldBe 23000
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (20637 to 0.0)),
                (februar(2020) to (20637 to 0.0)),
                (mars(2020) to (20637 to 0.0)),
                (april(2020) to (20637 to 0.0)),
                (mai(2020) to (20946 to 0.0)),
                (juni(2020) to (17946 to 3000.0)),
                (juli(2020) to (17946 to 3000.0)),
                (august(2020) to (15946 to 5000.0)),
                (september(2020) to (15946 to 5000.0)),
                (oktober(2020) to (13946 to 7000.0)),
                (november(2020) to (20946 to 0.0)),
                (desember(2020) to (20946 to 0.0)),
            ),
        )
    }

    @Test
    fun `inntekter reduseres i påfølgende`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 2000.0,
                    periode = januar(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 8000.0,
                    periode = februar(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 7000.0,
                    periode = mars(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 6000.0,
                    periode = april(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 5000.0,
                    periode = mai(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 4000.0,
                    periode = juni(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 2000.0,
                    periode = juli(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 1000.0,
                    periode = august(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
            utgangspunkt = null,
        )

        beregning.getSumYtelse() shouldBe 218116
        beregning.getSumFradrag() shouldBe 32000
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (18637 to 2000.0)),
                (februar(2020) to (18637 to 2000.0)),
                (mars(2020) to (12637 to 8000.0)),
                (april(2020) to (14637 to 6000.0)),
                (mai(2020) to (14946 to 6000.0)),
                (juni(2020) to (16946 to 4000.0)),
                (juli(2020) to (18946 to 2000.0)),
                (august(2020) to (18946 to 2000.0)),
                (september(2020) to (20946 to 0.0)),
                (oktober(2020) to (20946 to 0.0)),
                (november(2020) to (20946 to 0.0)),
                (desember(2020) to (20946 to 0.0)),
            ),
        )
    }

    @Test
    fun `økning og reduksjon`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 1000.0,
                    periode = februar(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 10000.0,
                    periode = mars(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 10000.0,
                    periode = april(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 10000.0,
                    periode = mai(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 9000.0,
                    periode = juni(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 9000.0,
                    periode = juli(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 6000.0,
                    periode = august(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 7000.0,
                    periode = september(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 15000.0,
                    periode = oktober(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
            utgangspunkt = null,
        )

        beregning.getSumYtelse() shouldBe 192116
        beregning.getSumFradrag() shouldBe 58000
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (20637 to 0.0)),
                (februar(2020) to (20637 to 0.0)),
                (mars(2020) to (20637 to 0.0)),
                (april(2020) to (10637 to 10000.0)),
                (mai(2020) to (10946 to 10000.0)),
                (juni(2020) to (10946 to 10000.0)),
                (juli(2020) to (10946 to 10000.0)),
                (august(2020) to (14946 to 6000.0)),
                (september(2020) to (14946 to 6000.0)),
                (oktober(2020) to (14946 to 6000.0)),
                (november(2020) to (20946 to 0.0)),
                (desember(2020) to (20946 to 0.0)),
            ),
        )
    }

    @Test
    fun `varig inntektsøkning`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 5000.0,
                    periode = Periode.create(1.juli(2020), 31.desember(2020)),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.EpsOver67År,
            utgangspunkt = null,
        )

        beregning.getSumYtelse() shouldBe 225116
        beregning.getSumFradrag() shouldBe 25000
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (20637 to 0.0)),
                (februar(2020) to (20637 to 0.0)),
                (mars(2020) to (20637 to 0.0)),
                (april(2020) to (20637 to 0.0)),
                (mai(2020) to (20946 to 0.0)),
                (juni(2020) to (20946 to 0.0)),
                (juli(2020) to (20946 to 0.0)),
                (august(2020) to (15946 to 5000.0)),
                (september(2020) to (15946 to 5000.0)),
                (oktober(2020) to (15946 to 5000.0)),
                (november(2020) to (15946 to 5000.0)),
                (desember(2020) to (15946 to 5000.0)),
            ),
        )
        beregning.merknader() shouldBe listOf(
            Merknad.NyYtelse(
                benyttetBeregning = Merknad.MerknadMånedsberegning(
                    periode = Periode.create(
                        fraOgMed = 1.januar(2020),
                        tilOgMed = 31.januar(2020),
                    ),
                    sats = Sats.HØY,
                    grunnbeløp = 99858,
                    beløp = 20637,
                    fradrag = listOf(
                        Merknad.MerknadFradrag(
                            periode = Periode.create(
                                fraOgMed = 1.januar(2020),
                                tilOgMed = 31.januar(2020),
                            ),
                            fradragstype = Fradragstype.ForventetInntekt,
                            månedsbeløp = 0.0,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                    satsbeløp = Sats.HØY.månedsbeløp(1.januar(2020)),
                    fribeløpForEps = 14674.916666666666,
                ),
            ),
            Merknad.EndringGrunnbeløp(
                gammeltGrunnbeløp = Merknad.EndringGrunnbeløp.Detalj(
                    dato = 1.mai(2019),
                    grunnbeløp = 99858,
                ),
                nyttGrunnbeløp = Merknad.EndringGrunnbeløp.Detalj(
                    dato = 1.mai(2020),
                    grunnbeløp = 101351,
                ),
            ),
            Merknad.RedusertYtelse(
                benyttetBeregning = Merknad.MerknadMånedsberegning(
                    periode = Periode.create(
                        fraOgMed = 1.juli(2020),
                        tilOgMed = 31.juli(2020),
                    ),
                    sats = Sats.HØY,
                    grunnbeløp = 101351,
                    beløp = 20946,
                    fradrag = listOf(
                        Merknad.MerknadFradrag(
                            periode = Periode.create(
                                fraOgMed = 1.juli(2020),
                                tilOgMed = 31.juli(2020),
                            ),
                            fradragstype = Fradragstype.ForventetInntekt,
                            månedsbeløp = 0.0,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                    satsbeløp = Sats.HØY.månedsbeløp(1.juli(2020)),
                    fribeløpForEps = 14810.333333333334,
                ),
                forkastetBeregning = Merknad.MerknadMånedsberegning(
                    periode = Periode.create(
                        fraOgMed = 1.juli(2020),
                        tilOgMed = 31.juli(2020),
                    ),
                    sats = Sats.HØY,
                    grunnbeløp = 101351,
                    beløp = 15946,
                    fradrag = listOf(
                        Merknad.MerknadFradrag(
                            periode = Periode.create(
                                fraOgMed = 1.juli(2020),
                                tilOgMed = 31.juli(2020),
                            ),
                            fradragstype = Fradragstype.Arbeidsinntekt,
                            månedsbeløp = 5000.0,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                    satsbeløp = Sats.HØY.månedsbeløp(1.juli(2020)),
                    fribeløpForEps = 14810.333333333334,
                ),
            ),
        )
    }

    @Test
    fun `varig inntektsreduksjon`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 10000.0,
                    periode = Periode.create(1.februar(2020), 31.mai(2020)),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 5000.0,
                    periode = Periode.create(1.juni(2020), 31.desember(2020)),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
            utgangspunkt = null,
        )

        beregning.getSumYtelse() shouldBe 185116
        beregning.getSumFradrag() shouldBe 65000
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (20637 to 0.0)),
                (februar(2020) to (20637 to 0.0)),
                (mars(2020) to (10637 to 10000.0)),
                (april(2020) to (10637 to 10000.0)),
                (mai(2020) to (10946 to 10000.0)),
                (juni(2020) to (15946 to 5000.0)),
                (juli(2020) to (15946 to 5000.0)),
                (august(2020) to (15946 to 5000.0)),
                (september(2020) to (15946 to 5000.0)),
                (oktober(2020) to (15946 to 5000.0)),
                (november(2020) to (15946 to 5000.0)),
                (desember(2020) to (15946 to 5000.0)),
            ),
        )
        beregning.merknader().let {
            it.filterIsInstance<Merknad.EndringGrunnbeløp>() shouldHaveSize 1
            it.filterIsInstance<Merknad.ØktYtelse>() shouldHaveSize 1
            it.filterIsInstance<Merknad.RedusertYtelse>() shouldHaveSize 1
            it.filterIsInstance<Merknad.EndringUnderTiProsent>() shouldHaveSize 0
        }
        beregning.merknader() shouldBe listOf(
            Merknad.NyYtelse(
                benyttetBeregning = Merknad.MerknadMånedsberegning(
                    periode = Periode.create(
                        fraOgMed = 1.januar(2020),
                        tilOgMed = 31.januar(2020),
                    ),
                    sats = Sats.HØY,
                    grunnbeløp = 99858,
                    beløp = 20637,
                    fradrag = listOf(
                        Merknad.MerknadFradrag(
                            periode = Periode.create(
                                fraOgMed = 1.januar(2020),
                                tilOgMed = 31.januar(2020),
                            ),
                            fradragstype = Fradragstype.ForventetInntekt,
                            månedsbeløp = 0.0,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                    satsbeløp = Sats.HØY.månedsbeløp(1.januar(2020)),
                    fribeløpForEps = 0.0,
                ),
            ),
            Merknad.RedusertYtelse(
                benyttetBeregning = Merknad.MerknadMånedsberegning(
                    periode = Periode.create(
                        fraOgMed = 1.februar(2020),
                        tilOgMed = 29.februar(2020),
                    ),
                    sats = Sats.HØY,
                    grunnbeløp = 99858,
                    beløp = 20637,
                    fradrag = listOf(
                        Merknad.MerknadFradrag(
                            periode = Periode.create(
                                fraOgMed = 1.februar(2020),
                                tilOgMed = 29.februar(2020),
                            ),
                            fradragstype = Fradragstype.ForventetInntekt,
                            månedsbeløp = 0.0,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                    satsbeløp = Sats.HØY.månedsbeløp(1.februar(2020)),
                    fribeløpForEps = 0.0,
                ),
                forkastetBeregning = Merknad.MerknadMånedsberegning(
                    periode = Periode.create(
                        fraOgMed = 1.februar(2020),
                        tilOgMed = 29.februar(2020),
                    ),
                    sats = Sats.HØY,
                    grunnbeløp = 99858,
                    beløp = 10637,
                    fradrag = listOf(
                        Merknad.MerknadFradrag(
                            periode = Periode.create(
                                fraOgMed = 1.februar(2020),
                                tilOgMed = 29.februar(2020),
                            ),
                            fradragstype = Fradragstype.Arbeidsinntekt,
                            månedsbeløp = 10000.0,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                    satsbeløp = Sats.HØY.månedsbeløp(1.februar(2020)),
                    fribeløpForEps = 0.0,
                ),
            ),
            Merknad.EndringGrunnbeløp(
                gammeltGrunnbeløp = Merknad.EndringGrunnbeløp.Detalj(
                    dato = 1.mai(2019),
                    grunnbeløp = 99858,
                ),
                nyttGrunnbeløp = Merknad.EndringGrunnbeløp.Detalj(
                    dato = 1.mai(2020),
                    grunnbeløp = 101351,
                ),
            ),
            Merknad.ØktYtelse(
                benyttetBeregning = Merknad.MerknadMånedsberegning(
                    periode = Periode.create(
                        fraOgMed = 1.juni(2020),
                        tilOgMed = 30.juni(2020),
                    ),
                    sats = Sats.HØY,
                    grunnbeløp = 101351,
                    beløp = 15946,
                    fradrag = listOf(
                        Merknad.MerknadFradrag(
                            periode = Periode.create(
                                fraOgMed = 1.juni(2020),
                                tilOgMed = 30.juni(2020),
                            ),
                            fradragstype = Fradragstype.Arbeidsinntekt,
                            månedsbeløp = 5000.0,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                    satsbeløp = Sats.HØY.månedsbeløp(1.juni(2020)),
                    fribeløpForEps = 0.0,
                ),
                forkastetBeregning = Merknad.MerknadMånedsberegning(
                    periode = Periode.create(
                        fraOgMed = 1.juni(2020),
                        tilOgMed = 30.juni(2020),
                    ),
                    sats = Sats.HØY,
                    grunnbeløp = 101351,
                    beløp = 10946,
                    fradrag = listOf(
                        Merknad.MerknadFradrag(
                            periode = Periode.create(
                                fraOgMed = 1.juni(2020),
                                tilOgMed = 30.juni(2020),
                            ),
                            fradragstype = Fradragstype.Arbeidsinntekt,
                            månedsbeløp = 10000.0,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    ),
                    satsbeløp = Sats.HØY.månedsbeløp(1.juni(2020)),
                    fribeløpForEps = 0.0,
                ),
            ),
        )
    }

    @Test
    fun `klynger med inntekt`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val fradrag = listOf(
            IkkePeriodisertFradrag(
                type = Fradragstype.ForventetInntekt,
                månedsbeløp = 0.0,
                periode = periode,
                tilhører = FradragTilhører.BRUKER,
            ),
            IkkePeriodisertFradrag(
                type = Fradragstype.Arbeidsinntekt,
                månedsbeløp = 2000.0,
                periode = Periode.create(1.januar(2020), 29.februar(2020)),
                tilhører = FradragTilhører.BRUKER,
            ),
            IkkePeriodisertFradrag(
                type = Fradragstype.Arbeidsinntekt,
                månedsbeløp = 5000.0,
                periode = Periode.create(1.april(2020), 31.juli(2020)),
                tilhører = FradragTilhører.BRUKER,
            ),
            IkkePeriodisertFradrag(
                type = Fradragstype.Arbeidsinntekt,
                månedsbeløp = 3000.0,
                periode = Periode.create(1.oktober(2020), 30.november(2020)),
                tilhører = FradragTilhører.BRUKER,
            ),
        )
        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = fradrag,
            fradragStrategy = FradragStrategy.Enslig,
            utgangspunkt = null,
        )

        beregning.getSumYtelse() shouldBe 228116
        beregning.getSumFradrag() shouldBe 22000
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (18637 to 2000.0)),
                (februar(2020) to (18637 to 2000.0)),
                (mars(2020) to (20637 to 0.0)),
                (april(2020) to (20637 to 0.0)),
                (mai(2020) to (15946 to 5000.0)),
                (juni(2020) to (15946 to 5000.0)),
                (juli(2020) to (15946 to 5000.0)),
                (august(2020) to (20946 to 0.0)),
                (september(2020) to (20946 to 0.0)),
                (oktober(2020) to (20946 to 0.0)),
                (november(2020) to (17946 to 3000.0)),
                (desember(2020) to (20946 to 0.0)),
            ),
        )
        beregning.getFradrag() shouldBe fradrag
        beregning.getMånedsberegninger().find { it.periode == februar(2020) }!!.getFradrag() shouldBe
            FradragFactory.periodiser(
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 2000.0,
                    periode = februar(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
            )
        beregning.getMånedsberegninger().find { it.periode == juli(2020) }!!.getFradrag() shouldBe
            FradragFactory.periodiser(
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 5000.0,
                    periode = juli(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
            )
        beregning.getMånedsberegninger().find { it.periode == desember(2020) }!!.getFradrag() shouldBe
            FradragFactory.periodiser(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = desember(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
            )
        beregning.merknader().let {
            it.filterIsInstance<Merknad.EndringGrunnbeløp>() shouldHaveSize 1
            it.filterIsInstance<Merknad.ØktYtelse>() shouldHaveSize 3
            it.filterIsInstance<Merknad.RedusertYtelse>() shouldHaveSize 2
            it.filterIsInstance<Merknad.EndringUnderTiProsent>() shouldHaveSize 0
        }
    }

    @Test
    fun `håndterer at utgangspunkt og ny beregning gir 0 i beløp`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningMedVirkningstidspunkt(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 45_000.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
            utgangspunkt = MånedsberegningFactory.ny(
                periode = periode.månedenFør(),
                sats = Sats.HØY,
                fradrag = listOf(
                    FradragFactory.ny(
                        type = Fradragstype.ForventetInntekt,
                        månedsbeløp = 45_000.0,
                        periode = desember(2019),
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
                fribeløpForEps = 0.0,
            ),
        )

        beregning.getSumYtelse() shouldBe 0
        beregning.getSumFradrag() shouldBe 250116.0.plusOrMinus(0.5)
        beregning.getMånedsberegninger().assertMåneder(
            expected = mapOf(
                (januar(2020) to (0 to 20637.32)),
                (februar(2020) to (0 to 20637.32)),
                (mars(2020) to (0 to 20637.32)),
                (april(2020) to (0 to 20637.32)),
                (mai(2020) to (0 to 20945.873333333333)),
                (juni(2020) to (0 to 20945.873333333333)),
                (juli(2020) to (0 to 20945.873333333333)),
                (august(2020) to (0 to 20945.873333333333)),
                (september(2020) to (0 to 20945.873333333333)),
                (oktober(2020) to (0 to 20945.873333333333)),
                (november(2020) to (0 to 20945.873333333333)),
                (desember(2020) to (0 to 20945.873333333333)),
            ),
        )
        beregning.merknader().let {
            it.filterIsInstance<Merknad.EndringGrunnbeløp>() shouldHaveSize 1
            it.filterIsInstance<Merknad.ØktYtelse>() shouldHaveSize 0
            it.filterIsInstance<Merknad.RedusertYtelse>() shouldHaveSize 0
            it.filterIsInstance<Merknad.EndringUnderTiProsent>() shouldHaveSize 0
        }
    }

    @Test
    fun `endring fra`() {
        1100 endringFra 1000 shouldBe Endring.ØKNING_OVER_10_PROSENT
        900 endringFra 1000 shouldBe Endring.REDUKSJON_OVER_10_PROSENT
        20_000 endringFra 10_000 shouldBe Endring.ØKNING_OVER_10_PROSENT
        10_000 endringFra 20_000 shouldBe Endring.REDUKSJON_OVER_10_PROSENT
        0 endringFra 100 shouldBe Endring.REDUKSJON_OVER_10_PROSENT
        100 endringFra 0 shouldBe Endring.ØKNING_OVER_10_PROSENT
        100_000 endringFra 1000 shouldBe Endring.ØKNING_OVER_10_PROSENT
        0 endringFra 0 shouldBe Endring.ENDRING_UNDER_10_PROSENT
        1050 endringFra 1000 shouldBe Endring.ENDRING_UNDER_10_PROSENT
        1000 endringFra 1050 shouldBe Endring.ENDRING_UNDER_10_PROSENT
    }

    private fun List<Månedsberegning>.assertMåneder(expected: Map<Periode, Pair<Int, Double>>) {
        map {
            Triple(
                first = it.periode,
                second = it.getSumYtelse(),
                third = it.getSumFradrag(),
            )
        }.forEach { (periode, ytelse, fradrag) ->
            expected[periode] shouldBe Pair(ytelse, fradrag)
        }.also {
            this.map { it.periode } shouldBe expected.keys
        }
    }
}
