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

        beregning.månedsresultater.mapValues { it.value.benyttetRegel } shouldBe forventetRegeltre(
            grunnlag = grunnlag,
            resultatregel = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_YTELSE,
            beregnetBeløp = forventetMånedssats,
            resultatBeløp = forventetMånedssats,
        )
    }

    @Test
    fun `knytter ulike fradrag og regeltrær til riktig månedsresultat`() {
        val januarGrunnlag = grunnlag(januar)
        val februarGrunnlag = grunnlag(
            februar,
            fradragUnderMinstegrensen(februar).copy(månedsbeløp = 1_000.0),
        )
        val forventetFebruarBeløp = BigDecimal("14952.0")
        val beregning = gjeldende().beregnRevurdering(listOf(januarGrunnlag, februarGrunnlag)).shouldBeRight()

        beregning.månedsresultater.mapValues { (it.value as HistoriskInfotrygdRevurdertMånedsresultat.Ytelse).beløp } shouldBe
            mapOf(januar to forventetMånedssats, februar to forventetFebruarBeløp)
        beregning.månedsresultater.mapValues { it.value.benyttetRegel } shouldBe
            forventetRegeltre(
                grunnlag = listOf(januarGrunnlag),
                resultatregel = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_YTELSE,
                beregnetBeløp = forventetMånedssats,
                resultatBeløp = forventetMånedssats,
            ) + forventetRegeltre(
                grunnlag = listOf(februarGrunnlag),
                resultatregel = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_YTELSE,
                beregnetBeløp = forventetFebruarBeløp,
                resultatBeløp = forventetFebruarBeløp,
            )
    }

    @Test
    fun `knytter komplette ulike regelgrener for EN og EO til riktig måned`() {
        val januarGrunnlag = grunnlag(januar)
        val februarGrunnlag = grunnlag(
            februar,
            epsFradrag(februar, Fradragstype.Arbeidsinntekt, 15_659.0),
            epsFradrag(februar, Fradragstype.Sosialstønad, 100.0),
        ).copy(satskategori = HistoriskInfotrygdSatskategori.EO)
        val forventetEoSats = BigDecimal(15_159)
        val forventetEoFradrag = BigDecimal("600.0")
        val forventetEoBeløp = BigDecimal("14559.0")
        val forventetEnRegel = forventetRegeltre(
            grunnlag = listOf(januarGrunnlag),
            resultatregel = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_YTELSE,
            beregnetBeløp = forventetMånedssats,
            resultatBeløp = forventetMånedssats,
        ).getValue(januar)
        val eoSatsregel = forventetSatsregel(
            kategori = HistoriskInfotrygdSatskategori.EO,
            månedssats = forventetEoSats,
            årsbeløp = BigDecimal(181_908),
        )
        val forventetEoRegel = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_YTELSE.benyttRegelspesifisering(
            verdi = forventetEoBeløp.toPlainString(),
            avhengigeRegler = listOf(
                Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_MÅNEDSBEREGNING.benyttRegelspesifisering(
                    verdi = forventetEoBeløp.toPlainString(),
                    avhengigeRegler = listOf(
                        Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_SATS_MINUS_FRADRAG.benyttRegelspesifisering(
                            verdi = forventetEoBeløp.toPlainString(),
                            avhengigeRegler = listOf(
                                eoSatsregel,
                                Regelspesifiseringer.REGEL_FRADRAG_EPS_OVER_FRIBELØP.benyttRegelspesifisering(
                                    verdi = forventetEoFradrag.toPlainString(),
                                    avhengigeRegler = listOf(
                                        RegelspesifisertGrunnlag.GRUNNLAG_FRADRAG.benyttGrunnlag(februarGrunnlag.fradrag.toString()),
                                        eoSatsregel,
                                    ),
                                ),
                            ),
                        ),
                        Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_MINSTEGRENSE.benyttRegelspesifisering(
                            verdi = minstegrense.toPlainString(),
                            avhengigeRegler = listOf(
                                forventetSatsregel(
                                    kategori = HistoriskInfotrygdSatskategori.EN,
                                    månedssats = forventetMånedssats,
                                    årsbeløp = BigDecimal(191_422),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val beregning = gjeldende().beregnRevurdering(listOf(januarGrunnlag, februarGrunnlag)).shouldBeRight()

        beregning.månedsresultater shouldBe linkedMapOf(
            januar to HistoriskInfotrygdRevurdertMånedsresultat.Ytelse(
                måned = januar,
                opprinneligStønadId = STØNAD_ID,
                opprinneligVedtakId = VEDTAK_ID,
                oppdragId = OPPDRAG_ID,
                bosituasjon = HistoriskBosituasjon.ENSLIG,
                sats = forventetMånedssats,
                fradrag = januarGrunnlag.fradrag,
                benyttetRegel = forventetEnRegel,
            ),
            februar to HistoriskInfotrygdRevurdertMånedsresultat.Ytelse(
                måned = februar,
                opprinneligStønadId = STØNAD_ID,
                opprinneligVedtakId = VEDTAK_ID,
                oppdragId = OPPDRAG_ID,
                bosituasjon = HistoriskBosituasjon.EPS_OVER_67,
                sats = forventetEoSats,
                fradrag = februarGrunnlag.fradrag,
                benyttetRegel = forventetEoRegel,
            ),
        )
        val februarResultat = beregning.månedsresultater.getValue(februar) as HistoriskInfotrygdRevurdertMånedsresultat.Ytelse
        februarResultat.sumFradrag shouldBe forventetEoFradrag
        februarResultat.beløp shouldBe forventetEoBeløp
    }

    @Test
    fun `opphører automatisk når beløpet er under minstegrensen`() {
        val januarFradrag = fradragUnderMinstegrensen(januar)
        val februarFradrag = fradragUnderMinstegrensen(februar)
        val grunnlag = listOf(
            grunnlag(januar, januarFradrag),
            grunnlag(februar, februarFradrag),
        )
        val beregning = gjeldende().beregnRevurdering(grunnlag).shouldBeRight()
        val forventedeRegler = forventetRegeltre(
            grunnlag = grunnlag,
            resultatregel = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_OPPHØR_UNDER_MINSTEGRENSE,
            beregnetBeløp = BigDecimal("52.0"),
            resultatBeløp = BigDecimal.ZERO,
        )

        beregning.månedsresultater.getValue(februar) shouldBe
            HistoriskInfotrygdRevurdertMånedsresultat.Opphør(
                måned = februar,
                opprinneligStønadId = STØNAD_ID,
                opprinneligVedtakId = VEDTAK_ID,
                oppdragId = OPPDRAG_ID,
                bosituasjon = HistoriskBosituasjon.ENSLIG,
                sats = forventetMånedssats,
                fradrag = listOf(februarFradrag),
                opphørsgrunn = Opphørsgrunn.SU_UNDER_MINSTEGRENSE,
                manueltOpphør = false,
                benyttetRegel = forventedeRegler.getValue(februar),
            )
        beregning.månedsresultater.mapValues { it.value.benyttetRegel } shouldBe forventedeRegler
    }

    @ParameterizedTest
    @ValueSource(doubles = [FORVENTET_MÅNEDSSATS_I_KRONER, 16_000.0])
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
        beregning.månedsresultater.mapValues { it.value.benyttetRegel } shouldBe forventetRegeltre(
            grunnlag = grunnlag,
            resultatregel = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_OPPHØR_FOR_HØY_INNTEKT,
            beregnetBeløp = forventetMånedssats - BigDecimal.valueOf(inntekt),
            resultatBeløp = BigDecimal.ZERO,
        )
    }

    @Test
    fun `beløp lik minstegrensen gir ytelse med komplett regeltre`() {
        val inntekt = 15_632.96
        val grunnlag = listOf(januar, februar).map { måned ->
            grunnlag(måned, fradragUnderMinstegrensen(måned).copy(månedsbeløp = inntekt))
        }
        val beregning = gjeldende().beregnRevurdering(grunnlag).shouldBeRight()

        beregning.månedsresultater.values.forEach {
            it as HistoriskInfotrygdRevurdertMånedsresultat.Ytelse
            it.beløp shouldBe minstegrense
        }
        beregning.månedsresultater.mapValues { it.value.benyttetRegel } shouldBe forventetRegeltre(
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
            it.opphørsgrunn shouldBe manueltOpphør.opphørsgrunn
            it.manueltOpphør shouldBe true
        }
        beregning.månedsresultater.mapValues { it.value.benyttetRegel } shouldBe forventetRegeltre(
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
    fun `knytter ulike manuelle opphørsgrunner til riktig måned`() {
        val januarGrunn = Opphørsgrunn.FORMUE
        val februarGrunn = Opphørsgrunn.UTENLANDSOPPHOLD
        val januarGrunnlag = grunnlag(januar).copy(manueltOpphør = HistoriskInfotrygdManueltOpphør(januarGrunn))
        val februarGrunnlag = grunnlag(februar).copy(manueltOpphør = HistoriskInfotrygdManueltOpphør(februarGrunn))
        val beregning = gjeldende().beregnRevurdering(listOf(januarGrunnlag, februarGrunnlag)).shouldBeRight()
        val resultatregel = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_MANUELT_OPPHØR

        beregning.månedsresultater.mapValues { (it.value as HistoriskInfotrygdRevurdertMånedsresultat.Opphør).opphørsgrunn } shouldBe
            mapOf(januar to januarGrunn, februar to februarGrunn)
        beregning.månedsresultater.mapValues { it.value.benyttetRegel } shouldBe
            forventetRegeltre(
                grunnlag = listOf(januarGrunnlag),
                resultatregel = resultatregel,
                beregnetBeløp = forventetMånedssats,
                resultatBeløp = BigDecimal.ZERO,
                manuellOpphørsgrunn = januarGrunn,
            ) + forventetRegeltre(
                grunnlag = listOf(februarGrunnlag),
                resultatregel = resultatregel,
                beregnetBeløp = forventetMånedssats,
                resultatBeløp = BigDecimal.ZERO,
                manuellOpphørsgrunn = februarGrunn,
            )
    }

    @Test
    fun `enslig får ikke fradrag for EPS`() {
        val epsInntekt = 20_000.0
        val beregning = gjeldende().beregnRevurdering(
            listOf(
                grunnlag(januar, epsFradrag(januar, Fradragstype.Arbeidsinntekt, epsInntekt)),
                grunnlag(februar, epsFradrag(februar, Fradragstype.Arbeidsinntekt, epsInntekt)),
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
        val epsInntekt = 1_000
        val beregning = gjeldende().beregnRevurdering(
            listOf(januar, februar).map { måned ->
                grunnlag(måned, epsFradrag(måned, Fradragstype.Arbeidsinntekt, epsInntekt.toDouble()))
                    .copy(satskategori = HistoriskInfotrygdSatskategori.EU)
            },
        ).shouldBeRight()

        beregning.månedsresultater.values.forEach {
            it as HistoriskInfotrygdRevurdertMånedsresultat.Ytelse
            it.beløp shouldBeEqualIgnoringScale it.sats - BigDecimal(epsInntekt)
        }
    }

    private fun forventetRegeltre(
        grunnlag: List<HistoriskInfotrygdBeregningsgrunnlagForMåned>,
        resultatregel: Regelspesifiseringer,
        beregnetBeløp: BigDecimal,
        resultatBeløp: BigDecimal,
        manuellOpphørsgrunn: Opphørsgrunn? = null,
    ): Map<Måned, Regelspesifisering> {
        val satsregel = forventetSatsregel(
            kategori = HistoriskInfotrygdSatskategori.EN,
            månedssats = forventetMånedssats,
            årsbeløp = BigDecimal(191_422),
        )
        return grunnlag.associate { månedsgrunnlag ->
            månedsgrunnlag.måned to resultatregel.benyttRegelspesifisering(
                verdi = resultatBeløp.toPlainString(),
                avhengigeRegler = listOfNotNull(
                    Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_MÅNEDSBEREGNING.benyttRegelspesifisering(
                        verdi = beregnetBeløp.toPlainString(),
                        avhengigeRegler = listOf(
                            Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_SATS_MINUS_FRADRAG.benyttRegelspesifisering(
                                verdi = beregnetBeløp.toPlainString(),
                                avhengigeRegler = listOf(
                                    satsregel,
                                    RegelspesifisertGrunnlag.GRUNNLAG_FRADRAG.benyttGrunnlag(månedsgrunnlag.fradrag.toString()),
                                ),
                            ),
                            Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_MINSTEGRENSE.benyttRegelspesifisering(
                                verdi = minstegrense.toPlainString(),
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
        }
    }

    private fun forventetSatsregel(
        kategori: HistoriskInfotrygdSatskategori,
        månedssats: BigDecimal,
        årsbeløp: BigDecimal,
    ) = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_SATS.benyttRegelspesifisering(
        verdi = månedssats.toPlainString(),
        avhengigeRegler = listOf(
            RegelspesifisertGrunnlag.GRUNNLAG_HISTORISK_INFOTRYGD_SATSKATEGORI.benyttGrunnlag(kategori.name),
            RegelspesifisertGrunnlag.GRUNNLAG_HISTORISK_INFOTRYGD_SATSVERDI
                .benyttGrunnlag(HistoriskInfotrygdSatsverdi.Årsbeløp(årsbeløp).toString()),
        ),
    )

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
        val år = 2020
        val januar = januar(år)
        val februar = februar(år)
        val minstegrense = BigDecimal("319.04")

        // 191 422 / 12 = 15 951,83, avrundet til hele kroner
        const val FORVENTET_MÅNEDSSATS_I_KRONER = 15_952.0
        val forventetMånedssats: BigDecimal = BigDecimal(FORVENTET_MÅNEDSSATS_I_KRONER.toInt())
        val STØNAD_ID = HistoriskStønadId(1)
        val VEDTAK_ID = HistoriskVedtakId(2)
        const val OPPDRAG_ID = "oppdrag-1"
    }
}
