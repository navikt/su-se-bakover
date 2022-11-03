package no.nav.su.se.bakover.domain.vedtak

import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.fixedClock
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.søknadinnhold.Personopplysninger
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.søknad.nySøknadJournalførtMedOppgave
import no.nav.su.se.bakover.test.søknad.søknadinnhold
import no.nav.su.se.bakover.test.vedtakRevurdering
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdAvslag
import no.nav.su.se.bakover.test.vilkårsvurderingRevurderingIkkeVurdert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class GjeldendeVedtaksdataTest {
    @Test
    fun `finner gjeldende vedtak for gitt dato`() {
        val (sak, førstegangsvedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(år(2021)),
        )
        val revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021))
        val (sak2, revurderingsVedtak) = vedtakRevurdering(
            revurderingsperiode = revurderingsperiode,
            sakOgVedtakSomKanRevurderes = sak to førstegangsvedtak,
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = revurderingsperiode,
                    arbeidsinntekt = 5000.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )
        val data = sak2.kopierGjeldendeVedtaksdata(
            fraOgMed = førstegangsvedtak.periode.fraOgMed,
            clock = fixedClock,
        ).getOrFail()

        data.gjeldendeVedtakPåDato(1.januar(2021)) shouldBe førstegangsvedtak
        data.gjeldendeVedtakPåDato(30.april(2021)) shouldBe førstegangsvedtak
        data.gjeldendeVedtakPåDato(1.mai(2021)) shouldBe revurderingsVedtak
        data.gjeldendeVedtakPåDato(1.desember(2021)) shouldBe revurderingsVedtak
        data.tidslinjeForVedtakErSammenhengende() shouldBe true
        data.garantertSammenhengendePeriode() shouldBe år(2021)
        data.vedtaksperioder() shouldBe listOf(
            Periode.create(1.januar(2021), 30.april(2021)),
            Periode.create(1.mai(2021), 31.desember(2021)),
        )
        data.periodeFørsteTilOgMedSeneste() shouldBe år(2021)
    }

    @Test
    fun `tidslinje inneholder hull mellom to vedtak`() {
        val tikkendeKlokke = TikkendeKlokke(fixedClock)

        val (sak, _, førstegangsvedtak) = iverksattSøknadsbehandlingUføre(
            stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 31.mars(2021))),
            clock = tikkendeKlokke,
        )

        val (sak2, _, nyStønadsperiode) = iverksattSøknadsbehandlingUføre(
            sakOgSøknad = sak to nySøknadJournalførtMedOppgave(
                clock = tikkendeKlokke,
                sakId = sak.id,
                søknadInnhold = søknadinnhold(
                    personopplysninger = Personopplysninger(sak.fnr),
                ),
            ),
            stønadsperiode = Stønadsperiode.create(Periode.create(1.mai(2021), 31.desember(2021))),
            clock = tikkendeKlokke,
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
        data.vedtaksperioder() shouldBe listOf(
            Periode.create(1.januar(2021), 31.mars(2021)),
            Periode.create(1.mai(2021), 31.desember(2021)),
        )
        data.periodeFørsteTilOgMedSeneste() shouldBe år(2021)
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
        data.vedtaksperioder() shouldBe emptyList()
        assertThrows<NoSuchElementException> {
            data.periodeFørsteTilOgMedSeneste()
        }
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
    fun `gjeldende vedtaksdata inneholder utbetalinger som skal avkortes`() {
        val (sak, _) = vedtakRevurdering(
            revurderingsperiode = år(2021),
            vilkårOverrides = listOf(
                utenlandsoppholdAvslag(
                    periode = år(2021),
                ),
            ),
            clock = TikkendeKlokke(1.august(2021).fixedClock()),
            utbetalingerKjørtTilOgMed = 1.juli(2021),
        )

        GjeldendeVedtaksdata(
            periode = år(2021),
            vedtakListe = sak.vedtakListe.filterIsInstance<VedtakSomKanRevurderes>().toNonEmptyList(),
            clock = fixedClock,
        ).let {
            it.inneholderOpphørsvedtakMedAvkortingUtenlandsopphold() shouldBe true
            it.pågåendeAvkortingEllerBehovForFremtidigAvkorting shouldBe true
        }
    }
}
