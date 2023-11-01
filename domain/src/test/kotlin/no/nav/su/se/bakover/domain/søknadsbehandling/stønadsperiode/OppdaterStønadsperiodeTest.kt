package no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.extensions.april
import no.nav.su.se.bakover.common.extensions.august
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.juli
import no.nav.su.se.bakover.common.extensions.juni
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.extensions.mars
import no.nav.su.se.bakover.common.extensions.november
import no.nav.su.se.bakover.common.extensions.oktober
import no.nav.su.se.bakover.common.extensions.september
import no.nav.su.se.bakover.common.extensions.toPeriode
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.YearRange
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
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.sak.nySøknadsbehandling
import no.nav.su.se.bakover.domain.sak.oppdaterSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.StøtterIkkeOverlappendeStønadsperioder
import no.nav.su.se.bakover.domain.søknadsbehandling.beregnetAvslag
import no.nav.su.se.bakover.domain.søknadsbehandling.beregnetInnvilget
import no.nav.su.se.bakover.domain.søknadsbehandling.opprettet
import no.nav.su.se.bakover.domain.søknadsbehandling.simulert
import no.nav.su.se.bakover.domain.søknadsbehandling.underkjentAvslagBeregning
import no.nav.su.se.bakover.domain.søknadsbehandling.underkjentAvslagVilkår
import no.nav.su.se.bakover.domain.søknadsbehandling.underkjentInnvilget
import no.nav.su.se.bakover.domain.søknadsbehandling.vilkårsvurdertAvslag
import no.nav.su.se.bakover.domain.søknadsbehandling.vilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.formuegrenserFactoryTestPåDato
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nySøknadsbehandlingUtenStønadsperiode
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.søknad.nySøknadJournalførtMedOppgave
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.vedtakRevurdering
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import no.nav.su.se.bakover.test.vilkårsvurdertSøknadsbehandling
import org.junit.jupiter.api.Test
import java.time.Year

internal class OppdaterStønadsperiodeTest {

    private val aldersvurdering = Aldersvurdering.Vurdert(
        maskinellVurdering = MaskinellAldersvurderingMedGrunnlagsdata.avgjørBasertPåFødselsdatoEllerFødselsår(
            stønadsperiode2021,
            person().fødsel,
        ),
        saksbehandlersAvgjørelse = null,
        aldersinformasjon = Aldersinformasjon.createAldersinformasjon(
            person(),
            fixedClock,
        ),
    )

