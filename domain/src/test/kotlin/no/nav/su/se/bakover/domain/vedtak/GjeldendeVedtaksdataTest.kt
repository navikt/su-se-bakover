package no.nav.su.se.bakover.domain.vedtak

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.periode2021
import no.nav.su.se.bakover.test.utenlandsoppholdAvslag
import no.nav.su.se.bakover.test.vedtakRevurdering
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test

internal class GjeldendeVedtaksdataTest {
    @Test
    fun `finner gjeldende vedtak for gitt dato`() {
        val (sak, førstegangsvedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(
                Periode.create(1.januar(2021), 31.desember(2021)), "",
            ),
        )
        val revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021))
        val (_, revurderingsVedtak) = vedtakRevurdering(
            revurderingsperiode = revurderingsperiode,
            sakOgVedtakSomKanRevurderes = sak to førstegangsvedtak,
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = revurderingsperiode,
                    arbeidsinntekt = 5000.0,
                    tilhører = FradragTilhører.BRUKER,
                )
            )
        )
        val data = GjeldendeVedtaksdata(
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            vedtakListe = nonEmptyListOf(
                førstegangsvedtak,
                revurderingsVedtak,
            ),
            clock = fixedClock,
        )
        data.gjeldendeVedtakPåDato(1.januar(2021)) shouldBe førstegangsvedtak
        data.gjeldendeVedtakPåDato(30.april(2021)) shouldBe førstegangsvedtak
        data.gjeldendeVedtakPåDato(1.mai(2021)) shouldBe revurderingsVedtak
        data.gjeldendeVedtakPåDato(1.desember(2021)) shouldBe revurderingsVedtak
        data.tidslinjeForVedtakErSammenhengende() shouldBe true
    }

    @Test
    fun `tidslinje inneholder hull mellom to vedtak`() {
        val (sak, førstegangsvedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(
                Periode.create(1.januar(2021), 31.mars(2021)), "",
            ),
        )

        val (_, nyStønadsperiode) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(
                Periode.create(1.mai(2021), 31.desember(2021)), "",
            ),
        )

        sak.copy(
            søknadsbehandlinger = sak.søknadsbehandlinger + nyStønadsperiode.behandling,
            vedtakListe = sak.vedtakListe + nyStønadsperiode,
        ).let {
            val data = GjeldendeVedtaksdata(
                periode = Periode.create(1.januar(2021), 31.desember(2021)),
                vedtakListe = nonEmptyListOf(
                    førstegangsvedtak as VedtakSomKanRevurderes,
                    nyStønadsperiode as VedtakSomKanRevurderes,
                ),
                clock = fixedClock,
            )
            data.gjeldendeVedtakPåDato(1.mars(2021)) shouldBe førstegangsvedtak
            data.gjeldendeVedtakPåDato(1.april(2021)) shouldBe null
            data.gjeldendeVedtakPåDato(1.desember(2021)) shouldBe nyStønadsperiode
            data.tidslinjeForVedtakErSammenhengende() shouldBe false
        }
    }

    @Test
    fun `håndterer at etterspurt periode ikke inneholder vedtaksdata`() {
        val (_, førstegangsvedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(
                Periode.create(1.januar(2021), 31.mars(2021)), "",
            ),
        )
        val data = GjeldendeVedtaksdata(
            periode = Periode.create(1.mai(2021), 31.desember(2021)),
            vedtakListe = nonEmptyListOf(førstegangsvedtak as VedtakSomKanRevurderes),
            clock = fixedClock,
        )
        data.gjeldendeVedtakPåDato(1.mai(2021)) shouldBe null
        data.grunnlagsdata shouldBe Grunnlagsdata.IkkeVurdert
        data.vilkårsvurderinger shouldBe Vilkårsvurderinger.Revurdering.IkkeVurdert
    }

    @Test
    fun `tidslinje inneholder bare et vedtak`() {
        val (_, førstegangsvedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(
                Periode.create(1.januar(2021), 31.desember(2021)), "",
            ),
        )

        val data = GjeldendeVedtaksdata(
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            vedtakListe = nonEmptyListOf(førstegangsvedtak as VedtakSomKanRevurderes),
            clock = fixedClock,
        )
        data.tidslinjeForVedtakErSammenhengende() shouldBe true
    }

    @Test
    fun `gjeldende vedtaksdata inngholder utbetalinger som skal avkortes`() {
        val (sak, _) = vedtakRevurdering(
            clock = TikkendeKlokke(),
            revurderingsperiode = periode2021,
            vilkårOverrides = listOf(
                utenlandsoppholdAvslag(
                    periode = periode2021,
                ),
            ),
        )

        GjeldendeVedtaksdata(
            periode = periode2021,
            vedtakListe = NonEmptyList.fromListUnsafe(sak.vedtakListe.filterIsInstance<VedtakSomKanRevurderes>()),
            clock = fixedClock,
        ).let {
            it.inneholderOpphørsvedtakMedAvkortingUtenlandsopphold() shouldBe true
        }
    }
}
