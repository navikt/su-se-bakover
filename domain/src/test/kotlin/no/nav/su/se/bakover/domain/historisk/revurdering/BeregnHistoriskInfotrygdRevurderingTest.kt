package no.nav.su.se.bakover.domain.historisk.revurdering

import behandling.revurdering.domain.Opphørsgrunn
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.bigdecimal.shouldBeEqualIgnoringScale
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifisering
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifiseringer
import no.nav.su.se.bakover.common.domain.regelspesifisering.RegelspesifisertGrunnlag
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskBosituasjon
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtakId
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import satser.domain.historisk.HistoriskInfotrygdSatskategori
import satser.domain.historisk.HistoriskInfotrygdSatsverdi
import vilkår.inntekt.domain.grunnlag.FradragForMåned
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal
import java.util.UUID

internal class BeregnHistoriskInfotrygdRevurderingTest {
    @Test
    fun `beregner ytelse med komplett regeltre`() {
        val grunnlag = listOf(grunnlag(januar), grunnlag(februar))
        val beregning = gjeldende().beregnRevurdering(grunnlag).shouldBeRight()

        beregning.månedsresultater.values.map {
            (it as HistoriskInfotrygdRevurdertMånedsresultat.Ytelse).sats
        } shouldBe listOf(forventetMånedssats, forventetMånedssats)

        beregning.benyttetRegel shouldBe forventetRegeltre(
            grunnlag = grunnlag,
            resultatregel = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_YTELSE,
            beregnetBeløp = forventetMånedssats,
            resultatBeløp = forventetMånedssats,
        )
    }

    @Test
    fun `opphører automatisk når beløpet er under minstegrensen`() {
        val grunnlag = listOf(
            grunnlag(januar, fradragUnderMinstegrensen(januar)),
            grunnlag(februar, fradragUnderMinstegrensen(februar)),
        )
        val beregning = gjeldende().beregnRevurdering(grunnlag).shouldBeRight()

        beregning.månedsresultater.getValue(februar) shouldBe
            HistoriskInfotrygdRevurdertMånedsresultat.Opphør(
                måned = februar,
                opprinneligStønadId = STØNAD_ID,
                opprinneligVedtakId = VEDTAK_ID,
                oppdragId = OPPDRAG_ID,
                bosituasjon = HistoriskBosituasjon.ENSLIG,
                sats = forventetMånedssats,
                fradrag = listOf(fradragUnderMinstegrensen(februar)),
                opphørsgrunn = Opphørsgrunn.SU_UNDER_MINSTEGRENSE,
                manueltOpphør = false,
            )
        beregning.benyttetRegel shouldBe forventetRegeltre(
            grunnlag = grunnlag,
            resultatregel = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_OPPHØR_UNDER_MINSTEGRENSE,
            beregnetBeløp = BigDecimal("52.0"),
            resultatBeløp = BigDecimal.ZERO,
        )
    }

    @ParameterizedTest
    @ValueSource(doubles = [15_952.0, 16_000.0])
    fun `opphører automatisk med komplett regeltre ved null eller negativt beregnet beløp`(inntekt: Double) {
        val grunnlag = listOf(januar, februar).map { måned ->
            grunnlag(måned, fradragUnderMinstegrensen(måned).copy(månedsbeløp = inntekt))
        }
        val beregning = gjeldende().beregnRevurdering(grunnlag).shouldBeRight()

        beregning.månedsresultater.values.forEach {
            it as HistoriskInfotrygdRevurdertMånedsresultat.Opphør
            it.opphørsgrunn shouldBe Opphørsgrunn.FOR_HØY_INNTEKT
            it.manueltOpphør shouldBe false
        }
        beregning.benyttetRegel shouldBe forventetRegeltre(
            grunnlag = grunnlag,
            resultatregel = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_OPPHØR_FOR_HØY_INNTEKT,
            beregnetBeløp = forventetMånedssats - BigDecimal.valueOf(inntekt),
            resultatBeløp = BigDecimal.ZERO,
        )
    }