    @Test
    fun `lovlige overganger`() {
        listOf(
            opprettet,
            vilkårsvurdertInnvilget,
            vilkårsvurdertAvslag,
            beregnetInnvilget,
            beregnetAvslag,
            simulert,
            underkjentAvslagVilkår,
            underkjentAvslagBeregning,
            underkjentInnvilget,
        ).forEach {
            it.oppdaterStønadsperiode(
                aldersvurdering = aldersvurdering,
                formuegrenserFactory = formuegrenserFactoryTestPåDato(),
                clock = fixedClock,
                saksbehandler = saksbehandler,
            ).shouldBeRight()
        }
    }

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
            hentPerson = { person().right() },
            saksbehandlersAvgjørelse = null,
        ).getOrFail().second

        vilkårsvurdert.periode shouldNotBe nyPeriode
        actual.periode shouldBe nyPeriode
        actual.vilkårsvurderinger.uføreVilkår().getOrFail().grunnlag.first().periode shouldBe nyPeriode
        actual.vilkårsvurderinger.formue.grunnlag.first().periode shouldBe nyPeriode
        actual.grunnlagsdata.bosituasjon.first().periode shouldBe nyPeriode
    }

    @Test
    fun `innvilget stønadsperioder skal ikke kunne overlappe`() {
        val clock = TikkendeKlokke()
        val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(periode = år(2021)),
            clock = clock,
        )

        val opprettetSøknadsbehandling = nySøknadsbehandlingUtenStønadsperiode(
            clock = clock,
            sakOgSøknad = sak to nySøknadJournalførtMedOppgave(
                sakId = sak.id,
                fnr = sak.fnr,
            ),
        ).second

        sak.nySøknadsbehandling(opprettetSøknadsbehandling).let {
            val nyPeriode = Periode.create(1.desember(2021), 31.mars(2022))

            it.oppdaterStønadsperiodeForSøknadsbehandling(
                søknadsbehandlingId = opprettetSøknadsbehandling.id,
                stønadsperiode = Stønadsperiode.create(nyPeriode),
                clock = fixedClock,
                formuegrenserFactory = formuegrenserFactoryTestPåDato(),
                saksbehandler = saksbehandler,
                hentPerson = { person().right() },
                saksbehandlersAvgjørelse = null,
            ) shouldBe Sak.KunneIkkeOppdatereStønadsperiode.OverlappendeStønadsperiode(
                StøtterIkkeOverlappendeStønadsperioder.StønadsperiodeOverlapperMedIkkeOpphørtStønadsperiode,
            ).left()
        }
    }

    @Test
    fun `stønadsperioder skal ikke kunne legges forut for eksisterende stønadsperioder`() {
        val clock = TikkendeKlokke()
        val (sakEtterFørstePeriode, _) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(periode = år(2021)),
            clock = clock,
        )
        val (sakEtterAndrePeriode, _) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(periode = år(2023)),
            clock = clock,
            sakOgSøknad = sakEtterFørstePeriode to nySøknadJournalførtMedOppgave(
                sakId = sakEtterFørstePeriode.id,
                fnr = sakEtterFørstePeriode.fnr,
            ),
        )
        val (sakEtterTredjePeriode, mellomToAndrePerioder) = nySøknadsbehandlingUtenStønadsperiode(
            clock = clock,
            sakOgSøknad = sakEtterAndrePeriode to nySøknadJournalførtMedOppgave(
                sakId = sakEtterAndrePeriode.id,
                fnr = sakEtterAndrePeriode.fnr,
            ),
        )

        sakEtterTredjePeriode.oppdaterSøknadsbehandling(mellomToAndrePerioder).let {
            val nyPeriode = Stønadsperiode.create(periode = år(2022))

            it.oppdaterStønadsperiodeForSøknadsbehandling(
                søknadsbehandlingId = mellomToAndrePerioder.id,
                stønadsperiode = nyPeriode,
                clock = fixedClock,
                formuegrenserFactory = formuegrenserFactoryTestPåDato(),
                saksbehandler = saksbehandler,
                hentPerson = { person().right() },
                saksbehandlersAvgjørelse = null,
            ) shouldBe Sak.KunneIkkeOppdatereStønadsperiode.OverlappendeStønadsperiode(
                StøtterIkkeOverlappendeStønadsperioder.StønadsperiodeForSenerePeriodeEksisterer,
            ).left()
        }
    }

    @Test
    fun `stønadsperioder skal kunne overlappe med perioder som førte til feilutbetaling `() {
        val tikkendeKlokke = TikkendeKlokke()
        val (sakMedSøknadsbehandlingsvedtak, søknadsbehandlingVedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
            clock = tikkendeKlokke,
            stønadsperiode = stønadsperiode2021,
        )
        val sakId = sakMedSøknadsbehandlingsvedtak.id
        val fnr = sakMedSøknadsbehandlingsvedtak.fnr
        val revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021))
        val (sakMedSøknadsbehandlingsOgRevurderingsvedtak, _) = vedtakRevurdering(
            clock = tikkendeKlokke,
            revurderingsperiode = revurderingsperiode,
            sakOgVedtakSomKanRevurderes = sakMedSøknadsbehandlingsvedtak to søknadsbehandlingVedtak,
            vilkårOverrides = listOf(
                avslåttUførevilkårUtenGrunnlag(
                    opprettet = Tidspunkt.now(tikkendeKlokke),
                    periode = revurderingsperiode,
                ),
            ),
            // Genererer feiltutbetaling for mai og juni.
            utbetalingerKjørtTilOgMed = { 1.juli(2021) },
        )

        val nyPeriode = år(2022)
        val nyStønadsperiode = Stønadsperiode.create(nyPeriode)
        val (sakMedNySøknadsbehandling, nySøknadsbehandling) = vilkårsvurdertSøknadsbehandling(
            clock = tikkendeKlokke,
            sakOgSøknad = sakMedSøknadsbehandlingsOgRevurderingsvedtak to nySøknadJournalførtMedOppgave(
                sakId = sakId,
                clock = tikkendeKlokke,
                fnr = fnr,
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
                hentPerson = { person().right() },
                saksbehandlersAvgjørelse = null,
            ).shouldBeRight()
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
                hentPerson = { person().right() },
                saksbehandlersAvgjørelse = null,
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
                hentPerson = { person().right() },
                saksbehandlersAvgjørelse = null,
            ).shouldBeRight()
        }
    }

    @Test
    fun `stønadsperiode som kun går innenfor et år, gir en YearRange for kun det året`() {
        Stønadsperiode.create(år(2021)).toYearRange() shouldBe YearRange(Year.of(2021), Year.of(2021))
    }

    @Test
    fun `stønadsperiode som krysser over 2 år, gir en YearRange på start og slutt året`() {
        Stønadsperiode.create((1.mai(2021)..30.april(2022)).toPeriode()).toYearRange() shouldBe
            YearRange(Year.of(2021), Year.of(2022))
    }
}
