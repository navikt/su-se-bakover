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
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskStønadId
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtakId
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import satser.domain.historisk.HistoriskInfotrygdSats
import satser.domain.historisk.HistoriskInfotrygdSatskategori
import satser.domain.historisk.HistoriskInfotrygdSatsverdi
import satser.domain.supplerendestønad.SatsFactoryForSupplerendeStønad
import vilkår.inntekt.domain.grunnlag.FradragForMåned
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
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
        ).copy(bosituasjon = HistoriskInfotrygdRevurderingsbosituasjon.EPS_OVER_67)
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
                                        RegelspesifisertGrunnlag.GRUNNLAG_BOSITUASJON.benyttGrunnlag(
                                            HistoriskInfotrygdRevurderingsbosituasjon.EPS_OVER_67.name,
                                        ),
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
                bosituasjon = HistoriskInfotrygdRevurderingsbosituasjon.ENSLIG,
                sats = forventetMånedssats,
                fradrag = januarGrunnlag.fradrag,
                benyttetRegel = forventetEnRegel,
            ),
            februar to HistoriskInfotrygdRevurdertMånedsresultat.Ytelse(
                måned = februar,
                opprinneligStønadId = STØNAD_ID,
                opprinneligVedtakId = VEDTAK_ID,
                oppdragId = OPPDRAG_ID,
                bosituasjon = HistoriskInfotrygdRevurderingsbosituasjon.EPS_OVER_67,
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
                bosituasjon = HistoriskInfotrygdRevurderingsbosituasjon.ENSLIG,
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
                ).copy(bosituasjon = HistoriskInfotrygdRevurderingsbosituasjon.EPS_OVER_67)
            },
        ).shouldBeRight()

        beregning.månedsresultater.values.forEach {
            it as HistoriskInfotrygdRevurdertMånedsresultat.Ytelse
            it.sats shouldBe epsSats
            it.beløp shouldBeEqualIgnoringScale epsSats - BigDecimal(600)
        }
    }

    @Test
    fun `EPS ufør flyktning bruker ordinære satser fra januar 2021 og trekker bare inntekt over fribeløpet`() {
        val desember2020 = Måned.fra(YearMonth.of(2020, 12))
        val januar2021 = Måned.fra(YearMonth.of(2021, 1))
        val satser = SatsFactoryForSupplerendeStønad().gjeldende(januar2021.fraOgMed)
        val ordinærAlder = satser.ordinærAlder(januar2021)
        val ordinærUføre = satser.ordinærUføre(januar2021)
        val ordinærAlderssats = ordinærAlder.satsForMåned
        val ordinærUføresats = ordinærUføre.satsForMåned
        val brukerfradrag = FradragForMåned(
            fradragstype = Fradragstype.Arbeidsinntekt,
            månedsbeløp = 100.0,
            måned = januar2021,
            tilhører = FradragTilhører.BRUKER,
        )
        val grunnlag = listOf(
            HistoriskInfotrygdBeregningsgrunnlagForMåned(
                måned = desember2020,
                bosituasjon = HistoriskInfotrygdRevurderingsbosituasjon.EPS_UNDER_67,
                fradrag = listOf(epsFradrag(desember2020, Fradragstype.Arbeidsinntekt, 1_000.0)),
            ),
            HistoriskInfotrygdBeregningsgrunnlagForMåned(
                måned = januar2021,
                bosituasjon = HistoriskInfotrygdRevurderingsbosituasjon.EPS_UNDER_67_UFØR_FLYKTNING,
                fradrag = listOf(
                    brukerfradrag,
                    epsFradrag(januar2021, Fradragstype.Arbeidsinntekt, 15_000.0),
                ),
            ),
        )
        val beregning = gjeldendeFor(desember2020, januar2021).beregnRevurdering(
            grunnlag = grunnlag,
            satsFactory = satser,
        ).shouldBeRight()
        val desemberresultat = beregning.månedsresultater.getValue(desember2020)
            as HistoriskInfotrygdRevurdertMånedsresultat.Ytelse
        val januarresultat = beregning.månedsresultater.getValue(januar2021)
            as HistoriskInfotrygdRevurdertMånedsresultat.Ytelse

        desemberresultat.sumFradrag shouldBeEqualIgnoringScale BigDecimal(1_000)
        januarresultat.bosituasjon shouldBe HistoriskInfotrygdRevurderingsbosituasjon.EPS_UNDER_67_UFØR_FLYKTNING
        januarresultat.sats shouldBe ordinærAlderssats
        januarresultat.beløp shouldBeEqualIgnoringScale
            (ordinærAlderssats - BigDecimal(100)).setScale(0, RoundingMode.HALF_UP)
        januarresultat.epsFribeløp shouldBe ordinærUføresats
        januarresultat.epsFradrag shouldBeEqualIgnoringScale BigDecimal.ZERO
        januarresultat.sumFradrag shouldBeEqualIgnoringScale BigDecimal(100)

        val epsFribeløpsregel = januarresultat.benyttetRegel.beregninger().single {
            it.kode == Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_EPS_FRIBELØP.kode
        }
        epsFribeløpsregel.verdi shouldBe ordinærUføresats.toPlainString()
        epsFribeløpsregel.avhengigeRegler shouldBe listOf(ordinærUføre.sats.benyttetRegel)

        val underFribeløp = listOf(
            epsFradrag(januar2021, Fradragstype.Arbeidsinntekt, ordinærUføresats.toDouble() - 1.0),
        )
        val likFribeløp = listOf(
            epsFradrag(januar2021, Fradragstype.Arbeidsinntekt, ordinærUføresats.toDouble()),
        )
        val overFribeløpMedSosialstønad = listOf(
            epsFradrag(januar2021, Fradragstype.Arbeidsinntekt, ordinærUføresats.toDouble() + 75.0),
            epsFradrag(januar2021, Fradragstype.Sosialstønad, 30.0),
        )

        listOf(
            underFribeløp to BigDecimal.ZERO,
            likFribeløp to BigDecimal.ZERO,
            overFribeløpMedSosialstønad to BigDecimal(105),
        ).forEach { (epsFradrag, forventetFradrag) ->
            val resultat = gjeldendeFor(januar2021).beregnRevurdering(
                grunnlag = listOf(
                    HistoriskInfotrygdBeregningsgrunnlagForMåned(
                        måned = januar2021,
                        bosituasjon = HistoriskInfotrygdRevurderingsbosituasjon.EPS_UNDER_67_UFØR_FLYKTNING,
                        fradrag = epsFradrag,
                    ),
                ),
                satsFactory = satser,
            ).shouldBeRight().månedsresultater.getValue(januar2021)
                as HistoriskInfotrygdRevurdertMånedsresultat.Ytelse

            resultat.epsFradrag shouldBeEqualIgnoringScale forventetFradrag
        }

        val regelkoder = januarresultat.benyttetRegel.regelspesifiseringer()
        setOf(
            Regelspesifiseringer.REGEL_BEREGN_SATS_ALDER_MÅNED.kode,
            Regelspesifiseringer.REGEL_BEREGN_SATS_UFØRE_MÅNED.kode,
            Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_EPS_FRIBELØP.kode,
            Regelspesifiseringer.REGEL_FRADRAG_EPS_OVER_FRIBELØP.kode,
            Regelspesifiseringer.REGEL_SATS_MINUS_FRADRAG_AVRUNDET.kode,
        ).all { it in regelkoder } shouldBe true
        val forventetBrukerfradrag = BigDecimal.valueOf(brukerfradrag.månedsbeløp)
        val epsFradragsregel = Regelspesifiseringer.REGEL_FRADRAG_EPS_OVER_FRIBELØP.benyttRegelspesifisering(
            verdi = forventetBrukerfradrag.toPlainString(),
            avhengigeRegler = listOf(
                RegelspesifisertGrunnlag.GRUNNLAG_BOSITUASJON.benyttGrunnlag(
                    HistoriskInfotrygdRevurderingsbosituasjon.EPS_UNDER_67_UFØR_FLYKTNING.name,
                ),
                RegelspesifisertGrunnlag.GRUNNLAG_FRADRAG.benyttGrunnlag(grunnlag.last().fradrag.toString()),
                epsFribeløpsregel,
            ),
        )
        val forventetSatsMinusFradrag =
            Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_SATS_MINUS_FRADRAG.benyttRegelspesifisering(
                verdi = (ordinærAlderssats - forventetBrukerfradrag).toPlainString(),
                avhengigeRegler = listOf(
                    ordinærAlder.sats.benyttetRegel,
                    epsFradragsregel,
                ),
            )
        val forventetAvrundetBeløp =
            (ordinærAlderssats - forventetBrukerfradrag).setScale(0, RoundingMode.HALF_UP)
        val forventetAvrundetSatsMinusFradrag =
            Regelspesifiseringer.REGEL_SATS_MINUS_FRADRAG_AVRUNDET.benyttRegelspesifisering(
                verdi = forventetAvrundetBeløp.toPlainString(),
                avhengigeRegler = listOf(forventetSatsMinusFradrag),
            )
        val ensligSats = requireNotNull(
            HistoriskInfotrygdSats.beregnMånedssats(
                dato = januar2021.fraOgMed,
                kategori = HistoriskInfotrygdSatskategori.EN,
            ),
        )
        val forventetRegeltre = Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_YTELSE.benyttRegelspesifisering(
            verdi = forventetAvrundetBeløp.toPlainString(),
            avhengigeRegler = listOf(
                Regelspesifiseringer.REGEL_HISTORISK_INFOTRYGD_MÅNEDSBEREGNING.benyttRegelspesifisering(
                    verdi = forventetAvrundetBeløp.toPlainString(),
                    avhengigeRegler = listOf(
                        forventetAvrundetSatsMinusFradrag,
                        HistoriskInfotrygdMinstegrense.beregn(ensligSats).benyttetRegel,
                    ),
                ),
            ),
        )
        januarresultat.benyttetRegel shouldBe forventetRegeltre
    }

    @Test
    fun `EPS ufør flyktning kan ikke velges før januar 2021`() {
        val desember2020 = Måned.fra(YearMonth.of(2020, 12))
        gjeldendeFor(desember2020).beregnRevurdering(
            listOf(
                HistoriskInfotrygdBeregningsgrunnlagForMåned(
                    måned = desember2020,
                    bosituasjon = HistoriskInfotrygdRevurderingsbosituasjon.EPS_UNDER_67_UFØR_FLYKTNING,
                    fradrag = emptyList(),
                ),
            ),
        ).shouldBeLeft() shouldBe
            KunneIkkeBeregneHistoriskInfotrygdRevurdering.EpsUførFlyktningFørJanuar2021(desember2020)
    }

    @Test
    fun `EPS ufør flyktning bruker satser som var kjent på beregningstidspunktet`() {
        val mai2021 = Måned.fra(YearMonth.of(2021, 5))
        val satserVedBeregning = SatsFactoryForSupplerendeStønad().gjeldende(LocalDate.of(2021, 6, 1))
        val satsVedMånedensStart = SatsFactoryForSupplerendeStønad().gjeldende(mai2021.fraOgMed)

        val resultat = gjeldendeFor(mai2021).beregnRevurdering(
            grunnlag = listOf(
                HistoriskInfotrygdBeregningsgrunnlagForMåned(
                    måned = mai2021,
                    bosituasjon = HistoriskInfotrygdRevurderingsbosituasjon.EPS_UNDER_67_UFØR_FLYKTNING,
                    fradrag = emptyList(),
                ),
            ),
            satsFactory = satserVedBeregning,
        ).shouldBeRight().månedsresultater.getValue(mai2021)
            as HistoriskInfotrygdRevurdertMånedsresultat.Ytelse

        resultat.sats shouldBe satserVedBeregning.ordinærAlder(mai2021).satsForMåned
        (resultat.sats > satsVedMånedensStart.ordinærAlder(mai2021).satsForMånedAvrundet.toBigDecimal()) shouldBe true
    }

    @Test
    fun `EPS under 67 trekkes fullt ut`() {
        val epsInntekt = 1_000
        val beregning = gjeldende().beregnRevurdering(
            listOf(januar, februar).map { måned ->
                grunnlag(måned, epsFradrag(måned, Fradragstype.Arbeidsinntekt, epsInntekt.toDouble()))
                    .copy(bosituasjon = HistoriskInfotrygdRevurderingsbosituasjon.EPS_UNDER_67)
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

    private fun Regelspesifisering.regelspesifiseringer(): Set<String> = when (this) {
        is Regelspesifisering.Beregning -> setOf(kode) + avhengigeRegler.flatMap {
            it.regelspesifiseringer()
        }
        is Regelspesifisering.Grunnlag -> setOf(kode)
        Regelspesifisering.BeregnetUtenSpesifisering -> emptySet()
    }

    private fun Regelspesifisering.beregninger(): List<Regelspesifisering.Beregning> = when (this) {
        is Regelspesifisering.Beregning -> listOf(this) + avhengigeRegler.flatMap { it.beregninger() }
        is Regelspesifisering.Grunnlag,
        Regelspesifisering.BeregnetUtenSpesifisering,
        -> emptyList()
    }

    private fun gjeldende() = GjeldendeHistoriskInfotrygdVedtaksdata(
        projeksjonId = UUID.randomUUID(),
        periode = Periode.create(januar.fraOgMed, februar.tilOgMed),
        tidslinje = linkedMapOf(
            januar to gammelYtelse(januar),
            februar to gammelYtelse(februar),
        ),
    )

    private fun gjeldendeFor(vararg måneder: Måned) = GjeldendeHistoriskInfotrygdVedtaksdata(
        projeksjonId = UUID.randomUUID(),
        periode = Periode.create(måneder.first().fraOgMed, måneder.last().tilOgMed),
        tidslinje = måneder.associateTo(linkedMapOf()) { it to gammelYtelse(it) },
    )

    private fun grunnlag(
        måned: Måned,
        vararg fradrag: FradragForMåned,
    ) = HistoriskInfotrygdBeregningsgrunnlagForMåned(
        måned = måned,
        bosituasjon = HistoriskInfotrygdRevurderingsbosituasjon.ENSLIG,
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
            bosituasjon = HistoriskInfotrygdRevurderingsbosituasjon.ENSLIG,
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
