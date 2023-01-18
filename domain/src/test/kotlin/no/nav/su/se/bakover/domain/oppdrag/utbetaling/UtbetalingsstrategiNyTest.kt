package no.nav.su.se.bakover.domain.oppdrag.utbetaling

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.fixedClock
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.april
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.juli
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.beregning.Beregningsperiode
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsstrategi
import no.nav.su.se.bakover.domain.oppdrag.hentOversendteUtbetalingerUtenFeil
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.beregnetRevurdering
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlag.uføregrunnlag
import no.nav.su.se.bakover.test.iverksattRevurdering
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simuleringNy
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.vedtakIverksattGjenopptakAvYtelseFraIverksattStans
import no.nav.su.se.bakover.test.vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

internal class UtbetalingsstrategiNyTest {
    private object BeregningMedTomMånedsbereninger : Beregning {
        override fun getId(): UUID = mock()
        override fun getOpprettet(): Tidspunkt = mock()
        override fun getMånedsberegninger(): List<Månedsberegning> = emptyList()
        override fun getFradrag(): List<Fradrag> = emptyList()
        override fun getSumYtelse(): Int = 1000
        override fun getSumFradrag(): Double = 1000.0
        override fun getBegrunnelse(): String = mock()
        override fun equals(other: Any?): Boolean = mock()
        override val periode: Periode = Periode.create(
            1.juni(2021),
            30.november(2021),
        )
    }

    @Test
    fun `ingen eksisterende utbetalinger`() {
        nyUtbetaling(
            uføregrunnlag = listOf(
                uføregrunnlag(
                    periode = januar(2020).rangeTo(april(2020)),
                    uføregrad = Uføregrad.parse(50),
                ),
            ),
            beregning = createBeregning(
                1.januar(2020),
                30.april(2020),
            ),
            eksisterendeUtbetalinger = listOf(),
        ).also {
            it shouldBe expectedUtbetaling(
                actual = it,
                utbetalingslinjer = nonEmptyListOf(
                    Utbetalingslinje.Ny(
                        id = it.utbetalingslinjer.single().id,
                        opprettet = fixedTidspunkt,
                        fraOgMed = 1.januar(2020),
                        tilOgMed = 30.april(2020),
                        forrigeUtbetalingslinjeId = null,
                        beløp = 20637,
                        uføregrad = Uføregrad.parse(50),
                        utbetalingsinstruksjonForEtterbetalinger = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                    ),
                ),
            )
        }
    }

    @Test
    fun `ny utbetaling med eksisterende utbetalinger`() {
        val uføregrunnlagListe = listOf(
            uføregrunnlag(
                periode = januar(2020).rangeTo(juni(2020)),
                forventetInntekt = 0,
                uføregrad = Uføregrad.parse(50),
            ),
            uføregrunnlag(
                periode = juli(2020).rangeTo(desember(2020)),
                forventetInntekt = 0,
                uføregrad = Uføregrad.parse(100),
            ),
        )

        val eksisterendeUtbetalinger = listOf(
            kvittertUtbetaling(
                uføregrunnlag = uføregrunnlagListe,
                beregning = createBeregning(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.mars(2020),
                ),
                eksisterendeUtbetalinger = emptyList(),
            ),
        )

        nyUtbetaling(
            uføregrunnlag = uføregrunnlagListe,
            beregning = createBeregning(
                fraOgMed = 1.mars(2020),
                tilOgMed = 31.desember(2020),
            ),
            eksisterendeUtbetalinger = eksisterendeUtbetalinger,
        ).also {
            it shouldBe expectedUtbetaling(
                actual = it,
                utbetalingslinjer = nonEmptyListOf(
                    expectedUtbetalingslinje(
                        utbetalingslinjeId = it.utbetalingslinjer[0].id,
                        opprettet = it.utbetalingslinjer[0].opprettet,
                        fraOgMed = 1.mars(2020),
                        tilOgMed = 30.april(2020),
                        beløp = 20637,
                        forrigeUtbetalingslinjeId = eksisterendeUtbetalinger.single().utbetalingslinjer.single().id,
                        uføregrad = Uføregrad.parse(50),
                    ),
                    expectedUtbetalingslinje(
                        utbetalingslinjeId = it.utbetalingslinjer[1].id,
                        opprettet = it.utbetalingslinjer[1].opprettet,
                        fraOgMed = 1.mai(2020),
                        tilOgMed = 30.juni(2020),
                        beløp = 20946,
                        forrigeUtbetalingslinjeId = it.utbetalingslinjer[0].id,
                        uføregrad = Uføregrad.parse(50),
                    ),
                    expectedUtbetalingslinje(
                        utbetalingslinjeId = it.utbetalingslinjer[2].id,
                        opprettet = it.utbetalingslinjer[2].opprettet,
                        fraOgMed = 1.juli(2020),
                        tilOgMed = 31.desember(2020),
                        beløp = 20946,
                        forrigeUtbetalingslinjeId = it.utbetalingslinjer[1].id,
                        uføregrad = Uføregrad.parse(100),
                    ),
                ),
            )
        }
    }

