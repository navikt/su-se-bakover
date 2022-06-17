package no.nav.su.se.bakover.domain.beregning.beregning

import arrow.core.left
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.april
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.common.periode.tilMåned
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.beregning.IngenMerknaderForAvslag
import no.nav.su.se.bakover.domain.beregning.Merknad
import no.nav.su.se.bakover.domain.beregning.finnFørsteMånedMedMerknadForAvslag
import no.nav.su.se.bakover.domain.beregning.finnMånederMedMerknad
import no.nav.su.se.bakover.domain.beregning.finnMånederMedMerknadForAvslag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragForMåned
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragForPeriode
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.harAlleMånederMerknadForAvslag
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class BeregningMedFradragBeregnetMånedsvisTest {

    @Test
    fun `summer for enkel beregning`() {
        val periode = år(2020)
        val beregning = BeregningFactory(clock = fixedClock).ny(
            fradrag = listOf(
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = periode,
                    strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
                ),
            ),
        )
        beregning.getSumYtelse() shouldBe 250116
        beregning.getSumFradrag() shouldBe 0
    }

    @Test
    fun `fradrag for alle perioder`() {
        val periode = år(2020)
        val beregning = BeregningFactory(clock = fixedClock).ny(
            fradrag = listOf(
                FradragForPeriode(
                    fradragstype = Fradragstype.ForventetInntekt, månedsbeløp = 1000.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = periode,
                    strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
                ),
            ),
        )

        beregning.getSumYtelse() shouldBe 238116
        beregning.getSumFradrag() shouldBe 12000
    }

    @Test
    fun `fradrag som gjør at alle perioder får beløp under minstenivå`() {
        val periode = år(2020)
        val beregning = BeregningFactory(clock = fixedClock).ny(
            fradrag = listOf(
                FradragForPeriode(
                    fradragstype = Fradragstype.ForventetInntekt, månedsbeløp = 20500.0,
                    periode = Periode.create(1.januar(2020), 30.april(2020)),
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragForPeriode(
                    fradragstype = Fradragstype.ForventetInntekt, månedsbeløp = 20800.0,
                    periode = Periode.create(1.mai(2020), 31.desember(2020)),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = periode,
                    strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
                ),
            ),
        )

        beregning.finnMånederMedMerknad().getOrFail() shouldContainAll listOf(
            beregning.getMånedsberegninger()[0] to listOf(Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats),
            beregning.getMånedsberegninger()[4] to listOf(Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats),
            beregning.getMånedsberegninger()[11] to listOf(Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats),
        )
    }

    @Test
    fun `fradrag som gjør at alle perioder får beløp lik 0`() {
        val periode = år(2020)
        val beregning = BeregningFactory(clock = fixedClock).ny(
            fradrag = listOf(
                FradragForPeriode(
                    fradragstype = Fradragstype.ForventetInntekt, månedsbeløp = 150000.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = periode,
                    strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
                ),
            ),
        )

        beregning.harAlleMånederMerknadForAvslag() shouldBe true
        beregning.finnFørsteMånedMedMerknadForAvslag()
            .getOrFail() shouldBe (beregning.getMånedsberegninger()[0] to Merknad.Beregning.Avslag.BeløpErNull)
        beregning.finnMånederMedMerknadForAvslag().getOrFail() shouldContainAll listOf(
            beregning.getMånedsberegninger()[0] to Merknad.Beregning.Avslag.BeløpErNull,
            beregning.getMånedsberegninger()[11] to Merknad.Beregning.Avslag.BeløpErNull,
        )
    }

    @Test
    fun `fradrag for enkelte perioder`() {
        val periode = år(2020)
        val beregning = BeregningFactory(clock = fixedClock).ny(
            fradrag = listOf(
                FradragForPeriode(
                    fradragstype = Fradragstype.ForventetInntekt,
                    månedsbeløp = 500.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragForPeriode(
                    fradragstype = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 6000.0,
                    periode = juni(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = periode,
                    strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
                ),
            ),
        )

        beregning.getSumYtelse() shouldBe 238616
        beregning.getSumFradrag() shouldBe 11500
    }

    @Test
    fun `overlappende fradrag for samme periode`() {
        val periode = år(2020)
        val beregning = BeregningFactory(clock = fixedClock).ny(
            fradrag = listOf(
                FradragForPeriode(
                    fradragstype = Fradragstype.ForventetInntekt,
                    månedsbeløp = 500.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragForPeriode(
                    fradragstype = Fradragstype.Kontantstøtte,
                    månedsbeløp = 6000.0,
                    periode = januar(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = periode,
                    strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
                ),
            ),
        )

        beregning.getSumYtelse() shouldBe 238116
        beregning.getSumFradrag() shouldBe 12000
    }

    /**
     * Månedsbeløp: Jan-Apr = 20637,32 -> * 0.02 = 412,7464
     * Månedsbeløp: Mai-Des = 20945,87 -> * 0.02 = 418,9174
     * Beløpsgrense regnet måned for måned: (412,7464 * 4) + (418,9174 * 8) = 5002,3248
     *
     * Fradrag: 245117 -> 20426,42 pr mnd
     *
     * Beløp Jan-Apr: 20637,32 - 20426,42 = 210,9 -> rundes til 211 -- "får" 0,1 pr mnd = 0,4 totalt
     * Beløp Mai-Des: 20945,87 - 20426,42 = 519,46 -> rundes til 519 -- "mister" 0,46 pr mnd = 3,68 totalt
     * "Mister" totalt 3,28 kr pga avrunding av månedsbeløp
     * Totalt (tatt høyde for avrunding av månedsbeløp): 4996
     *
     * Dersom vi ikke hadde tatt høyde for avrunding ville vi hatt
     * Jan-Apr: 210,9 pr mnd
     * Mai-Des: 519,46 pr mnd
     * Totalt: 4999.28
     *
     * Beløp for Jan-Apr er under beløpsgrense for utbetaling, dette fører til et ekstra fradrag disse månedene
     * tilsvarende 4 * 211 = 844
     *
     * Total (uten avrunding) - "det vi mister pga avrunding" = 4152
     */
    @Test
    fun `sum under minstebeløp for utbetaling (2 prosent av høy sats)`() {
        val periode = år(2020)
        val beregning = BeregningFactory(clock = fixedClock).ny(
            fradrag = listOf(
                FradragForPeriode(
                    fradragstype = Fradragstype.ForventetInntekt,
                    månedsbeløp = 20426.42,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = periode,
                    strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
                ),
            ),
        )

        val (janAprUnderMinstenivå, janAprAndre) = beregning.getMånedsberegninger()
            .flatMap { it.getFradrag() }
            .filter { Periode.create(1.januar(2020), 30.april(2020)) inneholder it.periode }
            .partition { it.fradragstype == Fradragstype.UnderMinstenivå }

        janAprUnderMinstenivå shouldHaveSize 4
        janAprUnderMinstenivå.forEach {
            it shouldBe FradragForMåned(
                fradragstype = Fradragstype.UnderMinstenivå,
                månedsbeløp = 211.0,
                måned = it.periode.tilMåned(),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            )
        }
        janAprAndre shouldHaveSize 4
        janAprAndre.forEach {
            it shouldBe FradragForMåned(
                fradragstype = Fradragstype.ForventetInntekt,
                månedsbeløp = 20426.42,
                måned = it.periode.tilMåned(),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            )
        }

        val (maiDesUnderMinstenivå, maiDesAndre) = beregning.getMånedsberegninger()
            .flatMap { it.getFradrag() }
            .filter { Periode.create(1.mai(2020), 31.desember(2020)) inneholder it.periode }
            .partition { it.fradragstype == Fradragstype.UnderMinstenivå }

        maiDesUnderMinstenivå shouldHaveSize 0
        maiDesAndre shouldHaveSize 8
        maiDesAndre.forEach {
            it shouldBe FradragForMåned(
                fradragstype = Fradragstype.ForventetInntekt,
                månedsbeløp = 20426.42,
                måned = it.periode.tilMåned(),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            )
        }

        beregning.getSumYtelse() shouldBe 4152
        beregning.getSumFradrag() shouldBe 245961.0.plusOrMinus(0.5)
    }

    @Test
    fun `generer bare bare id og opprettet en gang for hvert objekt`() {
        val periode = Periode.create(1.januar(2020), 31.mars(2020))
        val beregning = BeregningFactory(clock = fixedClock).ny(
            fradrag = listOf(
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = periode,
                    strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
                ),
            ),
        )
        beregning.getId() shouldBe beregning.getId()
        beregning.getOpprettet() shouldBe beregning.getOpprettet()
    }

    @Test
    fun `fradrag inkluderes kun i den måneden de er aktuelle`() {
        val periode = Periode.create(1.januar(2020), 31.mars(2020))

        val totaltFradrag = 100000.0

        val beregning = BeregningFactory(clock = fixedClock).ny(
            fradrag = listOf(
                FradragForPeriode(
                    fradragstype = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragForPeriode(
                    fradragstype = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = totaltFradrag,
                    periode = januar(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = periode,
                    strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
                ),
            ),
        )

        beregning.getSumYtelse() shouldBe 41274
        beregning.getSumFradrag() shouldBe 20637.32
        val grouped = beregning.getMånedsberegninger().groupBy { it.periode }
        val januar = grouped[januar(2020)]!!.first()
        januar.getSumFradrag() shouldBe 20637.32
        januar.getSumYtelse() shouldBe 0
        val februar = grouped[februar(2020)]!!.first()
        februar.getSumFradrag() shouldBe 0
        februar.getSumYtelse() shouldBe 20637
    }

    @Test
    fun `To beregninger med samme totalsum for fradrag, men i forskjellige perioder gir ikke nødvendigvis samme resultat`() {
        val periode = Periode.create(1.januar(2020), 31.mars(2020))

        val totaltFradrag = 100000.0

        val beregning = BeregningFactory(clock = fixedClock).ny(
            fradrag = listOf(
                FradragForPeriode(
                    fradragstype = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragForPeriode(
                    fradragstype = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = totaltFradrag,
                    periode = januar(2020),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = periode,
                    strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
                ),
            ),
        )

        val beregning2 = BeregningFactory(clock = fixedClock).ny(
            fradrag = listOf(
                FradragForPeriode(
                    fradragstype = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragForPeriode(
                    fradragstype = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = totaltFradrag,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = periode,
                    strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
                ),
            ),
        )

        beregning.getMånedsberegninger() shouldNotBe beregning2.getMånedsberegninger()
        beregning.getSumFradrag() shouldNotBe beregning2.getSumFradrag()
        beregning.getSumYtelse() shouldNotBe beregning2.getSumYtelse()
    }

    @Test
    fun `fradrag må være innenfor beregningsperioden`() {
        val beregningsperiode = år(2020)
        assertThrows<IllegalArgumentException> {
            BeregningFactory(clock = fixedClock).ny(
                fradrag = listOf(
                    FradragFactory.nyFradragsperiode(
                        fradragstype = Fradragstype.ForventetInntekt,
                        månedsbeløp = 12000.0,
                        periode = Periode.create(fraOgMed = 1.februar(2020), tilOgMed = 31.januar(2022)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
                beregningsperioder = listOf(
                    Beregningsperiode(
                        periode = beregningsperiode,
                        strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
                    ),
                ),
            )
        }

        assertThrows<IllegalArgumentException> {
            BeregningFactory(clock = fixedClock).ny(
                fradrag = listOf(
                    FradragFactory.nyFradragsperiode(
                        fradragstype = Fradragstype.ForventetInntekt,
                        månedsbeløp = 12000.0,
                        periode = Periode.create(fraOgMed = 1.januar(2019), tilOgMed = 31.januar(2022)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
                beregningsperioder = listOf(
                    Beregningsperiode(
                        periode = beregningsperiode,
                        strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
                    ),
                ),
            )
        }
    }

    @Test
    fun `should be equal to BeregningMedFradragBeregnetMånedsvis ignoring id, opprettet and begrunnelse`() {
        val a: Beregning = createBeregning(fixedTidspunkt, "a")
        val b: Beregning = createBeregning(fixedTidspunkt.plus(1, ChronoUnit.SECONDS), "b")
        a shouldBe b
        a.getId() shouldNotBe b.getId()
        a.getOpprettet() shouldNotBe b.getOpprettet()
        a.getBegrunnelse() shouldNotBe b.getBegrunnelse()
        (a === b) shouldBe false
    }

    @Test
    fun `sosialstønad som gir beløp under minstenivå leder ikke til 0-beløp`() {
        val periode = Periode.create(1.juni(2021), 31.desember(2021))
        val beregning = BeregningFactory(clock = fixedClock).ny(
            fradrag = listOf(
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode, utenlandskInntekt = null, tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.Sosialstønad,
                    månedsbeløp = satsFactoryTestPåDato().høyUføre(juni(2021)).satsForMånedAsDouble - 100,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = periode,
                    strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
                ),
            ),
        )

        beregning.getSumYtelse() shouldBe periode.getAntallMåneder() * 100
        beregning.finnMånederMedMerknadForAvslag() shouldBe IngenMerknaderForAvslag.left()
        beregning.finnMånederMedMerknad().getOrFail()
            .map { it.second }
            .all { it.contains(Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats) }
    }

    @Test
    fun `sosialstønad for EPS som gir beløp under minstenivå leder ikke til 0-beløp`() {
        val periode = Periode.create(1.juni(2021), 31.desember(2021))
        val beregning = BeregningFactory(clock = fixedClock).ny(
            fradrag = listOf(
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode, utenlandskInntekt = null, tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.Sosialstønad,
                    månedsbeløp = satsFactoryTestPåDato().høyUføre(juni(2021)).satsForMånedAsDouble - 100,
                    periode = periode,
                    tilhører = FradragTilhører.EPS,
                ),
            ),
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = periode,
                    strategy = BeregningStrategy.EpsUnder67År(satsFactoryTestPåDato(), Sakstype.UFØRE),
                ),
            ),
        )

        beregning.getSumYtelse() shouldBe periode.getAntallMåneder() * 100
        beregning.finnMånederMedMerknadForAvslag() shouldBe IngenMerknaderForAvslag.left()
        beregning.finnMånederMedMerknad().getOrFail()
            .map { it.second }
            .all { it.contains(Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats) }
    }

    @Test
    fun `merknader avslag`() {
        BeregningFactory(clock = fixedClock).ny(
            fradrag = listOf(
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = år(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 250000.0,
                    periode = juni(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.Kapitalinntekt,
                    månedsbeløp = 20750.0,
                    periode = desember(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = år(2021),
                    strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
                ),
            ),
        ).let {
            val beløpNull = it.getMånedsberegninger()[5] to listOf(
                Merknad.Beregning.Avslag.BeløpErNull,
            )
            val beløpMellomNullOgToProsent = it.getMånedsberegninger()[11] to listOf(
                Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats,
            )

            it.finnMånederMedMerknad().getOrFail() shouldBe listOf(
                beløpNull,
                beløpMellomNullOgToProsent,
            )
            it.finnMånederMedMerknadForAvslag().getOrFail() shouldBe listOf(
                beløpNull.first to beløpNull.second[0],
                beløpMellomNullOgToProsent.first to beløpMellomNullOgToProsent.second[0],
            )
            it.finnFørsteMånedMedMerknadForAvslag().getOrFail() shouldBe (beløpNull.first to beløpNull.second[0])
            it.harAlleMånederMerknadForAvslag() shouldBe false
        }
    }

    @Test
    fun `merknader avkorting utenlandsopphold under 2 prosent`() {
        BeregningFactory(clock = fixedClock).ny(
            fradrag = listOf(
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = år(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.AvkortingUtenlandsopphold,
                    månedsbeløp = 20750.0,
                    periode = januar(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.AvkortingUtenlandsopphold,
                    månedsbeløp = 20000.0,
                    periode = februar(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.Sosialstønad,
                    månedsbeløp = 900.0,
                    periode = februar(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 20000.0,
                    periode = april(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.AvkortingUtenlandsopphold,
                    månedsbeløp = 900.0,
                    periode = april(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.Sosialstønad,
                    månedsbeløp = 200.0,
                    periode = april(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = år(2021),
                    strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
                ),
            ),
        ).let {
            val avkortingJanuar = it.getMånedsberegninger()[0] to listOf(
                Merknad.Beregning.AvkortingFørerTilBeløpLavereEnnToProsentAvHøySats,
            )
            it.getMånedsberegninger()[0].getSumYtelse() shouldBe 196

            val avkortingFebruar = it.getMånedsberegninger()[1] to listOf(
                Merknad.Beregning.AvkortingFørerTilBeløpLavereEnnToProsentAvHøySats,
            )
            it.getMånedsberegninger()[1].getSumYtelse() shouldBe 46

            val avkortingApril = it.getMånedsberegninger()[3] to listOf(
                Merknad.Beregning.AvkortingFørerTilBeløpLavereEnnToProsentAvHøySats,
            )
            it.getMånedsberegninger()[3].getSumYtelse() shouldBe 0

            it.finnMånederMedMerknadForAvslag() shouldBe IngenMerknaderForAvslag.left()

            it.finnMånederMedMerknad().getOrFail() shouldBe listOf(
                avkortingJanuar,
                avkortingFebruar,
                avkortingApril,
            )
        }
    }

    @Test
    fun `merknader sosialstønad under 2 prosent`() {
        BeregningFactory(clock = fixedClock).ny(
            fradrag = listOf(
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = år(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.Sosialstønad,
                    månedsbeløp = 20750.0,
                    periode = januar(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.Sosialstønad,
                    månedsbeløp = 20750.0,
                    periode = februar(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 150.0,
                    periode = februar(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.Sosialstønad,
                    månedsbeløp = 5000.0,
                    periode = mars(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 20000.0,
                    periode = april(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.Sosialstønad,
                    månedsbeløp = 900.0,
                    periode = april(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.AvkortingUtenlandsopphold,
                    månedsbeløp = 200.0,
                    periode = april(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = år(2021),
                    strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
                ),
            ),
        ).let {
            val sosialstønadJanuar = it.getMånedsberegninger()[0] to listOf(
                Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats,
            )
            it.getMånedsberegninger()[0].getSumYtelse() shouldBe 196

            val sosialstønadFebruar = it.getMånedsberegninger()[1] to listOf(
                Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats,
            )
            it.getMånedsberegninger()[1].getSumYtelse() shouldBe 46

            val sosialstønadMars = it.getMånedsberegninger()[3] to listOf(
                Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats,
            )
            it.getMånedsberegninger()[3].getSumYtelse() shouldBe 0

            it.finnMånederMedMerknad().getOrFail() shouldBe listOf(
                sosialstønadJanuar,
                sosialstønadFebruar,
                sosialstønadMars,
            )
            it.finnMånederMedMerknadForAvslag() shouldBe IngenMerknaderForAvslag.left()
        }
    }

    private fun createBeregning(
        opprettet: Tidspunkt = fixedTidspunkt,
        begrunnelse: String = "begrunnelse",
    ): Beregning {
        val periode = år(2020)
        return BeregningFactory(fixedClock).ny(
            id = UUID.randomUUID(),
            opprettet = opprettet,
            fradrag = listOf(
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.ForventetInntekt,
                    månedsbeløp = 12000.0,
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            begrunnelse = begrunnelse,
            beregningsperioder = listOf(
                Beregningsperiode(
                    periode = periode,
                    strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato(), Sakstype.UFØRE),
                ),
            ),
        )
    }
}
