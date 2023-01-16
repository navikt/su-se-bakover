package no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode

import arrow.core.left
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.april
import no.nav.su.se.bakover.common.periode.august
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.juli
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.common.periode.november
import no.nav.su.se.bakover.common.periode.oktober
import no.nav.su.se.bakover.common.periode.september
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.søknadsbehandling.StøtterIkkeOverlappendeStønadsperioder
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.FastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.FlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.FormueVilkår
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.LovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.PersonligOppmøteVilkår
import no.nav.su.se.bakover.domain.vilkår.UføreVilkår
import no.nav.su.se.bakover.domain.vilkår.UtenlandsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.formuegrenserFactoryTestPåDato
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.søknad.nySøknadJournalførtMedOppgave
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.test.vedtakRevurdering
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.vilkår.tilstrekkeligDokumentert
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdAvslag
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import no.nav.su.se.bakover.test.vilkårsvurdertSøknadsbehandling
import org.junit.jupiter.api.Test

internal class OppdaterStønadsperiodeTest {

    @Test
    fun `oppdaterer perioden riktig`() {
        val (sak, vilkårsvurdert) = søknadsbehandlingVilkårsvurdertInnvilget()

        val nyPeriode = Periode.create(1.februar(2022), 31.mars(2022))
        val actual = sak.oppdaterStønadsperiodeForSøknadsbehandling(
            søknadsbehandlingId = vilkårsvurdert.id,
            stønadsperiode = Stønadsperiode.create(nyPeriode),
            formuegrenserFactory = formuegrenserFactoryTestPåDato(),
            clock = fixedClock,
            saksbehandler = saksbehandler,
        ).getOrFail().second

        vilkårsvurdert.periode shouldNotBe nyPeriode
        actual.periode shouldBe nyPeriode
        actual.vilkårsvurderinger.uføreVilkår().getOrFail().grunnlag.first().periode shouldBe nyPeriode
        actual.vilkårsvurderinger.formue.grunnlag.first().periode shouldBe nyPeriode
        actual.grunnlagsdata.bosituasjon.first().periode shouldBe nyPeriode
    }

    @Test
    fun `innvilget stønadsperioder skal ikke kunne overlappe`() {
        val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(periode = år(2021)),
        )

        val opprettetSøknadsbehandling = søknadsbehandlingVilkårsvurdertUavklart().second

