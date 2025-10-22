package no.nav.su.se.bakover.domain.vedtak

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.tid.april
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.domain.tid.fixedClock
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.domain.tid.mars
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.august
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.juli
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.common.tid.periode.november
import no.nav.su.se.bakover.common.tid.periode.oktober
import no.nav.su.se.bakover.common.tid.periode.september
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.FnrWrapper
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlag.nyFradragsgrunnlag
import no.nav.su.se.bakover.test.iverksattRevurdering
import no.nav.su.se.bakover.test.iverksattSøknadsbehandling
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.shouldBeEqualToExceptId
import no.nav.su.se.bakover.test.søknad.nySøknadJournalførtMedOppgave
import no.nav.su.se.bakover.test.søknad.søknadinnholdUføre
import no.nav.su.se.bakover.test.vedtakRevurdering
import no.nav.su.se.bakover.test.vedtakRevurderingIverksattInnvilget
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdAvslag
import no.nav.su.se.bakover.test.vilkårsvurderingRevurderingIkkeVurdert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import vedtak.domain.VedtakSomKanRevurderes
import vilkår.inntekt.domain.grunnlag.FradragForPeriode
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.vurderinger.domain.Grunnlagsdata

internal class GjeldendeVedtaksdataTest {
    @Test
    fun `finner gjeldende vedtak for gitt dato`() {
        val clock = TikkendeKlokke()
        val stønadsperiodeFørstegangsvedtak = Stønadsperiode.create(år(2021))
        val fradragSøknadsbehandling = nyFradragsgrunnlag(periode = stønadsperiodeFørstegangsvedtak.periode)
        val (sak, førstegangsvedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(år(2021)),
            customGrunnlag = listOf(fradragSøknadsbehandling),
            clock = clock,
        )

        val revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021))
        val fradragRevurdering = fradragsgrunnlagArbeidsinntekt(
            periode = revurderingsperiode,
            arbeidsinntekt = 5000.0,
            tilhører = FradragTilhører.BRUKER,
        )
        val (sak2, revurderingsVedtak) = vedtakRevurdering(
            revurderingsperiode = revurderingsperiode,
            sakOgVedtakSomKanRevurderes = sak to førstegangsvedtak,
            grunnlagsdataOverrides = listOf(fradragRevurdering),
            clock = clock,
        )
        val data = sak2.kopierGjeldendeVedtaksdata(
            fraOgMed = førstegangsvedtak.periode.fraOgMed,
            clock = clock,
        ).getOrFail()

        data.gjeldendeVedtakPåDato(1.januar(2021)) shouldBe førstegangsvedtak
        data.gjeldendeVedtakPåDato(30.april(2021)) shouldBe førstegangsvedtak
        data.gjeldendeVedtakPåDato(1.mai(2021)) shouldBe revurderingsVedtak
        data.gjeldendeVedtakPåDato(1.desember(2021)) shouldBe revurderingsVedtak
        data.tidslinjeForVedtakErSammenhengende() shouldBe true
        data.garantertSammenhengendePeriode() shouldBe år(2021)
        data.vedtaksperioder shouldBe listOf(
            Periode.create(1.januar(2021), 30.april(2021)),
            Periode.create(1.mai(2021), 31.desember(2021)),
        )
        data.grunnlagsdata.fradragsgrunnlag.shouldBeEqualToExceptId(
            listOf(
                Fradragsgrunnlag.create(
                    id = fradragSøknadsbehandling.id,
                    opprettet = Tidspunkt.parse("2021-01-01T01:03:45.456789Z"),
                    fradrag = FradragForPeriode(
                        fradragstype = fradragSøknadsbehandling.fradragstype,
                        månedsbeløp = fradragSøknadsbehandling.månedsbeløp,
                        periode = januar(2021)..april(2021),
                        utenlandskInntekt = fradragSøknadsbehandling.utenlandskInntekt,
                        tilhører = fradragSøknadsbehandling.tilhører,
                    ),
                ),
                Fradragsgrunnlag.create(
                    id = fradragRevurdering.id,
                    opprettet = Tidspunkt.parse("2021-01-01T01:03:45.456789Z"),
                    fradrag = FradragForPeriode(
                        fradragstype = fradragRevurdering.fradragstype,
                        månedsbeløp = fradragRevurdering.månedsbeløp,
                        periode = mai(2021)..desember(2021),
                        utenlandskInntekt = fradragRevurdering.utenlandskInntekt,
                        tilhører = fradragRevurdering.tilhører,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `tidslinje inneholder hull mellom to vedtak`() {
        val tikkendeKlokke = TikkendeKlokke(fixedClock)
        val stønadsperiodeFørstegangsvedtak = Stønadsperiode.create(Periode.create(1.januar(2021), 31.mars(2021)))
        val fradragSøknadsbehandling = nyFradragsgrunnlag(periode = stønadsperiodeFørstegangsvedtak.periode)
        val (sak, _, førstegangsvedtak) = iverksattSøknadsbehandlingUføre(
            clock = tikkendeKlokke,
            stønadsperiode = stønadsperiodeFørstegangsvedtak,
            customGrunnlag = listOf(fradragSøknadsbehandling),
        )

        val stønadsperiodeNyPeriode = Stønadsperiode.create(Periode.create(1.mai(2021), 31.desember(2021)))
        val fradragRevurdering = nyFradragsgrunnlag(
            periode = stønadsperiodeNyPeriode.periode,
            månedsbeløp = 100.0,
            type = Fradragstype.Dagpenger,
        )
        val (sak2, _, nyStønadsperiode) = iverksattSøknadsbehandlingUføre(
            clock = tikkendeKlokke,
            stønadsperiode = stønadsperiodeNyPeriode,
            customGrunnlag = listOf(fradragRevurdering),
            sakOgSøknad = sak to nySøknadJournalførtMedOppgave(
                clock = tikkendeKlokke,
                sakId = sak.id,
                søknadInnhold = søknadinnholdUføre(
                    personopplysninger = FnrWrapper(sak.fnr),
                ),
            ),
        )
        val data = sak2.kopierGjeldendeVedtaksdata(
            fraOgMed = førstegangsvedtak.periode.fraOgMed,
            clock = tikkendeKlokke,
        ).getOrFail()

        data.gjeldendeVedtakPåDato(1.mars(2021)) shouldBe førstegangsvedtak
        data.gjeldendeVedtakPåDato(1.april(2021)) shouldBe null
        data.gjeldendeVedtakPåDato(1.desember(2021)) shouldBe nyStønadsperiode
        data.tidslinjeForVedtakErSammenhengende() shouldBe false
        assertThrows<IllegalStateException> {
            data.garantertSammenhengendePeriode()
        }
        data.vedtaksperioder shouldBe listOf(
            Periode.create(1.januar(2021), 31.mars(2021)),
            Periode.create(1.mai(2021), 31.desember(2021)),
        )
        data.grunnlagsdata.fradragsgrunnlag.shouldBeEqualToExceptId(
            listOf(
                nyFradragsgrunnlag(
                    periode = stønadsperiodeFørstegangsvedtak.periode,
                    månedsbeløp = fradragSøknadsbehandling.fradrag.månedsbeløp,
                    opprettet = Tidspunkt.parse("2021-01-01T01:03:30.456789Z"),
                    id = fradragSøknadsbehandling.id,
                    type = fradragSøknadsbehandling.fradragstype,
                ),
                nyFradragsgrunnlag(
                    periode = stønadsperiodeNyPeriode.periode,
                    månedsbeløp = fradragRevurdering.fradrag.månedsbeløp,
                    opprettet = Tidspunkt.parse("2021-01-01T01:03:30.456789Z"),
                    id = fradragRevurdering.id,
                    type = fradragRevurdering.fradragstype,
                ),
            ),
        )
    }

    @Test
    fun `håndterer at etterspurt periode ikke inneholder vedtaksdata`() {
        val (_, førstegangsvedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 31.mars(2021))),
        )
        val data = GjeldendeVedtaksdata(
            periode = Periode.create(1.mai(2021), 31.desember(2021)),
            vedtakListe = nonEmptyListOf(førstegangsvedtak),
            clock = fixedClock,
        )
        data.gjeldendeVedtakPåDato(1.mai(2021)) shouldBe null
        data.grunnlagsdata shouldBe Grunnlagsdata.IkkeVurdert
        data.vilkårsvurderinger shouldBe vilkårsvurderingRevurderingIkkeVurdert()
        assertThrows<IllegalStateException> {
            data.garantertSammenhengendePeriode()
        }
        data.vedtaksperioder shouldBe emptyList()
    }

    @Test
    fun `tidslinje inneholder bare et vedtak`() {
        val (_, førstegangsvedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(år(2021)),
        )

        val data = GjeldendeVedtaksdata(
            periode = år(2021),
            vedtakListe = nonEmptyListOf(førstegangsvedtak),
            clock = fixedClock,
        )
        data.tidslinjeForVedtakErSammenhengende() shouldBe true
    }

    @Test
    fun `gjeldendeVedtakMånedsvis - mapper riktig med hull`() {
        val revurderingsperiode = januar(2021)..februar(2021)
        // Dete vil trigge feilutbetaling for januar.
        val clock = TikkendeKlokke(1.februar(2021).fixedClock())
        val fradragRevurdering = nyFradragsgrunnlag(
            periode = revurderingsperiode,
            månedsbeløp = 5000.0,
            tilhører = FradragTilhører.BRUKER,
            type = Fradragstype.Dagpenger,
        )
        val (sakMedRevurderingsvedtak, revurderingsvedtak) = vedtakRevurdering(
            clock = clock,
            revurderingsperiode = revurderingsperiode,
            stønadsperiode = Stønadsperiode.create(januar(2021)..april(2021)),
            grunnlagsdataOverrides = listOf(fradragRevurdering),
            vilkårOverrides = listOf(
                // Merk at dette skal nå gi et vanlig opphør (siden vi har fjernet tilbakekreving under revurdering og avkorting)
                utenlandsoppholdAvslag(periode = revurderingsperiode),
            ),
        )
        val førsteSøknadsbehandlingsvedtak =
            sakMedRevurderingsvedtak.vedtakListe.first() as VedtakInnvilgetSøknadsbehandling
        val andreSøknadsbehandlingsPeriode = Stønadsperiode.create(juni(2021))
        val fradragAndreSøknadsbehandling = nyFradragsgrunnlag(
            periode = andreSøknadsbehandlingsPeriode.periode,
            månedsbeløp = 2000.0,
            tilhører = FradragTilhører.BRUKER,
            type = Fradragstype.Sosialstønad,
        )
        val (sakMedAndreSøknadsbehandlingsvedtak, _, andreSøknadsbehandlingsvedtak) = iverksattSøknadsbehandling(
            // Hopper over mai for å lage et hull
            stønadsperiode = andreSøknadsbehandlingsPeriode,
            customGrunnlag = listOf(fradragAndreSøknadsbehandling),
            sakOgSøknad = sakMedRevurderingsvedtak to nySøknadJournalførtMedOppgave(
                sakId = sakMedRevurderingsvedtak.id,
                fnr = sakMedRevurderingsvedtak.fnr,
                clock = clock,
            ),
            clock = clock,
        )
        GjeldendeVedtaksdata(
            periode = år(2021),
            vedtakListe = sakMedAndreSøknadsbehandlingsvedtak.vedtakListe.filterIsInstance<VedtakSomKanRevurderes>()
                .toNonEmptyList(),
            clock = clock,
        ).let {
            it.gjeldendeVedtakMånedsvisMedPotensielleHull() shouldBe mapOf(
                januar(2021) to revurderingsvedtak,
                februar(2021) to revurderingsvedtak,
                mars(2021) to førsteSøknadsbehandlingsvedtak,
                april(2021) to førsteSøknadsbehandlingsvedtak,
                juni(2021) to andreSøknadsbehandlingsvedtak,
            )
            it.grunnlagsdata.fradragsgrunnlag.shouldBeEqualToExceptId(
                listOf(
                    nyFradragsgrunnlag(
                        periode = revurderingsperiode,
                        månedsbeløp = 5000.0,
                        opprettet = Tidspunkt.parse("2021-02-01T00:01:37Z"),
                        id = fradragRevurdering.id,
                        type = fradragRevurdering.fradragstype,
                    ),
                    nyFradragsgrunnlag(
                        periode = juni(2021),
                        opprettet = Tidspunkt.parse("2021-02-01T00:01:37Z"),
                        månedsbeløp = fradragAndreSøknadsbehandling.månedsbeløp,
                        id = fradragAndreSøknadsbehandling.id,
                        type = fradragAndreSøknadsbehandling.fradragstype,
                    ),
                ),
            )
        }
    }

    /**
     * Per nå er grunnlagrt bare fradragsgrunnlag.
     * Burde utvides videre når vi legger til flere nye slåSammen() funksjoner.
     */
    @Test
    fun `slår sammen alle tilstøtende og like grunnlag`() {
        val (_, søknadsbehandlingJanuar) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create((januar(2021))),
            customGrunnlag = listOf(nyFradragsgrunnlag(periode = januar(2021), månedsbeløp = 400.0)),
        )
        val (_, søknadsbehandlingFebruar) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create((februar(2021))),
            customGrunnlag = listOf(
                nyFradragsgrunnlag(periode = februar(2021)),
                nyFradragsgrunnlag(periode = februar(2021)),
            ),
        )
        val (_, søknadsbehandlingMars) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create((mars(2021))),
            customGrunnlag = listOf(nyFradragsgrunnlag(periode = mars(2021), månedsbeløp = 400.0)),
        )

        GjeldendeVedtaksdata(
            periode = år(2021),
            vedtakListe = nonEmptyListOf(søknadsbehandlingJanuar, søknadsbehandlingFebruar, søknadsbehandlingMars),
            clock = fixedClock,
        ).grunnlagsdata.fradragsgrunnlag.shouldBeEqualToExceptId(
            listOf(
                nyFradragsgrunnlag(
                    periode = januar(2021)..mars(2021),
                    månedsbeløp = 400.0,
                    opprettet = fixedTidspunkt,
                ),
            ),
        )
    }

    @Test
    fun `slår sammen like & tilstøtende grunnlag ved init`() {
        val vedtak = vedtakSøknadsbehandlingIverksattInnvilget(
            customGrunnlag = listOf(
                nyFradragsgrunnlag(
                    periode = januar(2021)..juni(2021),
                    type = Fradragstype.Uføretrygd,
                    månedsbeløp = 500.0,
                ),
                nyFradragsgrunnlag(
                    periode = juli(2021)..desember(2021),
                    type = Fradragstype.Uføretrygd,
                    månedsbeløp = 500.0,
                ),
            ),
        ).second
        GjeldendeVedtaksdata(
            periode = år(2021),
            vedtakListe = nonEmptyListOf(vedtak),
            clock = fixedClock,
        ).let {
            it.grunnlagsdata.fradragsgrunnlag.size shouldBe 1
        }
    }

    @Test
    fun `slår sammen de opphørte periodene`() {
        val vedtakNyPeriode = vedtakSøknadsbehandlingIverksattInnvilget().second
        val vedtakOpphør1 = iverksattRevurdering(
            revurderingsperiode = juli(2021)..august(2021),
            grunnlagsdataOverrides = listOf(
                nyFradragsgrunnlag(månedsbeløp = 27000.0, periode = juli(2021)..august(2021)),
            ),
        ).fourth
        val vedtakRevurdering =
            vedtakRevurderingIverksattInnvilget(revurderingsperiode = september(2021)..oktober(2021)).second
        val vedtakOpphør2 = iverksattRevurdering(
            revurderingsperiode = november(2021)..desember(2021),
            grunnlagsdataOverrides = listOf(
                nyFradragsgrunnlag(månedsbeløp = 27000.0, periode = november(2021)..desember(2021)),
            ),
        ).fourth

        GjeldendeVedtaksdata(
            periode = år(2021),
            vedtakListe = nonEmptyListOf(vedtakNyPeriode, vedtakRevurdering, vedtakOpphør1, vedtakOpphør2),
            clock = fixedClock,
        ).opphørtePerioderSlåttSammen() shouldBe listOf(
            juli(2021)..august(2021),
            november(2021)..desember(2021),
        )
    }
}