    @Test
    fun `beløp lik minstegrensen gir ytelse med komplett regeltre`() {
        val minstegrense = BigDecimal("319.04")
        val inntekt = 15_632.96
        val grunnlag = listOf(januar, februar).map { måned ->
            grunnlag(måned, fradragUnderMinstegrensen(måned).copy(månedsbeløp = inntekt))
        }
        val beregning = gjeldende().beregnRevurdering(grunnlag).shouldBeRight()

        beregning.månedsresultater.values.forEach {
            it as HistoriskInfotrygdRevurdertMånedsresultat.Ytelse
            it.beløp shouldBe minstegrense
        }
        beregning.benyttetRegel shouldBe forventetRegeltre(
            grunnlag = grunnlag,
            resultatregel = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_YTELSE,
            beregnetBeløp = minstegrense,
            resultatBeløp = minstegrense,
        )
    }

    @Test
    fun `manuelt opphør gir opphør med valgt opphørsgrunn`() {
        val manueltOpphør = HistoriskInfotrygdManueltOpphør(Opphørsgrunn.FORMUE)
        val grunnlag = listOf(
            grunnlag(januar).copy(manueltOpphør = manueltOpphør),
            grunnlag(februar).copy(manueltOpphør = manueltOpphør),
        )
        val beregning = gjeldende().beregnRevurdering(grunnlag).shouldBeRight()

        beregning.månedsresultater.values.forEach {
            it as HistoriskInfotrygdRevurdertMånedsresultat.Opphør
            it.opphørsgrunn shouldBe Opphørsgrunn.FORMUE
            it.manueltOpphør shouldBe true
        }
        beregning.benyttetRegel shouldBe forventetRegeltre(
            grunnlag = grunnlag,
            resultatregel = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_MANUELT_OPPHØR,
            beregnetBeløp = forventetMånedssats,
            resultatBeløp = BigDecimal.ZERO,
            manuellOpphørsgrunn = manueltOpphør.opphørsgrunn,
        )
    }

    @Test
    fun `avviser ytelse og opphør i samme revurdering`() {
        gjeldende().beregnRevurdering(
            listOf(
                grunnlag(januar).copy(manueltOpphør = HistoriskInfotrygdManueltOpphør(Opphørsgrunn.FORMUE)),
                grunnlag(februar),
            ),
        ).shouldBeLeft() shouldBe KunneIkkeBeregneHistoriskInfotrygdRevurdering.BlandetYtelseOgOpphør
    }

    @Test
    fun `enslig får ikke fradrag for EPS`() {
        val beregning = gjeldende().beregnRevurdering(
            listOf(
                grunnlag(januar, epsFradrag(januar, Fradragstype.Arbeidsinntekt, 20_000.0)),
                grunnlag(februar, epsFradrag(februar, Fradragstype.Arbeidsinntekt, 20_000.0)),
            ),
        ).shouldBeRight()

        beregning.erOpphør shouldBe false
    }

    @Test
    fun `EPS over 67 trekkes bare for inntekt over ordinær sats i tillegg til sosialstønad`() {
        // EO mai 2019: 181 908 / 12 = 15 159
        val epsSats = BigDecimal(15_159)
        val beregning = gjeldende().beregnRevurdering(
            listOf(januar, februar).map { måned ->
                grunnlag(
                    måned,
                    epsFradrag(måned, Fradragstype.Arbeidsinntekt, 15_659.0),
                    epsFradrag(måned, Fradragstype.Sosialstønad, 100.0),
                ).copy(satskategori = HistoriskInfotrygdSatskategori.EO)
            },
        ).shouldBeRight()

        beregning.månedsresultater.values.forEach {
            it as HistoriskInfotrygdRevurdertMånedsresultat.Ytelse
            it.sats shouldBe epsSats
            it.beløp shouldBeEqualIgnoringScale epsSats - BigDecimal(600)
        }
    }

    @Test
    fun `EPS under 67 trekkes fullt ut`() {
        val beregning = gjeldende().beregnRevurdering(
            listOf(januar, februar).map { måned ->
                grunnlag(måned, epsFradrag(måned, Fradragstype.Arbeidsinntekt, 1_000.0))
                    .copy(satskategori = HistoriskInfotrygdSatskategori.EU)
            },
        ).shouldBeRight()

        beregning.månedsresultater.values.forEach {
            it as HistoriskInfotrygdRevurdertMånedsresultat.Ytelse
            it.beløp shouldBeEqualIgnoringScale it.sats - BigDecimal(1_000)
        }
    }