        sak.copy(
            søknadsbehandlinger = sak.søknadsbehandlinger + opprettetSøknadsbehandling,
        ).let {
            val nyPeriode = Periode.create(1.desember(2021), 31.mars(2022))

            it.oppdaterStønadsperiodeForSøknadsbehandling(
                søknadsbehandlingId = opprettetSøknadsbehandling.id,
                stønadsperiode = Stønadsperiode.create(nyPeriode),
                clock = fixedClock,
                formuegrenserFactory = formuegrenserFactoryTestPåDato(),
                saksbehandler = saksbehandler,
            ) shouldBe Sak.KunneIkkeOppdatereStønadsperiode.OverlappendeStønadsperiode(
                StøtterIkkeOverlappendeStønadsperioder.StønadsperiodeOverlapperMedIkkeOpphørtStønadsperiode,
            ).left()
        }
    }

    @Test
    fun `stønadsperioder skal ikke kunne legges forut for eksisterende stønadsperioder`() {
        val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget()
        val (_, andreStønadsperiode) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(periode = år(2023)),
        )
        val mellomToAndrePerioder = søknadsbehandlingVilkårsvurdertUavklart().second

        sak.copy(
            søknadsbehandlinger = sak.søknadsbehandlinger + andreStønadsperiode.behandling + mellomToAndrePerioder,
            vedtakListe = sak.vedtakListe + andreStønadsperiode,
        ).let {
            val nyPeriode = Stønadsperiode.create(periode = år(2022))

            it.oppdaterStønadsperiodeForSøknadsbehandling(
                søknadsbehandlingId = mellomToAndrePerioder.id,
                stønadsperiode = nyPeriode,
                clock = fixedClock,
                formuegrenserFactory = formuegrenserFactoryTestPåDato(),
                saksbehandler = saksbehandler,
            ) shouldBe Sak.KunneIkkeOppdatereStønadsperiode.OverlappendeStønadsperiode(
                StøtterIkkeOverlappendeStønadsperioder.StønadsperiodeForSenerePeriodeEksisterer,
            ).left()
        }
    }

    @Test
    fun `stønadsperioder skal ikke kunne overlappe med perioder som førte til avkorting`() {
        val tikkendeKlokke = TikkendeKlokke()

        val (sakMedSøknadVedtak, søknadsbehandlingVedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
            clock = tikkendeKlokke,
        )
        val revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021))
        val (sakMedRevurderingOgSøknadVedtak, _) = vedtakRevurdering(
            clock = tikkendeKlokke,
            revurderingsperiode = revurderingsperiode,
            sakOgVedtakSomKanRevurderes = sakMedSøknadVedtak to søknadsbehandlingVedtak,
            vilkårOverrides = listOf(
                utenlandsoppholdAvslag(
                    periode = revurderingsperiode,
                ),
            ),
            utbetalingerKjørtTilOgMed = 1.juli(2021),
        )

        val nyPeriode = år(2022)
        val nyStønadsperiode = Stønadsperiode.create(nyPeriode)
        val (_, nySøknadsbehandling) = søknadsbehandlingVilkårsvurdertUavklart(
            clock = tikkendeKlokke,
            stønadsperiode = nyStønadsperiode,
        )

        val nySøknadsbehandlingMedOpplysningsplikt = nySøknadsbehandling.leggTilOpplysningspliktVilkår(
            saksbehandler = nySøknadsbehandling.saksbehandler!!,
            opplysningspliktVilkår = tilstrekkeligDokumentert(periode = nyPeriode),
        ).getOrFail() as Søknadsbehandling.Vilkårsvurdert.Uavklart

        sakMedRevurderingOgSøknadVedtak.copy(
            søknadsbehandlinger = sakMedRevurderingOgSøknadVedtak.søknadsbehandlinger + nySøknadsbehandling,
        ).let { sak ->
            sak.oppdaterStønadsperiodeForSøknadsbehandling(
                søknadsbehandlingId = nySøknadsbehandlingMedOpplysningsplikt.id,
                stønadsperiode = nyStønadsperiode,
                clock = tikkendeKlokke,
                formuegrenserFactory = formuegrenserFactoryTestPåDato(),
                saksbehandler = saksbehandler,
            ).getOrFail().second shouldBe nySøknadsbehandlingMedOpplysningsplikt

            listOf(
                1.mai(2021),
                1.juni(2021),
            ).map {
                Stønadsperiode.create(Periode.create(fraOgMed = it, tilOgMed = 31.desember(2021)))
            }.forEach { stønadsperiode ->
                sak.oppdaterStønadsperiodeForSøknadsbehandling(
                    søknadsbehandlingId = nySøknadsbehandlingMedOpplysningsplikt.id,
                    stønadsperiode = stønadsperiode,
                    clock = tikkendeKlokke,
                    formuegrenserFactory = formuegrenserFactoryTestPåDato(),
                    saksbehandler = saksbehandler,
                ) shouldBe Sak.KunneIkkeOppdatereStønadsperiode.OverlappendeStønadsperiode(
                    StøtterIkkeOverlappendeStønadsperioder.StønadsperiodeInneholderAvkortingPgaUtenlandsopphold,
                ).left()
            }

            listOf(
                1.juli(2021),
                1.august(2021),
                1.september(2021),
                1.oktober(2021),
                1.november(2021),
                1.desember(2021),
            ).map {
                Stønadsperiode.create(Periode.create(fraOgMed = it, tilOgMed = 31.desember(2021)))
            }.forEach { stønadsperiode ->
                sak.oppdaterStønadsperiodeForSøknadsbehandling(
                    søknadsbehandlingId = nySøknadsbehandlingMedOpplysningsplikt.id,
                    stønadsperiode = stønadsperiode,
                    clock = tikkendeKlokke,
                    formuegrenserFactory = formuegrenserFactoryTestPåDato(),
                    saksbehandler = saksbehandler,
                ).getOrFail().second shouldBe nySøknadsbehandlingMedOpplysningsplikt.copy(
                    stønadsperiode = stønadsperiode,
                    vilkårsvurderinger = Vilkårsvurderinger.Søknadsbehandling.Uføre(
                        formue = FormueVilkår.IkkeVurdert,
                        utenlandsopphold = UtenlandsoppholdVilkår.IkkeVurdert,
                        opplysningsplikt = tilstrekkeligDokumentert(periode = stønadsperiode.periode),
                        lovligOpphold = LovligOppholdVilkår.IkkeVurdert,
                        fastOpphold = FastOppholdINorgeVilkår.IkkeVurdert,
                        institusjonsopphold = InstitusjonsoppholdVilkår.IkkeVurdert,
                        personligOppmøte = PersonligOppmøteVilkår.IkkeVurdert,
                        flyktning = FlyktningVilkår.IkkeVurdert,
                        uføre = UføreVilkår.IkkeVurdert,
                    ),
                )
            }
        }
    }

    @Test
    fun `stønadsperioder skal ikke kunne overlappe med perioder som førte til feilutbetaling `() {
        val tikkendeKlokke = TikkendeKlokke()
        val (sakMedSøknadVedtak, søknadsbehandlingVedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
            clock = tikkendeKlokke,
            stønadsperiode = stønadsperiode2021,
        )
        val sakId = sakMedSøknadVedtak.id
        val revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021))
        val (sakMedRevurderingOgSøknadVedtak, _) = vedtakRevurdering(
            clock = tikkendeKlokke,
            revurderingsperiode = revurderingsperiode,
            sakOgVedtakSomKanRevurderes = sakMedSøknadVedtak to søknadsbehandlingVedtak,
            vilkårOverrides = listOf(
                avslåttUførevilkårUtenGrunnlag(
                    opprettet = Tidspunkt.now(tikkendeKlokke),
                    periode = revurderingsperiode,
                ),
            ),
            // Genererer feiltutbetaling for mai og juni.
            utbetalingerKjørtTilOgMed = 1.juli(2021),
        )

        val nyPeriode = år(2022)
        val nyStønadsperiode = Stønadsperiode.create(nyPeriode)
        val (sakMedNySøknadsbehandling, nySøknadsbehandling) = vilkårsvurdertSøknadsbehandling(
            clock = tikkendeKlokke,
            sakOgSøknad = sakMedRevurderingOgSøknadVedtak to nySøknadJournalførtMedOppgave(
                sakId = sakId,
                clock = tikkendeKlokke,
            ),
            stønadsperiode = nyStønadsperiode,
        )
        listOf(
            mai(2021),
            juni(2021),
        ).forEach { periode ->
            sakMedNySøknadsbehandling.oppdaterStønadsperiodeForSøknadsbehandling(
                søknadsbehandlingId = nySøknadsbehandling.id,
                stønadsperiode = Stønadsperiode.create(periode),
                clock = tikkendeKlokke.copy(),
                formuegrenserFactory = formuegrenserFactoryTestPåDato(),
                saksbehandler = saksbehandler,
            ) shouldBe Sak.KunneIkkeOppdatereStønadsperiode.OverlappendeStønadsperiode(
                StøtterIkkeOverlappendeStønadsperioder.StønadsperiodeInneholderFeilutbetaling,
            ).left()
        }

        listOf(
            januar(2021),
            februar(2021),
            mars(2021),
            april(2021),
        ).forEach { periode ->
            sakMedNySøknadsbehandling.oppdaterStønadsperiodeForSøknadsbehandling(
                søknadsbehandlingId = nySøknadsbehandling.id,
                stønadsperiode = Stønadsperiode.create(periode),
                clock = tikkendeKlokke.copy(),
                formuegrenserFactory = formuegrenserFactoryTestPåDato(),
                saksbehandler = saksbehandler,
            ) shouldBe Sak.KunneIkkeOppdatereStønadsperiode.OverlappendeStønadsperiode(
                StøtterIkkeOverlappendeStønadsperioder.StønadsperiodeOverlapperMedIkkeOpphørtStønadsperiode,
            ).left()
        }

        listOf(
            juli(2021),
            august(2021),
            september(2021),
            oktober(2021),
            november(2021),
            desember(2021),
        ).forEach { periode ->
            sakMedNySøknadsbehandling.oppdaterStønadsperiodeForSøknadsbehandling(
                søknadsbehandlingId = nySøknadsbehandling.id,
                stønadsperiode = Stønadsperiode.create(periode),
                clock = tikkendeKlokke.copy(),
                formuegrenserFactory = formuegrenserFactoryTestPåDato(),
                saksbehandler = saksbehandler,
            ).shouldBeRight()
        }
    }
}