    @Test
    fun `oppretter kobling mot nyeste oversendte utbetaling uten feil ved opprettelse av nye utbetalinger`() {
        val tikkendeKlokke = TikkendeKlokke(fixedClock)

        val uføregrunnlagListe = listOf(
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = januar(2000).rangeTo(desember(2050)),
                uføregrad = Uføregrad.parse(50),
                forventetInntekt = 0,
            ),
        )

        val first = kvittertUtbetaling(
            uføregrunnlag = uføregrunnlagListe,
            beregning = createBeregning(
                fraOgMed = 1.januar(2020),
                tilOgMed = 31.mars(2020),
            ),
            eksisterendeUtbetalinger = emptyList(),
            clock = tikkendeKlokke,
        )

        val second = kvittertUtbetaling(
            uføregrunnlag = uføregrunnlagListe,
            beregning = createBeregning(
                fraOgMed = 1.januar(2020),
                tilOgMed = 31.mars(2020),
            ),
            eksisterendeUtbetalinger = listOf(first),
            kvittering = Kvittering(
                Kvittering.Utbetalingsstatus.FEIL,
                "",
                mottattTidspunkt = fixedTidspunkt,
            ),
            clock = tikkendeKlokke,
        )

        val third = kvittertUtbetaling(
            uføregrunnlag = uføregrunnlagListe,
            beregning = createBeregning(
                fraOgMed = 1.januar(2020),
                tilOgMed = 31.mars(2020),
            ),
            eksisterendeUtbetalinger = listOf(
                first,
                second,
            ),
            kvittering = Kvittering(
                Kvittering.Utbetalingsstatus.OK_MED_VARSEL,
                "",
                mottattTidspunkt = fixedTidspunkt,
            ),
            clock = tikkendeKlokke,
        )

        val fourth = kvittertUtbetaling(
            uføregrunnlag = uføregrunnlagListe,
            beregning = createBeregning(
                fraOgMed = 1.januar(2020),
                tilOgMed = 31.mars(2020),
            ),
            eksisterendeUtbetalinger = listOf(
                first,
                second,
                third,
            ),
            kvittering = Kvittering(
                Kvittering.Utbetalingsstatus.FEIL,
                "",
                mottattTidspunkt = fixedTidspunkt,
            ),
            clock = tikkendeKlokke,
        )

        val utbetalinger = listOf(
            first,
            second,
            third,
            fourth,
        )
        utbetalinger.hentOversendteUtbetalingerUtenFeil()[1] shouldBe third

