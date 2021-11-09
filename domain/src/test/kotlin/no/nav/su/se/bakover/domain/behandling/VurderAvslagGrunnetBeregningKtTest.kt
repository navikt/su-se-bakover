package no.nav.su.se.bakover.domain.behandling

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.august
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.domain.behandling.VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.IngenMerknaderForAvslag
import no.nav.su.se.bakover.domain.beregning.Merknad
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.beregning.finnMerknaderForPeriode
import no.nav.su.se.bakover.domain.beregning.finnFørsteMånedMedMerknadForAvslag
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.test.getOrFail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class VurderAvslagGrunnetBeregningKtTest {

    @Test
    fun `beregning som ikke eksisterer kan ikke gi avslag`() {
        vurderAvslagGrunnetBeregning(null) shouldBe AvslagGrunnetBeregning.Nei
    }

    @Test
    fun `beregning med sum ytelse nøyaktig lik null skal gi avslag`() {
        val periode = januar(2021)
        val fradrag = lagFradrag(Sats.HØY.månedsbeløp(periode.fraOgMed), periode)
        val beregning = lagBeregningMedFradrag(fradrag)

        beregning.getSumYtelse() shouldBe 0
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            grunn = AvslagGrunnetBeregning.Grunn.FOR_HØY_INNTEKT,
        )
        beregning.finnFørsteMånedMedMerknadForAvslag()
            .getOrFail("")
            .second shouldBe Merknad.Beregning.BeløpErNull
        beregning.finnMerknaderForPeriode(januar(2021)) shouldBe listOf(Merknad.Beregning.BeløpErNull)
    }

    @Test
    fun `beregning med potensielt negativ sum ytelse skal gi avslag`() {
        val januar = lagFradrag(1000000.0, januar(2021))
        val desember = lagFradrag(100000.0, desember(2021))

        val beregning = lagBeregningMedFradrag(januar, desember)

        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            grunn = AvslagGrunnetBeregning.Grunn.FOR_HØY_INNTEKT,
        )
        beregning.finnFørsteMånedMedMerknadForAvslag()
            .getOrFail("")
            .second shouldBe Merknad.Beregning.BeløpErNull
        beregning.finnMerknaderForPeriode(januar(2021)) shouldBe listOf(Merknad.Beregning.BeløpErNull)
        beregning.finnMerknaderForPeriode(desember(2021)) shouldBe listOf(Merknad.Beregning.BeløpErNull)
    }

    @Test
    fun `beregning med alle måneder under minstebeløp skal gi avslag`() {
        val januar = lagFradrag(20750.0, Periode.create(1.januar(2021), 30.april(2021)))
        val desember = lagFradrag(21800.0, Periode.create(1.mai(2021), 31.desember(2021)))

        val beregning = lagBeregningMedFradrag(januar, desember)

        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            grunn = AvslagGrunnetBeregning.Grunn.SU_UNDER_MINSTEGRENSE,
        )
        beregning.finnFørsteMånedMedMerknadForAvslag()
            .getOrFail("")
            .second shouldBe Merknad.Beregning.BeløpMellomNullOgToProsentAvHøySats
        beregning.finnMerknaderForPeriode(januar(2021)) shouldBe listOf(Merknad.Beregning.BeløpMellomNullOgToProsentAvHøySats)
        beregning.finnMerknaderForPeriode(august(2021)) shouldBe listOf(Merknad.Beregning.BeløpMellomNullOgToProsentAvHøySats)
        beregning.finnMerknaderForPeriode(desember(2021)) shouldBe listOf(Merknad.Beregning.BeløpMellomNullOgToProsentAvHøySats)
    }

    @Test
    fun `beregning med en måned for EPS under minstebeløp skal gi avslag`() {
        val marsEPS = lagFradrag(285_000.0, mars(2021), tilhører = FradragTilhører.EPS)

        val beregning = lagBeregningMedFradrag(marsEPS, fradragStrategy = FradragStrategy.EpsOver67År)

        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            grunn = AvslagGrunnetBeregning.Grunn.FOR_HØY_INNTEKT,
        )
        beregning.finnFørsteMånedMedMerknadForAvslag()
            .getOrFail("")
            .second shouldBe Merknad.Beregning.BeløpErNull
        beregning.finnMerknaderForPeriode(mars(2021)) shouldBe listOf(Merknad.Beregning.BeløpErNull)
    }

    @Test
    fun `beregning med alle måneder over minstebeløp skal ikke gi avslag`() {
        val januar = lagFradrag(18500.0, januar(2021))
        val desember = lagFradrag(16400.0, desember(2021))

        val beregning = lagBeregningMedFradrag(januar, desember)

        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Nei

        beregning.finnFørsteMånedMedMerknadForAvslag() shouldBe IngenMerknaderForAvslag.left()
    }

    @Test
    fun `avslag dersom det eksisterer 1 måned med beløp lik 0`() {
        val januar = lagFradrag(30000.0, januar(2021))
        val desember = lagFradrag(2500.0, desember(2021))

        val beregning = lagBeregningMedFradrag(januar, desember)

        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            grunn = AvslagGrunnetBeregning.Grunn.FOR_HØY_INNTEKT,
        )
        beregning.finnFørsteMånedMedMerknadForAvslag()
            .getOrFail("")
            .second shouldBe Merknad.Beregning.BeløpErNull
        beregning.finnMerknaderForPeriode(januar(2021)) shouldBe listOf(Merknad.Beregning.BeløpErNull)
    }

    @Test
    fun `ikke avslag dersom det eksisterer 1 måned med beløp lik 0 pågrunn av sosialstønad`() {
        val januar = lagFradrag(30000.0, januar(2021), Fradragstype.Sosialstønad)
        val desember = lagFradrag(2500.0, desember(2021))

        val beregning = lagBeregningMedFradrag(januar, desember)

        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Nei

        beregning.finnFørsteMånedMedMerknadForAvslag() shouldBe IngenMerknaderForAvslag.left()
    }

    @Test
    fun `avslag dersom det eksisterer 1 måned med beløp under minstegrense`() {
        val januar = lagFradrag(2500.0, januar(2021))
        val juni = lagFradrag(21900.0, juni(2021))
        val desember = lagFradrag(2500.0, desember(2021))

        val beregning = lagBeregningMedFradrag(januar, juni, desember)
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            grunn = AvslagGrunnetBeregning.Grunn.SU_UNDER_MINSTEGRENSE,
        )
        beregning.finnFørsteMånedMedMerknadForAvslag()
            .getOrFail("")
            .second shouldBe Merknad.Beregning.BeløpMellomNullOgToProsentAvHøySats
        beregning.finnMerknaderForPeriode(juni(2021)) shouldBe listOf(Merknad.Beregning.BeløpMellomNullOgToProsentAvHøySats)
    }

    @Test
    fun `ikke avslag dersom det eksisterer 1 måned med beløp under minstegrensen pågrunn av sosialstønad`() {
        val januar = lagFradrag(2500.0, januar(2021))
        val juni = lagFradrag(21900.0, juni(2021), Fradragstype.Sosialstønad)
        val desember = lagFradrag(2500.0, desember(2021))

        val beregning = lagBeregningMedFradrag(januar, juni, desember, fradragStrategy = FradragStrategy.EpsUnder67År)
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Nei
        beregning.finnFørsteMånedMedMerknadForAvslag() shouldBe IngenMerknaderForAvslag.left()
    }

    @Test
    fun `ikke avslag dersom beløp er under minstegresen på grunn av EPS sosialstønad`() {
        val januar = lagFradrag(2500.0, januar(2021))
        val juni = lagFradrag(21900.0, juni(2021), Fradragstype.Sosialstønad, FradragTilhører.EPS)
        val desember = lagFradrag(2500.0, desember(2021))

        val beregning = lagBeregningMedFradrag(januar, juni, desember, fradragStrategy = FradragStrategy.EpsUnder67År)
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Nei
        beregning.finnFørsteMånedMedMerknadForAvslag() shouldBe IngenMerknaderForAvslag.left()
        beregning.finnMerknaderForPeriode(juni(2021)) shouldBe listOf(Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats)
    }

    @Test
    fun `ikke avslag dersom beløp er under minstegresen pågrunn av EPS og brukers sosialstønad`() {
        val januar = lagFradrag(2500.0, januar(2021))
        val juni = lagFradrag(10950.0, juni(2021), Fradragstype.Sosialstønad, FradragTilhører.BRUKER)
        val juniEPS = lagFradrag(10950.0, juni(2021), Fradragstype.Sosialstønad, FradragTilhører.EPS)
        val desember = lagFradrag(2500.0, desember(2021))

        val beregning =
            lagBeregningMedFradrag(januar, juni, juniEPS, desember, fradragStrategy = FradragStrategy.EpsUnder67År)

        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Nei

        beregning.finnMerknaderForPeriode(juni(2021)) shouldBe listOf(Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats)
    }

    @Test
    fun `avslagsgrunner for merknad`() {
        Merknad.Beregning.BeløpErNull.tilAvslagsgrunn() shouldBe AvslagGrunnetBeregning.Grunn.FOR_HØY_INNTEKT
        Merknad.Beregning.BeløpMellomNullOgToProsentAvHøySats.tilAvslagsgrunn() shouldBe AvslagGrunnetBeregning.Grunn.SU_UNDER_MINSTEGRENSE

        listOf(
            Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats,
            Merknad.Beregning.EndringGrunnbeløp(
                gammeltGrunnbeløp = Merknad.Beregning.EndringGrunnbeløp.Detalj.forDato(1.mai(2020)),
                nyttGrunnbeløp = Merknad.Beregning.EndringGrunnbeløp.Detalj.forDato(1.mai(2021)),
            ),
        ).forEach {
            assertThrows<IllegalStateException> {
                it.tilAvslagsgrunn()
            }
        }
    }

    private fun lagBeregningMedFradrag(
        vararg fradrag: Fradrag,
        fradragStrategy: FradragStrategy = FradragStrategy.Enslig,
    ): Beregning {
        val periode = Periode.create(fradrag.minOf { it.periode.fraOgMed }, fradrag.maxOf { it.periode.tilOgMed })
        return BeregningFactory.ny(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                *fradrag,
                FradragFactory.ny(
                    Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = fradragStrategy,
            begrunnelse = null,
        )
    }

    private fun lagFradrag(
        beløp: Double,
        periode: Periode,
        fradragstype: Fradragstype? = null,
        tilhører: FradragTilhører? = null,
    ) = FradragFactory.ny(
        type = fradragstype ?: Fradragstype.Kapitalinntekt,
        månedsbeløp = beløp,
        periode = periode,
        utenlandskInntekt = null,
        tilhører = tilhører ?: FradragTilhører.BRUKER,
    )
}
