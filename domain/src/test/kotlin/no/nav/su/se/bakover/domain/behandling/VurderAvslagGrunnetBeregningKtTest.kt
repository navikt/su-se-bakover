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
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.behandling.VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.beregning.IngenMerknaderForAvslag
import no.nav.su.se.bakover.domain.beregning.Merknad
import no.nav.su.se.bakover.domain.beregning.beregning.finnMerknaderForPeriode
import no.nav.su.se.bakover.domain.beregning.finnFørsteMånedMedMerknadForAvslag
import no.nav.su.se.bakover.domain.beregning.finnMånederMedMerknad
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
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
        val fradrag =
            lagFradrag(satsFactoryTestPåDato().høyUføre(periode).satsForMånedAsDouble, periode)
        val beregning = lagBeregningMedFradrag(fradrag, strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE))

        beregning.getSumYtelse() shouldBe 0
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            grunn = AvslagGrunnetBeregning.Grunn.FOR_HØY_INNTEKT,
        )
        beregning.finnFørsteMånedMedMerknadForAvslag()
            .getOrFail("")
            .second shouldBe Merknad.Beregning.Avslag.BeløpErNull
        beregning.finnMerknaderForPeriode(januar(2021)) shouldBe listOf(Merknad.Beregning.Avslag.BeløpErNull)
    }

    @Test
    fun `beregning med potensielt negativ sum ytelse skal gi avslag`() {
        val januar = lagFradrag(1000000.0, januar(2021))
        val desember = lagFradrag(100000.0, desember(2021))

        val beregning =
            lagBeregningMedFradrag(januar, desember, strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE))

        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            grunn = AvslagGrunnetBeregning.Grunn.FOR_HØY_INNTEKT,
        )
        beregning.finnFørsteMånedMedMerknadForAvslag()
            .getOrFail("")
            .second shouldBe Merknad.Beregning.Avslag.BeløpErNull
        beregning.finnMerknaderForPeriode(januar(2021)) shouldBe listOf(Merknad.Beregning.Avslag.BeløpErNull)
        beregning.finnMerknaderForPeriode(desember(2021)) shouldBe listOf(Merknad.Beregning.Avslag.BeløpErNull)
    }

    @Test
    fun `beregning med alle måneder under minstebeløp skal gi avslag`() {
        val januar = lagFradrag(20750.0, Periode.create(1.januar(2021), 30.april(2021)))
        val desember = lagFradrag(20800.0, Periode.create(1.mai(2021), 31.desember(2021)))

        val beregning =
            lagBeregningMedFradrag(januar, desember, strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE))

        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            grunn = AvslagGrunnetBeregning.Grunn.SU_UNDER_MINSTEGRENSE,
        )
        beregning.finnFørsteMånedMedMerknadForAvslag()
            .getOrFail("")
            .second shouldBe Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats
        beregning.finnMerknaderForPeriode(januar(2021)) shouldBe listOf(Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats)
        beregning.finnMerknaderForPeriode(august(2021)) shouldBe listOf(Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats)
        beregning.finnMerknaderForPeriode(desember(2021)) shouldBe listOf(Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats)
    }

    @Test
    fun `beregning med en måned for EPS under minstebeløp skal gi avslag`() {
        val marsEPS = lagFradrag(285_000.0, mars(2021), tilhører = FradragTilhører.EPS)

        val beregning =
            lagBeregningMedFradrag(marsEPS, strategy = BeregningStrategy.Eps67EllerEldre(satsFactoryTestPåDato(), Sakstype.UFØRE))

        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            grunn = AvslagGrunnetBeregning.Grunn.FOR_HØY_INNTEKT,
        )
        beregning.finnFørsteMånedMedMerknadForAvslag()
            .getOrFail("")
            .second shouldBe Merknad.Beregning.Avslag.BeløpErNull
        beregning.finnMerknaderForPeriode(mars(2021)) shouldBe listOf(Merknad.Beregning.Avslag.BeløpErNull)
    }

    @Test
    fun `beregning med alle måneder over minstebeløp skal ikke gi avslag`() {
        val januar = lagFradrag(18500.0, januar(2021))
        val desember = lagFradrag(16400.0, desember(2021))

        val beregning =
            lagBeregningMedFradrag(januar, desember, strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE))

        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Nei

        beregning.finnFørsteMånedMedMerknadForAvslag() shouldBe IngenMerknaderForAvslag.left()
    }

    @Test
    fun `avslag dersom det eksisterer 1 måned med beløp lik 0`() {
        val januar = lagFradrag(30000.0, januar(2021))
        val desember = lagFradrag(2500.0, desember(2021))

        val beregning =
            lagBeregningMedFradrag(januar, desember, strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE))

        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            grunn = AvslagGrunnetBeregning.Grunn.FOR_HØY_INNTEKT,
        )
        beregning.finnFørsteMånedMedMerknadForAvslag()
            .getOrFail("")
            .second shouldBe Merknad.Beregning.Avslag.BeløpErNull
        beregning.finnMerknaderForPeriode(januar(2021)) shouldBe listOf(Merknad.Beregning.Avslag.BeløpErNull)
    }

    @Test
    fun `ikke avslag dersom det eksisterer 1 måned med beløp lik 0 pågrunn av sosialstønad`() {
        val januar = lagFradrag(30000.0, januar(2021), Fradragstype.Sosialstønad)
        val desember = lagFradrag(2500.0, desember(2021))

        val beregning =
            lagBeregningMedFradrag(januar, desember, strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE))

        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Nei

        beregning.finnFørsteMånedMedMerknadForAvslag() shouldBe IngenMerknaderForAvslag.left()
    }

    @Test
    fun `avslag dersom det eksisterer 1 måned med beløp under minstegrense`() {
        val januar = lagFradrag(2500.0, januar(2021))
        val juni = lagFradrag(20750.0, juni(2021))
        val desember = lagFradrag(2500.0, desember(2021))

        val beregning = lagBeregningMedFradrag(
            januar,
            juni,
            desember,
            strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
        )

        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Ja(
            grunn = AvslagGrunnetBeregning.Grunn.SU_UNDER_MINSTEGRENSE,
        )
        beregning.finnFørsteMånedMedMerknadForAvslag()
            .getOrFail("")
            .second shouldBe Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats
        beregning.finnMerknaderForPeriode(juni(2021)) shouldBe listOf(Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats)
    }

    @Test
    fun `ikke avslag dersom det eksisterer 1 måned med beløp under minstegrensen pågrunn av sosialstønad`() {
        val januar = lagFradrag(2500.0, januar(2021))
        val juni = lagFradrag(21900.0, juni(2021), Fradragstype.Sosialstønad)
        val desember = lagFradrag(2500.0, desember(2021))

        val beregning =
            lagBeregningMedFradrag(
                januar,
                juni,
                desember,
                strategy = BeregningStrategy.EpsUnder67År(satsFactoryTestPåDato(), Sakstype.UFØRE),
            )
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Nei
        beregning.finnFørsteMånedMedMerknadForAvslag() shouldBe IngenMerknaderForAvslag.left()
    }

    @Test
    fun `ikke avslag dersom beløp er under minstegrensen på grunn av EPS sosialstønad`() {
        val januar = lagFradrag(2500.0, januar(2021))
        val juni = lagFradrag(21900.0, juni(2021), Fradragstype.Sosialstønad, FradragTilhører.EPS)
        val desember = lagFradrag(2500.0, desember(2021))

        val beregning =
            lagBeregningMedFradrag(
                januar,
                juni,
                desember,
                strategy = BeregningStrategy.EpsUnder67År(satsFactoryTestPåDato(), Sakstype.UFØRE),
            )
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Nei
        beregning.finnFørsteMånedMedMerknadForAvslag() shouldBe IngenMerknaderForAvslag.left()
        beregning.finnMerknaderForPeriode(juni(2021)) shouldBe listOf(Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats)
    }

    @Test
    fun `ikke avslag dersom beløp er under minstegrensen på grunn av avkorting`() {
        val januar = lagFradrag(5000.0, januar(2021))
        val juni = lagFradrag(21900.0, juni(2021), Fradragstype.AvkortingUtenlandsopphold)
        val desember = lagFradrag(5000.0, desember(2021))

        val beregning =
            lagBeregningMedFradrag(
                januar,
                juni,
                desember,
                strategy = BeregningStrategy.EpsUnder67År(satsFactoryTestPåDato(), Sakstype.UFØRE),
            )
        beregning.finnMånederMedMerknad()
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Nei
        beregning.finnFørsteMånedMedMerknadForAvslag() shouldBe IngenMerknaderForAvslag.left()
        beregning.finnMerknaderForPeriode(juni(2021)) shouldBe listOf(Merknad.Beregning.AvkortingFørerTilBeløpLavereEnnToProsentAvHøySats)
    }

    @Test
    fun `ikke avslag dersom beløp er under minstegrensen på grunn av avkorting pluss sosialstønad`() {
        val januar = lagFradrag(5000.0, januar(2021))
        val juniAvkorting = lagFradrag(10900.0, juni(2021), Fradragstype.AvkortingUtenlandsopphold)
        val juniSosialstønad = lagFradrag(11000.0, juni(2021), Fradragstype.Sosialstønad)
        val desember = lagFradrag(5000.0, desember(2021))

        val beregning = lagBeregningMedFradrag(
            januar,
            juniAvkorting,
            juniSosialstønad,
            desember,
            strategy = BeregningStrategy.EpsUnder67År(satsFactoryTestPåDato(), Sakstype.UFØRE),
        )

        beregning.finnMånederMedMerknad()
        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Nei
        beregning.finnFørsteMånedMedMerknadForAvslag() shouldBe IngenMerknaderForAvslag.left()
        beregning.finnMerknaderForPeriode(juni(2021)) shouldBe listOf(Merknad.Beregning.AvkortingFørerTilBeløpLavereEnnToProsentAvHøySats)
    }

    @Test
    fun `ikke avslag dersom beløp er under minstegresen pågrunn av EPS og brukers sosialstønad`() {
        val januar = lagFradrag(2500.0, januar(2021))
        val juni = lagFradrag(10950.0, juni(2021), Fradragstype.Sosialstønad, FradragTilhører.BRUKER)
        val juniEPS = lagFradrag(10950.0, juni(2021), Fradragstype.Sosialstønad, FradragTilhører.EPS)
        val desember = lagFradrag(2500.0, desember(2021))

        val beregning =
            lagBeregningMedFradrag(
                januar,
                juni,
                juniEPS,
                desember,
                strategy = BeregningStrategy.EpsUnder67År(satsFactoryTestPåDato(), Sakstype.UFØRE),
            )

        vurderAvslagGrunnetBeregning(beregning) shouldBe AvslagGrunnetBeregning.Nei

        beregning.finnMerknaderForPeriode(juni(2021)) shouldBe listOf(Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats)
    }

    @Test
    fun `avslagsgrunner for merknad`() {
        Merknad.Beregning.Avslag.BeløpErNull.tilAvslagsgrunn() shouldBe AvslagGrunnetBeregning.Grunn.FOR_HØY_INNTEKT
        Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats.tilAvslagsgrunn() shouldBe AvslagGrunnetBeregning.Grunn.SU_UNDER_MINSTEGRENSE

        listOf(
            Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats,
        ).forEach {
            assertThrows<IllegalStateException> {
                it.tilAvslagsgrunn()
            }
        }
    }

    private fun lagBeregningMedFradrag(
        vararg fradrag: Fradrag,
        strategy: BeregningStrategy,
    ): Beregning {
        val periode = Periode.create(fradrag.minOf { it.periode.fraOgMed }, fradrag.maxOf { it.periode.tilOgMed })
        return BeregningFactory(clock = fixedClock).ny(
            fradrag = listOf(
                *fradrag,
                FradragFactory.nyFradragsperiode(
                    Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            begrunnelse = null,
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = periode,
                    strategy = strategy,
                ),
            ),
        )
    }

    private fun lagFradrag(
        beløp: Double,
        periode: Periode,
        fradragstype: Fradragstype? = null,
        tilhører: FradragTilhører? = null,
    ) = FradragFactory.nyFradragsperiode(
        fradragstype = fradragstype ?: Fradragstype.Kapitalinntekt,
        månedsbeløp = beløp,
        periode = periode,
        utenlandskInntekt = null,
        tilhører = tilhører ?: FradragTilhører.BRUKER,
    )
}