        nyUtbetaling(
            uføregrunnlag = uføregrunnlagListe,
            beregning = createBeregning(
                fraOgMed = 1.januar(2020),
                tilOgMed = 31.mars(2020),
            ),
            eksisterendeUtbetalinger = utbetalinger,
            clock = tikkendeKlokke,
        ).also {
            it shouldBe expectedUtbetaling(
                actual = it,
                utbetalingslinjer = nonEmptyListOf(
                    expectedUtbetalingslinje(
                        utbetalingslinjeId = it.utbetalingslinjer.single().id,
                        opprettet = it.utbetalingslinjer.single().opprettet,
                        fraOgMed = 1.januar(2020),
                        tilOgMed = 31.mars(2020),
                        beløp = 20637,
                        forrigeUtbetalingslinjeId = third.utbetalingslinjer.single().id,
                        uføregrad = Uføregrad.parse(50),
                    ),
                ),
            )
        }
    }

    @Test
    fun `perioder som har likt beløp, men ikke tilstøter hverandre får separate utbetalingsperioder`() {
        nyUtbetaling(
            uføregrunnlag = listOf(
                uføregrunnlag(
                    periode = januar(2020).rangeTo(januar(2020)),
                    uføregrad = Uføregrad.parse(10),
                ),
                uføregrunnlag(
                    periode = februar(2020).rangeTo(april(2020)),
                    uføregrad = Uføregrad.parse(70),
                ),
            ),
            beregning = BeregningFactory(clock = fixedClock).ny(
                fradrag = listOf(
                    FradragFactory.nyFradragsperiode(
                        fradragstype = Fradragstype.ForventetInntekt,
                        månedsbeløp = 1000.0,
                        periode = Periode.create(
                            fraOgMed = 1.januar(2020),
                            tilOgMed = 30.april(2020),
                        ),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                    FradragFactory.nyFradragsperiode(
                        fradragstype = Fradragstype.Arbeidsinntekt,
                        månedsbeløp = 4000.0,
                        periode = februar(2020),
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    ),
                ),
                beregningsperioder = listOf(
                    Beregningsperiode(
                        periode = Periode.create(
                            1.januar(2020),
                            30.april(2020),
                        ),
                        strategy = BeregningStrategy.BorAlene(
                            satsFactoryTestPåDato(),
                            Sakstype.UFØRE,
                        ),
                    ),
                ),
            ),
            eksisterendeUtbetalinger = listOf(),
        ).also {
            it shouldBe expectedUtbetaling(
                actual = it,
                utbetalingslinjer = nonEmptyListOf(
                    Utbetalingslinje.Ny(
                        id = it.utbetalingslinjer[0].id,
                        opprettet = it.utbetalingslinjer[0].opprettet,
                        fraOgMed = 1.januar(2020),
                        tilOgMed = 31.januar(2020),
                        forrigeUtbetalingslinjeId = null,
                        beløp = 19637,
                        uføregrad = Uføregrad.parse(10),
                    ),
                    Utbetalingslinje.Ny(
                        id = it.utbetalingslinjer[1].id,
                        opprettet = it.utbetalingslinjer[1].opprettet,
                        fraOgMed = 1.februar(2020),
                        tilOgMed = 29.februar(2020),
                        forrigeUtbetalingslinjeId = it.utbetalingslinjer[0].id,
                        beløp = 16637,
                        uføregrad = Uføregrad.parse(70),
                    ),
                    Utbetalingslinje.Ny(
                        id = it.utbetalingslinjer[2].id,
                        opprettet = it.utbetalingslinjer[2].opprettet,
                        fraOgMed = 1.mars(2020),
                        tilOgMed = 30.april(2020),
                        forrigeUtbetalingslinjeId = it.utbetalingslinjer[1].id,
                        beløp = it.utbetalingslinjer[0].beløp,
                        uføregrad = Uføregrad.parse(70),
                    ),
                ),
            )
        }
    }

    @Test
    fun `kaster exception hvis månedsberegning og uføregrunnlag er tomme (0 til 0)`() {
        shouldThrow<RuntimeException> {
            nyUtbetaling(
                uføregrunnlag = emptyList(),
                beregning = BeregningMedTomMånedsbereninger,
                eksisterendeUtbetalinger = listOf(),
            )
        }
    }

    @Test
    fun `kaster exception hvis månedsberegning er tom, men finnes uføregrunnlag (0 til 1)`() {
        shouldThrow<RuntimeException> {
            nyUtbetaling(
                uføregrunnlag = listOf(
                    uføregrunnlag(
                        periode = januar(2021).rangeTo(mai(2021)),
                    ),
                ),
                beregning = BeregningMedTomMånedsbereninger,
                eksisterendeUtbetalinger = listOf(),
            )
        }
    }

    @Test
    fun `kaster exception hvis månedsberegning er tom, men finnes flere uføregrunnlag (0 til mange)`() {
        shouldThrow<RuntimeException> {
            nyUtbetaling(
                uføregrunnlag = listOf(
                    uføregrunnlag(
                        periode = januar(2021).rangeTo(mai(2021)),
                    ),
                    uføregrunnlag(
                        periode = juni(2021).rangeTo(desember(2021)),
                    ),
                ),
                beregning = BeregningMedTomMånedsbereninger,
                eksisterendeUtbetalinger = listOf(),
            )
        }
    }

    @Test
    fun `kaster exception hvis det finnes månedsberegning, men uføregrunnlag er tom (1 til 0)`() {
        shouldThrow<Utbetalingsstrategi.UtbetalingStrategyException> {
            nyUtbetaling(
                uføregrunnlag = listOf(),
                beregning = createBeregning(
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.desember(2021),
                ),
                eksisterendeUtbetalinger = listOf(),
            )
        }
    }

    @Test
    fun `kaster exception hvis uføregrunnalget ikke inneholder alle beregningsperiodene`() {
        assertThrows<Utbetalingsstrategi.UtbetalingStrategyException> {
            nyUtbetaling(
                uføregrunnlag = listOf(
                    uføregrunnlag(
                        periode = januar(2021).rangeTo(mai(2021)),
                    ),
                ),
                beregning = createBeregning(
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 31.desember(2021),
                ),
                eksisterendeUtbetalinger = listOf(),
            )
        }.also {
            it.message shouldContain "Uføregrunnlaget inneholder ikke alle beregningsperiodene. Grunnlagsperiodene:"
        }
    }

    @Test
    fun `rekonstruerer historikk for måneder senere enn nye utbetalinger`() {
        val tikkendeKlokke = TikkendeKlokke(1.januar(2022).fixedClock())
        val (sak, _, vedtak) = iverksattSøknadsbehandlingUføre(
            clock = tikkendeKlokke,
            stønadsperiode = stønadsperiode2021,
        )

        val (_, revurdering) = beregnetRevurdering(
            revurderingsperiode = januar(2021),
            sakOgVedtakSomKanRevurderes = sak to vedtak as VedtakSomKanRevurderes,
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = januar(2021),
                    arbeidsinntekt = 5000.0,
                ),
            ),
            clock = tikkendeKlokke,
        )

        nyUtbetaling(
            clock = tikkendeKlokke,
            uføregrunnlag = revurdering.vilkårsvurderinger.uføreVilkår().getOrFail().grunnlag,
            beregning = revurdering.beregning,
            eksisterendeUtbetalinger = sak.utbetalinger,
        ).let {
            it shouldBe expectedUtbetaling(
                actual = it,
                utbetalingslinjer = nonEmptyListOf(
                    Utbetalingslinje.Ny(
                        id = it.utbetalingslinjer[0].id,
                        opprettet = it.utbetalingslinjer[0].opprettet,
                        fraOgMed = 1.januar(2021),
                        tilOgMed = 31.januar(2021),
                        forrigeUtbetalingslinjeId = sak.utbetalinger.last().sisteUtbetalingslinje().id,
                        beløp = 15946,
                        uføregrad = Uføregrad.parse(100),
                    ),
                    Utbetalingslinje.Ny(
                        id = it.utbetalingslinjer[1].id,
                        opprettet = it.utbetalingslinjer[1].opprettet,
                        fraOgMed = 1.februar(2021),
                        tilOgMed = 30.april(2021),
                        forrigeUtbetalingslinjeId = it.utbetalingslinjer[0].id,
                        beløp = 20946,
                        uføregrad = Uføregrad.parse(100),
                    ),
                    Utbetalingslinje.Ny(
                        id = it.utbetalingslinjer[2].id,
                        opprettet = it.utbetalingslinjer[2].opprettet,
                        fraOgMed = 1.mai(2021),
                        tilOgMed = 31.desember(2021),
                        forrigeUtbetalingslinjeId = it.utbetalingslinjer[1].id,
                        beløp = 21989,
                        uføregrad = Uføregrad.parse(100),
                    ),
                ),
            )
        }
    }

    @Test
    fun `rekonstruerer historikk for måneder senere enn nye utbetalinger - stans`() {
        val tikkendeKlokke = TikkendeKlokke(1.februar(2021).fixedClock())
        val (sak, _, vedtak) = iverksattSøknadsbehandlingUføre(
            clock = tikkendeKlokke,
            stønadsperiode = stønadsperiode2021,
        )

        val (sak2, _) = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            periode = februar(2021).rangeTo(desember(2021)),
            sakOgVedtakSomKanRevurderes = sak to vedtak as VedtakSomKanRevurderes,
            clock = tikkendeKlokke,
        )

        val (sak3, revurdering) = beregnetRevurdering(
            revurderingsperiode = januar(2021),
            sakOgVedtakSomKanRevurderes = sak2 to vedtak,
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = januar(2021),
                    arbeidsinntekt = 5000.0,
                ),
            ),
            clock = tikkendeKlokke,
        )

        nyUtbetaling(
            clock = tikkendeKlokke,
            uføregrunnlag = revurdering.vilkårsvurderinger.uføreVilkår().getOrFail().grunnlag,
            beregning = revurdering.beregning,
            eksisterendeUtbetalinger = sak3.utbetalinger,
        ).let {
            it shouldBe expectedUtbetaling(
                actual = it,
                utbetalingslinjer = nonEmptyListOf(
                    Utbetalingslinje.Ny(
                        id = it.utbetalingslinjer[0].id,
                        opprettet = it.utbetalingslinjer[0].opprettet,
                        fraOgMed = 1.januar(2021),
                        tilOgMed = 31.januar(2021),
                        forrigeUtbetalingslinjeId = sak3.utbetalinger.last().sisteUtbetalingslinje().id,
                        beløp = 15946,
                        uføregrad = Uføregrad.parse(100),
                    ),
                    Utbetalingslinje.Ny(
                        id = it.utbetalingslinjer[1].id,
                        opprettet = it.utbetalingslinjer[1].opprettet,
                        fraOgMed = 1.februar(2021),
                        tilOgMed = 31.desember(2021),
                        forrigeUtbetalingslinjeId = it.utbetalingslinjer[0].id,
                        beløp = 20946,
                        uføregrad = Uføregrad.parse(100),
                    ),
                    Utbetalingslinje.Endring.Stans(
                        id = it.utbetalingslinjer[1].id,
                        opprettet = it.utbetalingslinjer[2].opprettet,
                        fraOgMed = 1.februar(2021),
                        tilOgMed = 31.desember(2021),
                        forrigeUtbetalingslinjeId = it.utbetalingslinjer[1].forrigeUtbetalingslinjeId,
                        virkningsperiode = Periode.create(1.februar(2021), 31.desember(2021)),
                        beløp = 20946,
                        uføregrad = Uføregrad.parse(100),
                    ),
                ),
            )
            it.utbetalingslinjer.map { it.id }.none { it in sak3.utbetalinger.flatMap { it.utbetalingslinjer }.map { it.id } } shouldBe true
        }
    }

    @Test
    fun `rekonstruerer historikk for måneder senere enn nye utbetalinger - opphør`() {
        val tikkendeKlokke = TikkendeKlokke(1.februar(2021).fixedClock())
        val (sak, _, vedtak) = iverksattSøknadsbehandlingUføre(
            clock = tikkendeKlokke,
            stønadsperiode = stønadsperiode2021,
        )

        val (sak2, _) = iverksattRevurdering(
            clock = tikkendeKlokke,
            revurderingsperiode = februar(2021).rangeTo(desember(2021)),
            sakOgVedtakSomKanRevurderes = sak to vedtak as VedtakSomKanRevurderes,
            vilkårOverrides = listOf(
                avslåttUførevilkårUtenGrunnlag(
                    periode = februar(2021).rangeTo(desember(2021)),
                ),
            ),
        )

        val (sak3, revurdering) = beregnetRevurdering(
            revurderingsperiode = januar(2021),
            sakOgVedtakSomKanRevurderes = sak2 to vedtak,
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = januar(2021),
                    arbeidsinntekt = 5000.0,
                ),
            ),
            clock = tikkendeKlokke,
        )

        nyUtbetaling(
            clock = tikkendeKlokke,
            uføregrunnlag = revurdering.vilkårsvurderinger.uføreVilkår().getOrFail().grunnlag,
            beregning = revurdering.beregning,
            eksisterendeUtbetalinger = sak3.utbetalinger,
        ).let {
            it shouldBe expectedUtbetaling(
                actual = it,
                utbetalingslinjer = nonEmptyListOf(
                    Utbetalingslinje.Ny(
                        id = it.utbetalingslinjer[0].id,
                        opprettet = it.utbetalingslinjer[0].opprettet,
                        fraOgMed = 1.januar(2021),
                        tilOgMed = 31.januar(2021),
                        forrigeUtbetalingslinjeId = sak3.utbetalinger.last().sisteUtbetalingslinje().id,
                        beløp = 15946,
                        uføregrad = Uføregrad.parse(100),
                    ),
                    Utbetalingslinje.Ny(
                        id = it.utbetalingslinjer[1].id,
                        opprettet = it.utbetalingslinjer[1].opprettet,
                        fraOgMed = 1.februar(2021),
                        tilOgMed = 31.desember(2021),
                        forrigeUtbetalingslinjeId = it.utbetalingslinjer[0].id,
                        beløp = 20946,
                        uføregrad = Uføregrad.parse(100),
                    ),
                    Utbetalingslinje.Endring.Opphør(
                        id = it.utbetalingslinjer[1].id,
                        opprettet = it.utbetalingslinjer[2].opprettet,
                        fraOgMed = 1.februar(2021),
                        tilOgMed = 31.desember(2021),
                        forrigeUtbetalingslinjeId = it.utbetalingslinjer[1].forrigeUtbetalingslinjeId,
                        virkningsperiode = Periode.create(1.februar(2021), 31.desember(2021)),
                        beløp = 20946,
                        uføregrad = Uføregrad.parse(100),
                    ),
                ),
            )
        }
    }

    @Test
    fun `rekonstruerer historikk for måneder senere enn nye utbetalinger - reaktivering`() {
        val tikkendeKlokke = TikkendeKlokke(1.februar(2021).fixedClock())
        val (sak, _, vedtak) = iverksattSøknadsbehandlingUføre(
            clock = tikkendeKlokke,
            stønadsperiode = stønadsperiode2021,
        )

        val (sak2, stans) = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            periode = februar(2021).rangeTo(desember(2021)),
            sakOgVedtakSomKanRevurderes = sak to vedtak as VedtakSomKanRevurderes,
            clock = tikkendeKlokke,
        )

        val (sak3, reak) = vedtakIverksattGjenopptakAvYtelseFraIverksattStans(
            periode = februar(2021).rangeTo(desember(2021)),
            sakOgVedtakSomKanRevurderes = sak2 to stans,
            clock = tikkendeKlokke,
        )

        val (sak4, revurdering) = beregnetRevurdering(
            revurderingsperiode = januar(2021),
            sakOgVedtakSomKanRevurderes = sak3 to reak,
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = januar(2021),
                    arbeidsinntekt = 5000.0,
                ),
            ),
            clock = tikkendeKlokke,
        )

        nyUtbetaling(
            clock = tikkendeKlokke,
            uføregrunnlag = revurdering.vilkårsvurderinger.uføreVilkår().getOrFail().grunnlag,
            beregning = revurdering.beregning,
            eksisterendeUtbetalinger = sak4.utbetalinger,
        ).let {
            it shouldBe expectedUtbetaling(
                actual = it,
                utbetalingslinjer = nonEmptyListOf(
                    Utbetalingslinje.Ny(
                        id = it.utbetalingslinjer[0].id,
                        opprettet = it.utbetalingslinjer[0].opprettet,
                        fraOgMed = 1.januar(2021),
                        tilOgMed = 31.januar(2021),
                        forrigeUtbetalingslinjeId = sak4.utbetalinger.last().sisteUtbetalingslinje().id,
                        beløp = 15946,
                        uføregrad = Uføregrad.parse(100),
                    ),
                    Utbetalingslinje.Ny(
                        id = it.utbetalingslinjer[1].id,
                        opprettet = it.utbetalingslinjer[1].opprettet,
                        fraOgMed = 1.februar(2021),
                        tilOgMed = 31.desember(2021),
                        forrigeUtbetalingslinjeId = it.utbetalingslinjer[0].id,
                        beløp = 20946,
                        uføregrad = Uføregrad.parse(100),
                    ),
                    Utbetalingslinje.Endring.Stans(
                        id = it.utbetalingslinjer[1].id,
                        opprettet = it.utbetalingslinjer[2].opprettet,
                        fraOgMed = 1.februar(2021),
                        tilOgMed = 31.desember(2021),
                        forrigeUtbetalingslinjeId = it.utbetalingslinjer[1].forrigeUtbetalingslinjeId,
                        virkningsperiode = Periode.create(1.februar(2021), 31.desember(2021)),
                        beløp = 20946,
                        uføregrad = Uføregrad.parse(100),
                    ),
                    Utbetalingslinje.Endring.Reaktivering(
                        id = it.utbetalingslinjer[1].id,
                        opprettet = it.utbetalingslinjer[3].opprettet,
                        fraOgMed = 1.februar(2021),
                        tilOgMed = 31.desember(2021),
                        forrigeUtbetalingslinjeId = it.utbetalingslinjer[1].forrigeUtbetalingslinjeId,
                        virkningsperiode = Periode.create(1.februar(2021), 31.desember(2021)),
                        beløp = 20946,
                        uføregrad = Uføregrad.parse(100),
                    ),
                ),
            )
        }
    }

    private fun expectedUtbetalingslinje(
        utbetalingslinjeId: UUID30,
        opprettet: Tidspunkt,
        fraOgMed: LocalDate,
        tilOgMed: LocalDate,
        beløp: Int,
        forrigeUtbetalingslinjeId: UUID30?,
        uføregrad: Uføregrad = Uføregrad.parse(50),
    ): Utbetalingslinje {
        return Utbetalingslinje.Ny(
            id = utbetalingslinjeId,
            opprettet = opprettet,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId,
            beløp = beløp,
            uføregrad = uføregrad,
        )
    }
}

