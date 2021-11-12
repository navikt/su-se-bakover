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
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.BeregningMedFradragBeregnetMånedsvis
import no.nav.su.se.bakover.domain.beregning.IngenMerknaderForAvslag
import no.nav.su.se.bakover.domain.beregning.Merknad
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.finnFørsteMånedMedMerknadForAvslag
import no.nav.su.se.bakover.domain.beregning.finnMånederMedMerknad
import no.nav.su.se.bakover.domain.beregning.finnMånederMedMerknadForAvslag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.beregning.fradrag.IkkePeriodisertFradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.PeriodisertFradrag
import no.nav.su.se.bakover.domain.beregning.harAlleMånederMerknadForAvslag
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.periode2021
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class BeregningMedFradragBeregnetMånedsvisTest {

    @Test
    fun `summer for enkel beregning`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningFactory(clock = fixedClock).ny(
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
        )
        beregning.getSumYtelse() shouldBe 250116
        beregning.getSumFradrag() shouldBe 0
    }

    @Test
    fun `fradrag for alle perioder`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningFactory(clock = fixedClock).ny(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt, månedsbeløp = 1000.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
        )

        beregning.getSumYtelse() shouldBe 238116
        beregning.getSumFradrag() shouldBe 12000
    }

    @Test
    fun `fradrag som gjør at alle perioder får beløp under minstenivå`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningFactory(clock = fixedClock).ny(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt, månedsbeløp = 20500.0,
                    periode = Periode.create(1.januar(2020), 30.april(2020)),
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt, månedsbeløp = 20800.0,
                    periode = Periode.create(1.mai(2020), 31.desember(2020)),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
        )

        beregning.finnMånederMedMerknad().getOrFail() shouldContainAll listOf(
            beregning.getMånedsberegninger()[0] to listOf(Merknad.Beregning.BeløpMellomNullOgToProsentAvHøySats),
            beregning.getMånedsberegninger()[4] to listOf(Merknad.Beregning.BeløpMellomNullOgToProsentAvHøySats),
            beregning.getMånedsberegninger()[11] to listOf(Merknad.Beregning.BeløpMellomNullOgToProsentAvHøySats),
        )
    }

    @Test
    fun `fradrag som gjør at alle perioder får beløp lik 0`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningFactory(clock = fixedClock).ny(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt, månedsbeløp = 150000.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
        )

        beregning.harAlleMånederMerknadForAvslag() shouldBe true
        beregning.finnFørsteMånedMedMerknadForAvslag()
            .getOrFail() shouldBe (beregning.getMånedsberegninger()[0] to Merknad.Beregning.BeløpErNull)
        beregning.finnMånederMedMerknadForAvslag().getOrFail() shouldContainAll listOf(
            beregning.getMånedsberegninger()[0] to Merknad.Beregning.BeløpErNull,
            beregning.getMånedsberegninger()[11] to Merknad.Beregning.BeløpErNull,
        )
    }

    @Test
    fun `fradrag for enkelte perioder`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningFactory(clock = fixedClock).ny(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 500.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 6000.0,
                    periode = Periode.create(1.juni(2020), 30.juni(2020)),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
        )

        beregning.getSumYtelse() shouldBe 238616
        beregning.getSumFradrag() shouldBe 11500
    }

    @Test
    fun `overlappende fradrag for samme periode`() {
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningFactory(clock = fixedClock).ny(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 500.0,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
                IkkePeriodisertFradrag(
                    type = Fradragstype.Kontantstøtte,
                    månedsbeløp = 6000.0,
                    periode = Periode.create(1.januar(2020), 31.januar(2020)),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
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
        val periode = Periode.create(1.januar(2020), 31.desember(2020))
        val beregning = BeregningFactory(clock = fixedClock).ny(
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                IkkePeriodisertFradrag(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 20426.42,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
        )

        val (janAprUnderMinstenivå, janAprAndre) = beregning.getMånedsberegninger()
            .flatMap { it.getFradrag() }
            .filter { Periode.create(1.januar(2020), 30.april(2020)) inneholder it.periode }
            .partition { it.fradragstype == Fradragstype.UnderMinstenivå }

        janAprUnderMinstenivå shouldHaveSize 4
        janAprUnderMinstenivå.forEach {
            it shouldBe PeriodisertFradrag(
                type = Fradragstype.UnderMinstenivå,
                månedsbeløp = 211.0,
                periode = it.periode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            )
        }
        janAprAndre shouldHaveSize 4
        janAprAndre.forEach {
            it shouldBe PeriodisertFradrag(
                type = Fradragstype.ForventetInntekt,
                månedsbeløp = 20426.42,
                periode = it.periode,
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
            it shouldBe PeriodisertFradrag(
                type = Fradragstype.ForventetInntekt,
                månedsbeløp = 20426.42,
                periode = it.periode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            )
        }

        beregning.getSumYtelse() shouldBe 4152
        beregning.getSumFradrag() shouldBe 245961.0.plusOrMinus(0.5)
    }

    @Test
    fun `generer bare bare id og opprettet en gang for hvert objekt`() {
        val beregning = BeregningFactory(clock = fixedClock).ny(
            periode = Periode.create(1.januar(2020), 31.mars(2020)),
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = Periode.create(1.januar(2020), 31.mars(2020)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
        )
        beregning.getId() shouldBe beregning.getId()
        beregning.getOpprettet() shouldBe beregning.getOpprettet()
    }

    @Test
    fun `fradrag inkluderes kun i den måneden de er aktuelle`() {
        val periode = Periode.create(1.januar(2020), 31.mars(2020))

        val totaltFradrag = 100000.0

        val beregning = BeregningFactory(clock = fixedClock).ny(
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
                    månedsbeløp = totaltFradrag,
                    periode = Periode.create(1.januar(2020), 31.januar(2020)),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
        )

        beregning.getSumYtelse() shouldBe 41274
        beregning.getSumFradrag() shouldBe 20637.32
        val grouped = beregning.getMånedsberegninger().groupBy { it.periode }
        val januar = grouped[Periode.create(1.januar(2020), 31.januar(2020))]!!.first()
        januar.getSumFradrag() shouldBe 20637.32
        januar.getSumYtelse() shouldBe 0
        val februar = grouped[Periode.create(1.februar(2020), 29.februar(2020))]!!.first()
        februar.getSumFradrag() shouldBe 0
        februar.getSumYtelse() shouldBe 20637
    }

    @Test
    fun `To beregninger med samme totalsum for fradrag, men i forskjellige perioder gir ikke nødvendigvis samme resultat`() {
        val periode = Periode.create(1.januar(2020), 31.mars(2020))

        val totaltFradrag = 100000.0

        val beregning = BeregningFactory(clock = fixedClock).ny(
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
                    månedsbeløp = totaltFradrag,
                    periode = Periode.create(1.januar(2020), 31.januar(2020)),
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
        )

        val beregning2 = BeregningFactory(clock = fixedClock).ny(
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
                    månedsbeløp = totaltFradrag,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
        )

        beregning.getMånedsberegninger() shouldNotBe beregning2.getMånedsberegninger()
        beregning.getSumFradrag() shouldNotBe beregning2.getSumFradrag()
        beregning.getSumYtelse() shouldNotBe beregning2.getSumYtelse()
    }

    @Test
    fun `fradrag må være innenfor beregningsperioden`() {
        val beregningsperiode = Periode.create(fraOgMed = 1.januar(2020), tilOgMed = 31.desember(2020))
        assertThrows<IllegalArgumentException> {
            BeregningFactory(clock = fixedClock).ny(
                periode = beregningsperiode,
                sats = Sats.HØY,
                fradrag = listOf(
                    FradragFactory.ny(
                        type = Fradragstype.ForventetInntekt,
                        månedsbeløp = 12000.0,
                        periode = Periode.create(fraOgMed = 1.februar(2020), tilOgMed = 31.januar(2022)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
                fradragStrategy = FradragStrategy.Enslig,
            )
        }

        assertThrows<IllegalArgumentException> {
            BeregningFactory(clock = fixedClock).ny(
                periode = beregningsperiode,
                sats = Sats.HØY,
                fradrag = listOf(
                    FradragFactory.ny(
                        type = Fradragstype.ForventetInntekt,
                        månedsbeløp = 12000.0,
                        periode = Periode.create(fraOgMed = 1.januar(2019), tilOgMed = 31.januar(2022)),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
                fradragStrategy = FradragStrategy.Enslig,
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
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode, utenlandskInntekt = null, tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.ny(
                    type = Fradragstype.Sosialstønad,
                    månedsbeløp = Sats.HØY.månedsbeløp(periode.fraOgMed) - 100,
                    periode = periode,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
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
            periode = periode,
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode, utenlandskInntekt = null, tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.ny(
                    type = Fradragstype.Sosialstønad,
                    månedsbeløp = Sats.HØY.månedsbeløp(periode.fraOgMed) - 100,
                    periode = periode,
                    tilhører = FradragTilhører.EPS,
                ),
            ),
            fradragStrategy = FradragStrategy.EpsUnder67År,
        )

        beregning.getSumYtelse() shouldBe periode.getAntallMåneder() * 100
        beregning.finnMånederMedMerknadForAvslag() shouldBe IngenMerknaderForAvslag.left()
        beregning.finnMånederMedMerknad().getOrFail()
            .map { it.second }
            .all { it.contains(Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats) }
    }

    @Test
    fun `merknader`() {
        BeregningFactory(clock = fixedClock).ny(
            periode = periode2021,
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    periode = periode2021,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.ny(
                    type = Fradragstype.Sosialstønad,
                    månedsbeløp = 20750.0,
                    periode = april(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    månedsbeløp = 250000.0,
                    periode = juni(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
                FradragFactory.ny(
                    type = Fradragstype.Kapitalinntekt,
                    månedsbeløp = 21750.0,
                    periode = desember(2021),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
        ).let {
            val sosialstønad =
                it.getMånedsberegninger()[3] to listOf(Merknad.Beregning.SosialstønadFørerTilBeløpLavereEnnToProsentAvHøySats)
            val beløpNull = it.getMånedsberegninger()[5] to listOf(
                Merknad.Beregning.BeløpErNull,
            )
            val beløpMellomNullOgToProsent = it.getMånedsberegninger()[11] to listOf(
                Merknad.Beregning.BeløpMellomNullOgToProsentAvHøySats,
            )

            it.finnMånederMedMerknad().getOrFail() shouldBe listOf(
                sosialstønad,
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

    private fun createBeregning(opprettet: Tidspunkt = fixedTidspunkt, begrunnelse: String = "begrunnelse") =
        BeregningMedFradragBeregnetMånedsvis(
            id = UUID.randomUUID(),
            opprettet = opprettet,
            periode = Periode.create(fraOgMed = 1.januar(2020), tilOgMed = 31.desember(2020)),
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 12000.0,
                    periode = Periode.create(fraOgMed = 1.januar(2020), tilOgMed = 31.desember(2020)),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
            fradragStrategy = FradragStrategy.Enslig,
            begrunnelse = begrunnelse,
        )
}
