package no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.domain.tid.juli
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.domain.tid.mars
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
import no.nav.su.se.bakover.domain.revurdering.steg.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.sak.nySøknadsbehandling
import no.nav.su.se.bakover.domain.sak.oppdaterSøknadsbehandling
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
import no.nav.su.se.bakover.test.opprettetKlage
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.søknad.nySøknadJournalførtMedOppgave
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.vedtakRevurdering
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattAvslagMedBeregning
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import no.nav.su.se.bakover.test.vilkårsvurdertSøknadsbehandling
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Month
import java.time.Year
import java.time.YearMonth

internal class OppdaterStønadsperiodeTest {

    @Nested
    inner class Ufoere {
        private val aldersvurdering = Aldersvurdering.Vurdert(
            maskinellVurdering = MaskinellAldersvurderingMedGrunnlagsdata.avgjørBasertPåFødselsdatoEllerFødselsår(
                stønadsperiode2021,
                person().fødsel,
                saksType = Sakstype.UFØRE,
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
                ).shouldBeRight()
            }
        }

        /*
            Feks hvis man skal revurdere et avslag tilbake i tid der bruker har fått et innvilget vedtak i ettertid
            Så her har vi en revurderingsperiode for det avslåtte vedtaket som har en TOM som er før FOM for innvilgelsesperioden
         */
        @Test
        fun `stønadsperioder skal kunne legges forut for eksisterende stønadsperioder hvis periodens tom er før de andres fom`() {
            val clock = TikkendeKlokke()
            val avslagsperiode = Periode.create(
                fraOgMed = YearMonth.of(2023, Month.MAY).atDay(1),
                tilOgMed = YearMonth.of(2024, Month.APRIL).atEndOfMonth(),
            )

            /*
            Avslått pga høy inntekt, må vurdere om vi skal sende inn liste med vilkår man vil revurdere i frontend.
             */

            val (sakEtterFørstePeriode, _) = vedtakSøknadsbehandlingIverksattAvslagMedBeregning(
                stønadsperiode = Stønadsperiode.create(periode = avslagsperiode),
                clock = clock,
            )

            val innvilgelsesPeriode = Periode.create(
                fraOgMed = YearMonth.of(2023, Month.OCTOBER).atDay(1),
                tilOgMed = YearMonth.of(2024, Month.SEPTEMBER).atEndOfMonth(),
            )
            val (saketterInnvilgelseOgAvslag, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = Stønadsperiode.create(periode = innvilgelsesPeriode),
                clock = clock,
                sakOgSøknad = sakEtterFørstePeriode to nySøknadJournalførtMedOppgave(
                    sakId = sakEtterFørstePeriode.id,
                    fnr = sakEtterFørstePeriode.fnr,
                ),
            )

            val revurderingsPeriodeInnvilgelseEtterKA = Periode.create(
                fraOgMed = YearMonth.of(2023, Month.AUGUST).atDay(1),
                tilOgMed = YearMonth.of(2023, Month.SEPTEMBER).atEndOfMonth(),
            )

            val (sakogklage, klage) = opprettetKlage(sakMedVedtak = saketterInnvilgelseOgAvslag)
            /*
            her må vilkåret for inntekt ha endret seg da det er satt til 1 mill i
            vedtakSøknadsbehandlingIverksattAvslagMedBeregning by default. Dette gjøres manuelt av sb
            i beregnetRevurdering avgjøres det
             */
            val (sak, revurdering) = opprettetRevurdering(
                revurderingsperiode = revurderingsPeriodeInnvilgelseEtterKA,
                sakOgVedtakSomKanRevurderes = sakogklage to vedtak,
                revurderingsårsak = Revurderingsårsak(
                    Revurderingsårsak.Årsak.OMGJØRING_VEDTAK_FRA_KLAGEINSTANSEN,
                    Revurderingsårsak.Begrunnelse.create("LOL"),
                ),
            )
            // TODO: lag egen test med informasjonSomRevurderes og sjekk at de som velges finnes i det avslåtte vilkåret
            revurdering.informasjonSomRevurderes.any { it.key == Revurderingsteg.Inntekt }.shouldBeTrue()
            revurdering.periode shouldBe revurderingsPeriodeInnvilgelseEtterKA
            revurdering.revurderingsårsak.årsak shouldBe Revurderingsårsak.Årsak.OMGJØRING_VEDTAK_FRA_KLAGEINSTANSEN
        }

        @Test
        fun `stønadsperiode som legges tilbake i tid med en tom over en stønadsperiode for VedtakSomKanRevurderes`() {
            val clock = TikkendeKlokke()
            val avslagsperiode = Periode.create(
                fraOgMed = YearMonth.of(2023, Month.MAY).atDay(1),
                tilOgMed = YearMonth.of(2024, Month.APRIL).atEndOfMonth(),
            )
            val (sakEtterFørstePeriode, _) = vedtakSøknadsbehandlingIverksattAvslagMedBeregning(
                stønadsperiode = Stønadsperiode.create(periode = avslagsperiode),
                clock = clock,
            )

            val innvilgelsesPeriode = Periode.create(
                fraOgMed = YearMonth.of(2023, Month.OCTOBER).atDay(1),
                tilOgMed = YearMonth.of(2024, Month.SEPTEMBER).atEndOfMonth(),
            )
            val (sakEtterAndrePeriode, _) = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = Stønadsperiode.create(periode = innvilgelsesPeriode),
                clock = clock,
                sakOgSøknad = sakEtterFørstePeriode to nySøknadJournalførtMedOppgave(
                    sakId = sakEtterFørstePeriode.id,
                    fnr = sakEtterFørstePeriode.fnr,
                ),
            )

            // TODO: MÅ være revurdering? virker ikke som det spiller noen rolle her, men det skjønner ikke testen uansett så...evt test med opprettetRevurdering() fra helpers
            val (sakEtterTredjePeriode, mellomToAndrePerioder) = nySøknadsbehandlingUtenStønadsperiode(
                clock = clock,
                sakOgSøknad = sakEtterAndrePeriode to nySøknadJournalførtMedOppgave(
                    sakId = sakEtterAndrePeriode.id,
                    fnr = sakEtterAndrePeriode.fnr,
                ),
            )

            sakEtterTredjePeriode.oppdaterSøknadsbehandling(mellomToAndrePerioder).let {
                val revurderingsPeriodeInnvilgelseEtterKA = Periode.create(
                    fraOgMed = YearMonth.of(2023, Month.AUGUST).atDay(1),
                    tilOgMed = YearMonth.of(2023, Month.OCTOBER).atEndOfMonth(),
                )
                val nyPeriode = Stønadsperiode.create(periode = revurderingsPeriodeInnvilgelseEtterKA)

                it.oppdaterStønadsperiodeForSøknadsbehandling(
                    søknadsbehandlingId = mellomToAndrePerioder.id,
                    stønadsperiode = nyPeriode,
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
            Stønadsperiode.create(mai(2021)..april(2022)).toYearRange() shouldBe
                YearRange(Year.of(2021), Year.of(2022))
        }
    }
}