    private fun forventetRegeltre(
        grunnlag: List<HistoriskInfotrygdBeregningsgrunnlagForMåned>,
        resultatregel: Regelspesifiseringer,
        beregnetBeløp: BigDecimal,
        resultatBeløp: BigDecimal,
        manuellOpphørsgrunn: Opphørsgrunn? = null,
    ): Regelspesifisering {
        val satsregel = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_SATS.benyttRegelspesifisering(
            verdi = forventetMånedssats.toPlainString(),
            avhengigeRegler = listOf(
                RegelspesifisertGrunnlag.GRUNNLAG_HISTORISK_INFOTRYGD_SATSKATEGORI
                    .benyttGrunnlag(HistoriskInfotrygdSatskategori.EN.name),
                RegelspesifisertGrunnlag.GRUNNLAG_HISTORISK_INFOTRYGD_SATSVERDI
                    .benyttGrunnlag(HistoriskInfotrygdSatsverdi.Årsbeløp(BigDecimal(191_422)).toString()),
            ),
        )
        return Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_BEREGNING.benyttRegelspesifisering(
            verdi = "Beregnet ${grunnlag.size} måneder",
            avhengigeRegler = grunnlag.map { månedsgrunnlag ->
                resultatregel.benyttRegelspesifisering(
                    verdi = resultatBeløp.toPlainString(),
                    avhengigeRegler = listOfNotNull(
                        Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_MÅNEDSBEREGNING.benyttRegelspesifisering(
                            verdi = beregnetBeløp.toPlainString(),
                            avhengigeRegler = listOf(
                                satsregel,
                                RegelspesifisertGrunnlag.GRUNNLAG_FRADRAG.benyttGrunnlag(månedsgrunnlag.fradrag.toString()),
                                Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_MINSTEGRENSE.benyttRegelspesifisering(
                                    verdi = "319.04",
                                    avhengigeRegler = listOf(satsregel),
                                ),
                            ),
                        ),
                        manuellOpphørsgrunn?.let {
                            RegelspesifisertGrunnlag.GRUNNLAG_HISTORISK_INFOTRYGD_MANUELL_OPPHØRSGRUNN
                                .benyttGrunnlag(it.name)
                        },
                    ),
                )
            },
        )
    }

    private fun epsFradrag(måned: Måned, fradragstype: Fradragstype, beløp: Double) = FradragForMåned(
        fradragstype = fradragstype,
        månedsbeløp = beløp,
        måned = måned,
        tilhører = FradragTilhører.EPS,
    )

    private fun gjeldende() = GjeldendeHistoriskInfotrygdVedtaksdata(
        projeksjonId = UUID.randomUUID(),
        periode = Periode.create(januar.fraOgMed, februar.tilOgMed),
        tidslinje = linkedMapOf(
            januar to gammelYtelse(januar),
            februar to gammelYtelse(februar),
        ),
    )

    private fun grunnlag(
        måned: Måned,
        vararg fradrag: FradragForMåned,
    ) = HistoriskInfotrygdBeregningsgrunnlagForMåned(
        måned = måned,
        satskategori = HistoriskInfotrygdSatskategori.EN,
        fradrag = fradrag.toList(),
    )

    private fun fradragUnderMinstegrensen(måned: Måned) = FradragForMåned(
        fradragstype = Fradragstype.Arbeidsinntekt,
        månedsbeløp = 15_900.0,
        måned = måned,
        tilhører = FradragTilhører.BRUKER,
    )

    private fun gammelYtelse(måned: Måned) =
        GjeldendeHistoriskInfotrygdMånedsdata.Ytelse(
            måned = måned,
            kilde = HistoriskInfotrygdMånedskilde.OriginalProjeksjon(UUID.randomUUID()),
            opprinneligStønadId = STØNAD_ID,
            opprinneligVedtakId = VEDTAK_ID,
            oppdragId = OPPDRAG_ID,
            bosituasjon = HistoriskBosituasjon.ENSLIG,
            sats = BigDecimal(10_000),
            fradrag = BigDecimal.ZERO,
            fradragsgrunnlag = HistoriskInfotrygdFradragsgrunnlag.OriginaleKoder(emptyList()),
        )

    private companion object {
        val januar = januar(2020)
        val februar = februar(2020)

        // 191 422 / 12 = 15 951,83, avrundet til hele kroner
        val forventetMånedssats: BigDecimal = BigDecimal(15_952)
        val STØNAD_ID = HistoriskStønadId(1)
        val VEDTAK_ID = HistoriskVedtakId(2)
        const val OPPDRAG_ID = "oppdrag-1"
    }
}
