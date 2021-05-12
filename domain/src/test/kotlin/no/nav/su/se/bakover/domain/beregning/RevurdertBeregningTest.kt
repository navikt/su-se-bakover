package no.nav.su.se.bakover.domain.beregning

import arrow.core.left
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategyName
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.fixedTidspunkt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import org.junit.jupiter.api.Test

internal class RevurdertBeregningTest {
    private val forventetMånedsbeløp: Double = Sats.HØY.månedsbeløp(1.januar(2021))
    private val januar: Periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021))
    private val februar: Periode = Periode.create(fraOgMed = 1.februar(2021), tilOgMed = 28.februar(2021))

    @Test
    fun `en måned stønaden ikke endrer seg`() {
        listOf(true, false).forEach {
            val actual = RevurdertBeregning.fraSøknadsbehandling(
                vedtattBeregning = BeregningFactory.ny(
                    periode = januar,
                    sats = Sats.HØY,
                    fradrag = listOf(
                        fradrag(januar, 0.0)
                    ),
                    fradragStrategy = FradragStrategy.fromName(FradragStrategyName.Enslig),
                ),
                beregningsgrunnlag = Beregningsgrunnlag.create(
                    beregningsperiode = januar,
                    uføregrunnlag = listOf(
                        Grunnlag.Uføregrunnlag(
                            opprettet = fixedTidspunkt,
                            periode = januar,
                            uføregrad = Uføregrad.parse(100),
                            forventetInntekt = 0,
                        ),
                    ),
                    fradragFraSaksbehandler = emptyList(),
                ),
                beregningsstrategi = BeregningStrategy.BorAlene,
                beregnMedVirkningNesteMånedDersomStønadenGårNed = it,
            ).orNull()!!

            assertSoftly {
                actual.getSats() shouldBe Sats.HØY
                actual.getMånedsberegninger() shouldBe listOf(
                    periodisertBeregning(januar, 0.0)
                )
                actual.getFradrag() shouldBe listOf(
                    fradrag(januar, 0.0)
                )
                actual.getSumYtelse() shouldBe (forventetMånedsbeløp + 0.5).toInt()
                actual.getSumFradrag() shouldBe 0.0
                actual.getFradragStrategyName() shouldBe FradragStrategyName.Enslig
            }
        }
    }

    @Test
    fun `en måned stønaden blir satt opp`() {
        listOf(true, false).forEach {
            val actual = RevurdertBeregning.fraSøknadsbehandling(
                vedtattBeregning = BeregningFactory.ny(
                    periode = januar,
                    sats = Sats.HØY,
                    fradrag = listOf(
                        fradrag(januar, 10000.0)
                    ),
                    fradragStrategy = FradragStrategy.fromName(FradragStrategyName.Enslig),
                ),
                beregningsgrunnlag = Beregningsgrunnlag.create(
                    beregningsperiode = januar,
                    uføregrunnlag = listOf(
                        Grunnlag.Uføregrunnlag(
                            opprettet = fixedTidspunkt,
                            periode = januar,
                            uføregrad = Uføregrad.parse(100),
                            forventetInntekt = 0,
                        ),
                    ),
                    fradragFraSaksbehandler = emptyList(),
                ),
                beregningsstrategi = BeregningStrategy.BorAlene,
                beregnMedVirkningNesteMånedDersomStønadenGårNed = it,
            ).orNull()!!

            assertSoftly {
                actual.getSats() shouldBe Sats.HØY
                actual.getMånedsberegninger() shouldBe listOf(
                    periodisertBeregning(januar, 0.0)
                )
                actual.getFradrag() shouldBe listOf(
                    fradrag(januar, 0.0)
                )
                actual.getSumYtelse() shouldBe (forventetMånedsbeløp + 0.5).toInt()
                actual.getSumFradrag() shouldBe 0.0
                actual.getFradragStrategyName() shouldBe FradragStrategyName.Enslig
            }
        }
    }

    @Test
    fun `to måneder stønaden blir satt opp`() {
        listOf(true, false).forEach {
            val periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 28.februar(2021))
            val månedsberegninger = listOf<Månedsberegning>(
                mock { on { getSumYtelse() } doReturn 1 },
                mock { on { getSumYtelse() } doReturn 2 }
            )
            val actual = RevurdertBeregning.fraSøknadsbehandling(
                vedtattBeregning = mock { on { getMånedsberegninger() } doReturn månedsberegninger },
                beregningsgrunnlag = Beregningsgrunnlag.create(
                    beregningsperiode = periode,
                    uføregrunnlag = listOf(
                        Grunnlag.Uføregrunnlag(
                            opprettet = fixedTidspunkt,
                            periode = periode,
                            uføregrad = Uføregrad.parse(100),
                            forventetInntekt = 0,
                        ),
                    ),
                    fradragFraSaksbehandler = emptyList(),
                ),
                beregningsstrategi = BeregningStrategy.BorAlene,
                beregnMedVirkningNesteMånedDersomStønadenGårNed = it,
            ).orNull()!!

            assertSoftly {
                actual.getSats() shouldBe Sats.HØY
                actual.getMånedsberegninger() shouldBe listOf(
                    periodisertBeregning(januar, 0.0),
                    periodisertBeregning(februar, 0.0)
                )
                actual.getFradrag() shouldBe listOf(
                    FradragFactory.ny(
                        opprettet = fixedTidspunkt,
                        type = Fradragstype.ForventetInntekt,
                        månedsbeløp = 0.0,
                        periode = periode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                )
                actual.getSumYtelse() shouldBe (forventetMånedsbeløp + forventetMånedsbeløp + 0.5).toInt()
                actual.getSumFradrag() shouldBe 0.0
                actual.getFradragStrategyName() shouldBe FradragStrategyName.Enslig
            }
        }
    }

    @Test
    fun `Kan revurdere siste måned dersom stønaden går ned hvis beregnMedVirkningNesteMånedDersomStønadenGårNed false`() {
        val actual = RevurdertBeregning.fraSøknadsbehandling(
            vedtattBeregning = BeregningFactory.ny(
                periode = januar,
                sats = Sats.HØY,
                fradrag = listOf(
                    fradrag(januar, 0.0)
                ),
                fradragStrategy = FradragStrategy.fromName(FradragStrategyName.Enslig),
            ),
            beregningsgrunnlag = Beregningsgrunnlag.create(
                beregningsperiode = januar,
                uføregrunnlag = listOf(
                    Grunnlag.Uføregrunnlag(
                        opprettet = fixedTidspunkt,
                        periode = januar,
                        uføregrad = Uføregrad.parse(99),
                        forventetInntekt = 12,
                    ),
                ),
                fradragFraSaksbehandler = emptyList(),
            ),
            beregningsstrategi = BeregningStrategy.BorAlene,
            beregnMedVirkningNesteMånedDersomStønadenGårNed = false,
        ).orNull()!!

        assertSoftly {
            actual.getSats() shouldBe Sats.HØY
            actual.getMånedsberegninger() shouldBe listOf(
                periodisertBeregning(januar, 1.0)
            )
            actual.getFradrag() shouldBe listOf(
                FradragFactory.ny(
                    opprettet = fixedTidspunkt,
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = 1.0,
                    periode = januar,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            )
            actual.getSumYtelse() shouldBe (forventetMånedsbeløp - 0.5).toInt()
            actual.getSumFradrag() shouldBe 1.0
            actual.getFradragStrategyName() shouldBe FradragStrategyName.Enslig
        }
    }

    @Test
    fun `Kan ikke revurdere kun siste måned dersom stønaden går ned hvis beregnMedVirkningNesteMånedDersomStønadenGårNed true`() {
        val actual = RevurdertBeregning.fraSøknadsbehandling(
            vedtattBeregning = BeregningFactory.ny(
                periode = januar,
                sats = Sats.HØY,
                fradrag = listOf(
                    fradrag(januar, 0.0)
                ),
                fradragStrategy = FradragStrategy.fromName(FradragStrategyName.Enslig),
            ),
            beregningsgrunnlag = Beregningsgrunnlag.create(
                beregningsperiode = januar,
                uføregrunnlag = listOf(
                    Grunnlag.Uføregrunnlag(
                        opprettet = fixedTidspunkt,
                        periode = januar,
                        uføregrad = Uføregrad.parse(99),
                        forventetInntekt = 12,
                    ),
                ),
                fradragFraSaksbehandler = emptyList(),
            ),
            beregningsstrategi = BeregningStrategy.BorAlene,
            beregnMedVirkningNesteMånedDersomStønadenGårNed = true,
        )

        actual.shouldBe(KanIkkeVelgeSisteMånedVedNedgangIStønaden.left())
    }

    @Test
    fun `to måneder stønaden blir satt ned hopper over den første måneden`() {
        val periode = Periode.create(1.januar(2021), 28.februar(2021))
        val actual = RevurdertBeregning.fraSøknadsbehandling(
            vedtattBeregning = BeregningFactory.ny(
                periode = periode,
                sats = Sats.HØY,
                fradrag = listOf(
                    fradrag(periode, 0.0)
                ),
                fradragStrategy = FradragStrategy.fromName(FradragStrategyName.Enslig),
            ),
            beregningsgrunnlag = Beregningsgrunnlag.create(
                beregningsperiode = periode,
                uføregrunnlag = listOf(
                    Grunnlag.Uføregrunnlag(
                        opprettet = fixedTidspunkt,
                        periode = periode,
                        uføregrad = Uføregrad.parse(80),
                        forventetInntekt = 12000,
                    ),
                ),
                fradragFraSaksbehandler = listOf(
                    fradrag(januar, 10.0, Fradragstype.Arbeidsinntekt),
                    fradrag(februar, 20.0, Fradragstype.Kapitalinntekt)
                ),
            ),
            beregningsstrategi = BeregningStrategy.BorAlene,
            beregnMedVirkningNesteMånedDersomStønadenGårNed = true,
        ).orNull()!!

        assertSoftly {
            actual.getSats() shouldBe Sats.HØY
            actual.getMånedsberegninger() shouldBe listOf(
                periodisertBeregningListe(
                    februar,
                    periodisertFradrag(februar, 20.0, Fradragstype.Kapitalinntekt) + periodisertFradrag(februar, 1000.0)
                )
            )
            actual.getFradrag() shouldBe listOf(
                fradrag(februar, 20.0, Fradragstype.Kapitalinntekt),
                fradrag(februar, 1000.0)

            )
            actual.getSumYtelse() shouldBe (forventetMånedsbeløp - 1000 - 20 + 0.5).toInt()
            actual.getSumFradrag() shouldBe 1020.0
            actual.getFradragStrategyName() shouldBe FradragStrategyName.Enslig
        }
    }

    private fun fradrag(
        periode: Periode,
        månedsbeløp: Double,
        fradragstype: Fradragstype = Fradragstype.ForventetInntekt
    ) = FradragFactory.ny(
        opprettet = fixedTidspunkt,
        type = fradragstype,
        månedsbeløp = månedsbeløp,
        periode = periode,
        utenlandskInntekt = null,
        tilhører = FradragTilhører.BRUKER,
    )

    private fun periodisertBeregning(
        periode: Periode,
        forventetInntektPerMåned: Double,
        fradragstype: Fradragstype = Fradragstype.ForventetInntekt
    ) = PeriodisertBeregning(
        periode = periode,
        sats = Sats.HØY,
        fradrag = FradragFactory.periodiser(
            FradragFactory.ny(
                opprettet = fixedTidspunkt,
                type = fradragstype,
                månedsbeløp = forventetInntektPerMåned,
                periode = periode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        ),
    )

    private fun periodisertBeregningListe(periode: Periode, liste: List<Fradrag>) = PeriodisertBeregning(
        periode = periode,
        sats = Sats.HØY,
        fradrag = liste
    )

    private fun periodisertFradrag(
        periode: Periode,
        forventetInntektPerMåned: Double,
        fradragstype: Fradragstype = Fradragstype.ForventetInntekt
    ): List<Fradrag> {
        return FradragFactory.periodiser(
            FradragFactory.ny(
                opprettet = fixedTidspunkt,
                type = fradragstype,
                månedsbeløp = forventetInntektPerMåned,
                periode = periode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            ),
        )
    }
}