fun createBeregning(
    fraOgMed: LocalDate,
    tilOgMed: LocalDate,
    fradrag: List<Fradrag> = emptyList(),
    clock: Clock = fixedClock,
): Beregning {
    return BeregningFactory(clock = clock).ny(
        fradrag = fradrag + (
            FradragFactory.nyFradragsperiode(
                fradragstype = Fradragstype.ForventetInntekt,
                månedsbeløp = 0.0,
                periode = Periode.create(
                    fraOgMed = fraOgMed,
                    tilOgMed = tilOgMed,
                ),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER,
            )
            ),
        beregningsperioder = listOf(
            Beregningsperiode(
                periode = Periode.create(
                    fraOgMed,
                    tilOgMed,
                ),
                strategy = BeregningStrategy.BorAlene(
                    satsFactoryTestPåDato(LocalDate.now(clock)),
                    Sakstype.UFØRE,
                ),
            ),
        ),
    )
}

fun nyUtbetaling(
    clock: Clock = fixedClock,
    uføregrunnlag: List<Grunnlag.Uføregrunnlag> = listOf(
        uføregrunnlag(periode = år(2021)),
    ),
    beregning: Beregning = createBeregning(
        fraOgMed = 1.januar(2021),
        tilOgMed = 31.desember(2021),
        fradrag = listOf(),
        clock = clock,
    ),
    eksisterendeUtbetalinger: List<Utbetaling> = emptyList(),
): Utbetaling.UtbetalingForSimulering {
    return Utbetalingsstrategi.NyUføreUtbetaling(
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        behandler = saksbehandler,
        beregning = beregning,
        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
        clock = clock,
        uføregrunnlag = uføregrunnlag,
        kjøreplan = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
        sakstype = Sakstype.UFØRE,
    ).generate()
}

fun oversendtUtbetaling(
    clock: Clock = fixedClock,
    uføregrunnlag: List<Grunnlag.Uføregrunnlag> = listOf(
        uføregrunnlag(periode = år(2021)),
    ),
    beregning: Beregning = createBeregning(
        fraOgMed = 1.januar(2021),
        tilOgMed = 31.desember(2021),
        fradrag = listOf(),
        clock = clock,
    ),
    eksisterendeUtbetalinger: List<Utbetaling.OversendtUtbetaling> = emptyList(),
    utbetalingsrequest: Utbetalingsrequest = Utbetalingsrequest(""),
): Utbetaling.OversendtUtbetaling.UtenKvittering {
    return nyUtbetaling(
        uføregrunnlag = uføregrunnlag,
        beregning = beregning,
        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
        clock = clock,
    ).toSimulertUtbetaling(
        simuleringNy(
            beregning = beregning,
            eksisterendeUtbetalinger = eksisterendeUtbetalinger,
            fnr = fnr,
            sakId = sakId,
            saksnummer = saksnummer,
            clock = fixedClock,
            uføregrunnlag = uføregrunnlag,
        ),
    ).toOversendtUtbetaling(
        oppdragsmelding = utbetalingsrequest,
    )
}

fun kvittertUtbetaling(
    clock: Clock = fixedClock,
    uføregrunnlag: List<Grunnlag.Uføregrunnlag> = listOf(
        uføregrunnlag(periode = år(2021)),
    ),
    beregning: Beregning = createBeregning(
        fraOgMed = 1.januar(2021),
        tilOgMed = 31.desember(2021),
        fradrag = listOf(),
        clock = clock,
    ),
    eksisterendeUtbetalinger: List<Utbetaling.OversendtUtbetaling> = emptyList(),
    utbetalingsrequest: Utbetalingsrequest = Utbetalingsrequest(""),
    kvittering: Kvittering = Kvittering(
        Kvittering.Utbetalingsstatus.OK,
        "",
        mottattTidspunkt = fixedTidspunkt,
    ),
): Utbetaling.OversendtUtbetaling.MedKvittering {
    return oversendtUtbetaling(
        uføregrunnlag = uføregrunnlag,
        beregning = beregning,
        eksisterendeUtbetalinger = eksisterendeUtbetalinger,
        utbetalingsrequest = utbetalingsrequest,
        clock = clock,
    ).toKvittertUtbetaling(
        kvittering = kvittering,
    )
}

fun expectedUtbetaling(
    actual: Utbetaling.UtbetalingForSimulering,
    utbetalingslinjer: NonEmptyList<Utbetalingslinje>,
): Utbetaling.UtbetalingForSimulering {
    return Utbetaling.UtbetalingForSimulering(
        id = actual.id,
        opprettet = actual.opprettet,
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalingslinjer = utbetalingslinjer,
        behandler = saksbehandler,
        avstemmingsnøkkel = actual.avstemmingsnøkkel,
        sakstype = Sakstype.UFØRE,
    )
}
