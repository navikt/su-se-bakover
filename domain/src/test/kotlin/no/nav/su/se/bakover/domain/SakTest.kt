package no.nav.su.se.bakover.domain

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.juli
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.common.periode.november
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
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
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.formuegrenserFactoryTestPåDato
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.innvilgetSøknadsbehandlingMedIverksattRegulering
import no.nav.su.se.bakover.test.innvilgetSøknadsbehandlingMedÅpenRegulering
import no.nav.su.se.bakover.test.iverksattRevurdering
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.nySakUføre
import no.nav.su.se.bakover.test.nySøknadJournalførtMedOppgave
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.plus
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.søknadinnhold
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.test.vedtakIverksattGjenopptakAvYtelseFraIverksattStans
import no.nav.su.se.bakover.test.vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.vedtakRevurdering
import no.nav.su.se.bakover.test.vedtakRevurderingIverksattInnvilget
import no.nav.su.se.bakover.test.vedtakRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.vilkår.tilstrekkeligDokumentert
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdAvslag
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class SakTest {

    @Test
    fun `henter åpne søknadsbehandlinger`() {
        val sakMedÅpenBehandling = søknadsbehandlingVilkårsvurdertUavklart().first
        sakMedÅpenBehandling.hentÅpneSøknadsbehandlinger().shouldBeRight()
        sakMedÅpenBehandling.harÅpenSøknadsbehandling() shouldBe true

        val sakUtenÅpenBehandling = iverksattSøknadsbehandlingUføre().first
        sakUtenÅpenBehandling.hentÅpneSøknadsbehandlinger().shouldBeLeft()
        sakUtenÅpenBehandling.harÅpenSøknadsbehandling() shouldBe false
    }

    @Test
    fun `henter åpne revurderinger`() {
        val sakMedÅpenBehandling = opprettetRevurdering().first
        sakMedÅpenBehandling.hentÅpneRevurderinger().shouldBeRight()

        val sakUtenÅpenBehandling = iverksattRevurdering().first
        sakUtenÅpenBehandling.hentÅpneRevurderinger().shouldBeLeft()
    }

    @Test
    fun `henter åpne reguleringer`() {
        val sakMedÅpenBehandling = innvilgetSøknadsbehandlingMedÅpenRegulering(1.mai(2021)).first
        sakMedÅpenBehandling.hentÅpneReguleringer().shouldBeRight()

        val sakUtenÅpenBehandling = innvilgetSøknadsbehandlingMedIverksattRegulering().first
        sakUtenÅpenBehandling.hentÅpneReguleringer().shouldBeLeft()
    }

    @Test
    fun `oppretter søknadsbehandling dersom det ikke finnes eksisterende åpne behandlinger`() {
        val (sakUtenÅpenBehandling, søknad) = nySakUføre()
        sakUtenÅpenBehandling.opprettNySøknadsbehandling(søknad.id, fixedClock).shouldBeRight()

        val sakMedÅpenSøknadsbehandling = søknadsbehandlingVilkårsvurdertUavklart().first
        sakMedÅpenSøknadsbehandling.opprettNySøknadsbehandling(søknad.id, fixedClock).shouldBeLeft()

        val sakMedÅpenRevurdering = opprettetRevurdering().first
        sakMedÅpenRevurdering.opprettNySøknadsbehandling(søknad.id, fixedClock).shouldBeLeft()

        val sakMedÅpenRegulering = innvilgetSøknadsbehandlingMedÅpenRegulering(1.mai(2021)).first
        sakMedÅpenRegulering.opprettNySøknadsbehandling(søknad.id, fixedClock)
    }

    @Test
    fun `oppretter revurdering dersom det ikke finnes eksisterende åpne behandlinger`() {
        val sakUtenÅpenBehandling = (iverksattSøknadsbehandlingUføre(stønadsperiode = stønadsperiode2021)).first
        val (sakMedÅpenRevurdering, revurdering) = opprettetRevurdering()

        sakUtenÅpenBehandling.opprettNyRevurdering(
            saksbehandler = revurdering.saksbehandler,
            revurderingsårsak = revurdering.revurderingsårsak,
            informasjonSomRevurderes = revurdering.informasjonSomRevurderes,
            clock = fixedClock,
            periode = stønadsperiode2021.periode,
            hentAktørId = { AktørId("aktørId").right() },
        ) { OppgaveId("oppgaveId").right() }.shouldBeRight()

        sakMedÅpenRevurdering.opprettNyRevurdering(
            saksbehandler = revurdering.saksbehandler,
            revurderingsårsak = revurdering.revurderingsårsak,
            informasjonSomRevurderes = revurdering.informasjonSomRevurderes,
            clock = fixedClock,
            periode = stønadsperiode2021.periode,
            hentAktørId = { AktørId("aktørId").right() },
        ) { OppgaveId("oppgaveId").right() }.shouldBeLeft()

        val sakMedÅpenRegulering = innvilgetSøknadsbehandlingMedÅpenRegulering(1.mai(2021)).first
        sakMedÅpenRegulering.opprettNyRevurdering(
            saksbehandler = revurdering.saksbehandler,
            revurderingsårsak = revurdering.revurderingsårsak,
            informasjonSomRevurderes = revurdering.informasjonSomRevurderes,
            clock = fixedClock,
            periode = stønadsperiode2021.periode,
            hentAktørId = { AktørId("aktørId").right() },
        ) { OppgaveId("oppgaveId").right() }.shouldBeLeft()
    }

    @Test
    fun `oppretter regulering dersom det ikke finnes eksisterende åpne behandlinger`() {
        val sakUtenÅpenBehandling = (iverksattSøknadsbehandlingUføre()).first
        sakUtenÅpenBehandling.opprettEllerOppdaterRegulering(1.mai(2020), fixedClock).shouldBeRight()

        val sakMedÅpenSøknadsbehandling = søknadsbehandlingVilkårsvurdertUavklart().first
        sakMedÅpenSøknadsbehandling.opprettEllerOppdaterRegulering(1.mai(2020), fixedClock).shouldBeLeft()

        val sakMedÅpenRevurdering = opprettetRevurdering().first
        sakMedÅpenRevurdering.opprettEllerOppdaterRegulering(1.mai(2020), fixedClock).shouldBeLeft()
    }

    @Nested
    inner class HentPerioderMedLøpendeYtelse {
        @Test
        fun `henter tom liste dersom ingen eksisterer`() {
            Sak(
                id = UUID.randomUUID(),
                saksnummer = saksnummer,
                opprettet = fixedTidspunkt,
                fnr = Fnr.generer(),
                søknader = listOf(),
                søknadsbehandlinger = listOf(),
                utbetalinger = listOf(),
                revurderinger = listOf(),
                vedtakListe = listOf(),
                type = Sakstype.UFØRE,
                uteståendeAvkorting = Avkortingsvarsel.Ingen,
            ).hentPerioderMedLøpendeYtelse() shouldBe emptyList()
        }

        @Test
        fun `henter en stønadsperiode`() {
            val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget()

            sak.hentPerioderMedLøpendeYtelse() shouldBe listOf(
                år(2021),
            )
        }

        @Test
        fun `henter stønadsperioder og justerer varigheten dersom de er delvis opphørt`() {
            val (sak, _) = vedtakRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(
                sakOgVedtakSomKanRevurderes = vedtakSøknadsbehandlingIverksattInnvilget(),
                revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
            )

            sak.hentPerioderMedLøpendeYtelse() shouldBe listOf(
                Periode.create(1.januar(2021), 30.april(2021)),
            )
        }

        @Test
        fun `henter stønadsperioder for tidligere opphørt periode som er innvilget og revurdert igjen`() {
            val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
                clock = fixedClock,
            )

            val (sakEtterOpphør, opphør) = vedtakRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(
                revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
                sakOgVedtakSomKanRevurderes = sak to vedtak,
                clock = fixedClock.plus(1, ChronoUnit.SECONDS),
            )

            sakEtterOpphør.hentPerioderMedLøpendeYtelse() shouldBe listOf(
                Periode.create(1.januar(2021), 30.april(2021)),
            )

            val (sakEtterNyPeriode, nyPeriode) = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = Stønadsperiode.create(periode = Periode.create(1.juni(2021), 31.desember(2021))),
                clock = fixedClock.plus(2, ChronoUnit.SECONDS),
            )

            val sakEtterOpphørOgNyPeriode = sakEtterOpphør.copy(
                revurderinger = sakEtterOpphør.revurderinger + sakEtterNyPeriode.revurderinger,
                søknadsbehandlinger = sakEtterOpphør.søknadsbehandlinger + sakEtterNyPeriode.søknadsbehandlinger,
                vedtakListe = sakEtterOpphør.vedtakListe + sakEtterNyPeriode.vedtakListe,
                utbetalinger = sakEtterOpphør.utbetalinger + sakEtterNyPeriode.utbetalinger,
            )

            val (sakEtterRevurdering, revurdering) = vedtakRevurderingIverksattInnvilget(
                revurderingsperiode = Periode.create(1.november(2021), 31.desember(2021)),
                sakOgVedtakSomKanRevurderes = sakEtterOpphørOgNyPeriode to nyPeriode,
                clock = fixedClock.plus(3, ChronoUnit.SECONDS),
            )

            sakEtterRevurdering.let {
                it.hentPerioderMedLøpendeYtelse() shouldBe listOf(
                    Periode.create(1.januar(2021), 30.april(2021)),
                    Periode.create(1.juni(2021), 31.desember(2021)),
                )
                it.vedtakListe shouldContainAll listOf(
                    vedtak,
                    opphør,
                    nyPeriode,
                    revurdering,
                )
            }
        }

        @Test
        fun `henter stønadsperioder med opphold mellom`() {
            val (sak, stønadsperiode1) = vedtakSøknadsbehandlingIverksattInnvilget()

            val (_, stønadsperiode2) = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = Stønadsperiode.create(periode = år(2023)),
            )

            sak.copy(
                søknadsbehandlinger = sak.søknadsbehandlinger + stønadsperiode2.behandling,
                vedtakListe = sak.vedtakListe + stønadsperiode2,
            ).let {
                it.hentPerioderMedLøpendeYtelse() shouldBe listOf(
                    år(2021),
                    år(2023),
                )
                it.vedtakListe shouldContainAll listOf(
                    stønadsperiode1,
                    stønadsperiode2,
                )
            }
        }

        @Test
        fun `henter stønadsperioder med revurdering og med opphold mellom`() {
            val (sakStønadsperiode1, stønadsperiode1) = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = stønadsperiode2021,
            )

            val (sakRevurdering1, revurderingPeriode1) = vedtakRevurderingIverksattInnvilget(
                revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
                sakOgVedtakSomKanRevurderes = sakStønadsperiode1 to stønadsperiode1,
            )

            val stønadsperiode2023 = Stønadsperiode.create(periode = år(2023))

            val (sakStønadsperiode2, stønadsperiode2) = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = stønadsperiode2023,
            )

            val (sakRevurdering2, revurderingPeriode2) = vedtakRevurderingIverksattInnvilget(
                stønadsperiode = stønadsperiode2023,
                revurderingsperiode = Periode.create(1.november(2023), 31.desember(2023)),
                sakOgVedtakSomKanRevurderes = sakStønadsperiode2 to stønadsperiode2,
            )

            sakStønadsperiode1.copy(
                søknadsbehandlinger = sakRevurdering1.søknadsbehandlinger + sakRevurdering2.søknadsbehandlinger,
                revurderinger = sakRevurdering1.revurderinger + sakRevurdering2.revurderinger,
                vedtakListe = sakRevurdering1.vedtakListe + sakRevurdering2.vedtakListe,
                utbetalinger = sakRevurdering1.utbetalinger + sakRevurdering2.utbetalinger,
            ).let {
                it.hentPerioderMedLøpendeYtelse() shouldBe listOf(
                    år(2021),
                    år(2023),
                )
                it.vedtakListe shouldContainAll listOf(
                    stønadsperiode1,
                    revurderingPeriode1,
                    stønadsperiode2,
                    revurderingPeriode2,
                )
            }
        }

        @Test
        fun `henter stønadsperioder som har blitt revurdert`() {
            val (sakFørRevurdering, søknadsvedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
                clock = fixedClock,
            )

            sakFørRevurdering.hentPerioderMedLøpendeYtelse() shouldBe listOf(
                år(2021),
            )

            val (sakEtterStans, stans) = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
                periode = Periode.create(1.februar(2021), 31.desember(2021)),
                sakOgVedtakSomKanRevurderes = sakFørRevurdering to søknadsvedtak,
                clock = fixedClock.plus(1, ChronoUnit.SECONDS),
            )

            sakEtterStans.hentPerioderMedLøpendeYtelse() shouldBe listOf(
                år(2021),
            )

            val (sakEtterGjenopptak, gjenopptak) = vedtakIverksattGjenopptakAvYtelseFraIverksattStans(
                periode = Periode.create(1.februar(2021), 31.desember(2021)),
                sakOgVedtakSomKanRevurderes = sakEtterStans to stans,
                // Funksjonen vil plusse på 2 selv, slik at vi ikke trenger
                clock = fixedClock,
            )

            sakEtterGjenopptak.hentPerioderMedLøpendeYtelse() shouldBe listOf(
                år(2021),
            )

            val (sakEtterRevurdering, revurdering) = vedtakRevurderingIverksattInnvilget(
                revurderingsperiode = Periode.create(1.juli(2021), 31.desember(2021)),
                sakOgVedtakSomKanRevurderes = sakEtterGjenopptak to gjenopptak,
                clock = fixedClock.plus(3, ChronoUnit.SECONDS),
            )

            sakEtterRevurdering.hentPerioderMedLøpendeYtelse() shouldBe listOf(
                år(2021),
            )
            sakEtterRevurdering.vedtakListe shouldContainAll listOf(
                søknadsvedtak,
                stans,
                gjenopptak,
                revurdering,
            )
        }

        @Test
        fun `slår sammen stønadsperioder som kommer etter hverandre`() {
            val (sakStønadsperiode1, stønadsperiode1) = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = stønadsperiode2021,
            )

            val stønadsperiode2022 = Stønadsperiode.create(periode = år(2022))

            val (sakStønadsperiode2, stønadsperiode2) = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = stønadsperiode2022,
            )

            sakStønadsperiode1.copy(
                søknadsbehandlinger = sakStønadsperiode1.søknadsbehandlinger + sakStønadsperiode2.søknadsbehandlinger,
                revurderinger = sakStønadsperiode1.revurderinger + sakStønadsperiode2.revurderinger,
                vedtakListe = sakStønadsperiode1.vedtakListe + sakStønadsperiode2.vedtakListe,
                utbetalinger = sakStønadsperiode1.utbetalinger + sakStønadsperiode2.utbetalinger,
            ).let {
                it.hentPerioderMedLøpendeYtelse() shouldBe listOf(
                    Periode.create(1.januar(2021), 31.desember(2022)),
                )
                it.vedtakListe shouldContainAll listOf(
                    stønadsperiode1,
                    stønadsperiode2,
                )
            }
        }
    }

    @Nested
    inner class KanUtbetalingerStansesEllerGjenopptas {
        @Test
        fun `utbetalinger kan stanses på en sak med en standard stønadsperiode`() {
            val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget()

            sak.kanUtbetalingerStansesEllerGjenopptas(fixedClock) shouldBe KanStansesEllerGjenopptas.STANS
        }

        @Test
        fun `utbetalinger kan ikke stanses dersom det er fremtidig hull i stønadsperiodene`() {
            val (førHull, _) = iverksattSøknadsbehandlingUføre(
                stønadsperiode = Stønadsperiode.create(
                    periode = januar(2021),
                ),
            )

            val (etterHull, _) = iverksattSøknadsbehandlingUføre(
                stønadsperiode = Stønadsperiode.create(
                    periode = mars(2021),
                ),
                sakOgSøknad = førHull to nySøknadJournalførtMedOppgave(
                    clock = fixedClock,
                    sakId = førHull.id,
                    søknadInnhold = søknadinnhold(
                        fnr = førHull.fnr,
                    ),
                ),
            )

            etterHull.kanUtbetalingerStansesEllerGjenopptas(fixedClock) shouldBe KanStansesEllerGjenopptas.INGEN
        }

        @Test
        fun `utbetalinger kan stanses dersom det er et historisk hull i stønadsperiodene`() {
            val juni2021 = Clock.fixed(1.juni(2021).atTime(0, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
            val (førHull, _) = iverksattSøknadsbehandlingUføre(
                stønadsperiode = Stønadsperiode.create(
                    periode = januar(2021),
                ),
            )

            val (etterHull, _) = iverksattSøknadsbehandlingUføre(
                stønadsperiode = Stønadsperiode.create(
                    periode = mars(2021),
                ),
                sakOgSøknad = førHull to nySøknadJournalførtMedOppgave(
                    clock = fixedClock,
                    sakId = førHull.id,
                    søknadInnhold = søknadinnhold(
                        fnr = førHull.fnr,
                    ),
                ),
            )

            etterHull.kanUtbetalingerStansesEllerGjenopptas(juni2021) shouldBe KanStansesEllerGjenopptas.STANS
        }

        @Test
        fun `harGjeldendeEllerFremtidigStønadsperiode skal returnere true om det man er i en stønadsperiode`() {
            val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = Stønadsperiode.create(
                    periode = januar(2021),
                ),
            )
            sak.harGjeldendeEllerFremtidigStønadsperiode(fixedClock) shouldBe true
        }

        @Test
        fun `harGjeldendeEllerFremtidigStønadsperiode skal returnere true om det er en stønadsperiode i fremtiden`() {
            val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = Stønadsperiode.create(
                    periode = mars(2021),
                ),
            )
            sak.harGjeldendeEllerFremtidigStønadsperiode(fixedClock) shouldBe true
        }

        @Test
        fun `harGjeldendeEllerFremtidigStønadsperiode skal returnere false om det ikke er noen fremtidige stønadsperioder`() {
            val juni2021 = Clock.fixed(1.juni(2021).atTime(0, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
            val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = Stønadsperiode.create(
                    periode = januar(2021),
                ),
            )
            sak.harGjeldendeEllerFremtidigStønadsperiode(juni2021) shouldBe false
        }
    }

    @Nested
    inner class OppdaterStønadsperiodeForSøknadsbehandling {

        @Test
        fun `oppdaterer perioden riktig`() {
            val (_, vilkårsvurdert) = søknadsbehandlingVilkårsvurdertInnvilget()

            val nyPeriode = Periode.create(1.februar(2022), 31.mars(2022))
            val actual = vilkårsvurdert.oppdaterStønadsperiode(
                oppdatertStønadsperiode = Stønadsperiode.create(nyPeriode),
                formuegrenserFactory = formuegrenserFactoryTestPåDato(),
            ).getOrFail()

            vilkårsvurdert.periode shouldNotBe nyPeriode
            actual.periode shouldBe nyPeriode
            actual.vilkårsvurderinger.uføreVilkår().getOrFail().grunnlag.first().periode shouldBe nyPeriode
            actual.vilkårsvurderinger.formue.grunnlag.first().periode shouldBe nyPeriode
            actual.grunnlagsdata.bosituasjon.first().periode shouldBe nyPeriode
        }

        @Test
        fun `stønadsperioder skal ikke kunne overlappe`() {
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
                ) shouldBe Sak.KunneIkkeOppdatereStønadsperiode.StønadsperiodeOverlapperMedLøpendeStønadsperiode.left()
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
                ) shouldBe Sak.KunneIkkeOppdatereStønadsperiode.StønadsperiodeForSenerePeriodeEksisterer.left()
            }
        }

        @Test
        fun `stønadsperioder skal ikke kunne overlappe med perioder som skal avkortes`() {
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
            )

            val periode = år(2022)
            val nyStønadsperiode =
                Stønadsperiode.create(periode)
            val (_, nySøknadsbehandling) = søknadsbehandlingVilkårsvurdertUavklart(
                clock = tikkendeKlokke,
                stønadsperiode = nyStønadsperiode,
            )

            val nySøknadsbehandlingMedOpplysningsplikt = nySøknadsbehandling.copy(
                vilkårsvurderinger = nySøknadsbehandling.vilkårsvurderinger.leggTil(tilstrekkeligDokumentert(periode = periode)),
            )

            sakMedRevurderingOgSøknadVedtak.copy(
                søknadsbehandlinger = sakMedRevurderingOgSøknadVedtak.søknadsbehandlinger + nySøknadsbehandling,
            ).let { sak ->
                sak.oppdaterStønadsperiodeForSøknadsbehandling(
                    søknadsbehandlingId = nySøknadsbehandlingMedOpplysningsplikt.id,
                    stønadsperiode = nyStønadsperiode,
                    clock = tikkendeKlokke,
                    formuegrenserFactory = formuegrenserFactoryTestPåDato(),
                ).getOrFail() shouldBe nySøknadsbehandlingMedOpplysningsplikt

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
                    ) shouldBe Sak.KunneIkkeOppdatereStønadsperiode.StønadsperiodeInneholderAvkortingPgaUtenlandsopphold.left()
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
                    ).getOrFail() shouldBe nySøknadsbehandlingMedOpplysningsplikt.copy(
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
        fun `utløp av ytelse`() {
            val stønadsperiode = Stønadsperiode.create(år(2021))
            val revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021))

            vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = stønadsperiode,
            ).let { (sak, _) ->
                sak.ytelseUtløperVedUtløpAv(januar(2021)) shouldBe false
                sak.ytelseUtløperVedUtløpAv(desember(2021)) shouldBe true
                sak.ytelseUtløperVedUtløpAv(år(2021)) shouldBe true
                sak.ytelseUtløperVedUtløpAv(Periode.create(1.januar(2021), 31.desember(2025))) shouldBe false
            }

            // opphørt ytelse oppfattes ikke som utløpende
            vedtakRevurdering(
                stønadsperiode = stønadsperiode,
                revurderingsperiode = revurderingsperiode,
                vilkårOverrides = listOf(
                    avslåttUførevilkårUtenGrunnlag(
                        periode = revurderingsperiode,
                    ),
                ),
            ).let { (sak, vedtak) ->
                vedtak shouldBe beOfType<VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering>()

                stønadsperiode.periode.måneder().none {
                    sak.ytelseUtløperVedUtløpAv(it)
                } shouldBe true

                revurderingsperiode.måneder().none {
                    sak.ytelseUtløperVedUtløpAv(it)
                } shouldBe true
            }
        }
    }

    @Nested
    inner class HentGjeldendeMånedsberegninger {
        @Test
        fun `henter gjeldende månedsberegninger for enkelt vedtak`() {
            iverksattSøknadsbehandlingUføre().also { (sak, _, vedtak) ->
                sak.hentGjeldendeMånedsberegninger(
                    periode = mai(2021)..juli(2021),
                    clock = fixedClock,
                ).also { gjeldendeMånedsberegninger ->
                    vedtak.shouldBeType<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling>().also {
                        gjeldendeMånedsberegninger shouldBe listOf(
                            it.beregning.getMånedsberegninger()[4],
                            it.beregning.getMånedsberegninger()[5],
                            it.beregning.getMånedsberegninger()[6],
                        )
                    }
                }
            }
        }

        @Test
        fun `henter gjeldende månedsberegninger med hull mellom vedtak`() {
            val tikkendeKlokke = TikkendeKlokke(fixedClock)
            iverksattSøknadsbehandlingUføre(
                stønadsperiode = Stønadsperiode.create(januar(2021)..november(2021)),
                clock = tikkendeKlokke,
            ).also { (sak1, _, vedtak1) ->
                iverksattSøknadsbehandlingUføre(
                    stønadsperiode = Stønadsperiode.create(januar(2022)..november(2022)),
                    sakOgSøknad = sak1 to nySøknadJournalførtMedOppgave(
                        clock = tikkendeKlokke,
                        sakId = sak1.id,
                        søknadInnhold = søknadinnhold(
                            fnr = sak1.fnr,
                        ),
                    ),
                    clock = tikkendeKlokke,
                ).also { (sak2, _, vedtak2) ->
                    sak2.hentGjeldendeMånedsberegninger(
                        periode = november(2021)..januar(2022),
                        clock = fixedClock,
                    ).also { gjeldendeMånedsberegninger ->
                        vedtak2.shouldBeType<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling>().also {
                            gjeldendeMånedsberegninger shouldBe listOf(
                                (vedtak1 as VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling).beregning.getMånedsberegninger()[10],
                                it.beregning.getMånedsberegninger()[0],
                            )
                        }
                    }
                }
            }
        }

        @Test
        fun `henter gjeldende månedsberegninger fra tidligere vedtak hvis ytelse er stanset`() {
            iverksattSøknadsbehandlingUføre().also { (sak, _, vedtakSøknadsbehandling) ->
                vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
                    sakOgVedtakSomKanRevurderes = sak to vedtakSøknadsbehandling as VedtakSomKanRevurderes,
                ).also { (sak2, vedtakStans) ->
                    sak2.hentGjeldendeMånedsberegninger(
                        periode = mai(2021)..juli(2021),
                        clock = fixedClock,
                    ).also { gjeldendeMånedsberegninger ->
                        vedtakSøknadsbehandling.shouldBeType<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling>()
                            .also { søknadsbehandling ->
                                gjeldendeMånedsberegninger shouldBe listOf(
                                    søknadsbehandling.beregning.getMånedsberegninger()[4].also {
                                        sak2.hentGjeldendeVedtaksdata(it.måned, fixedClock)
                                            .getOrFail().gjeldendeVedtakPåDato(it.måned.fraOgMed) shouldBe vedtakStans
                                    },
                                    søknadsbehandling.beregning.getMånedsberegninger()[5].also {
                                        sak2.hentGjeldendeVedtaksdata(it.måned, fixedClock)
                                            .getOrFail().gjeldendeVedtakPåDato(it.måned.fraOgMed) shouldBe vedtakStans
                                    },
                                    søknadsbehandling.beregning.getMånedsberegninger()[6].also {
                                        sak2.hentGjeldendeVedtaksdata(it.måned, fixedClock)
                                            .getOrFail().gjeldendeVedtakPåDato(it.måned.fraOgMed) shouldBe vedtakStans
                                    },
                                )
                            }
                        vedtakStans.shouldBeType<VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse>()
                    }
                }
            }
        }
    }

    @Nested
    inner class HistoriskVedtaksdataForVedtaksperiode {
        @Test
        fun `henter tidligere informasjon for overlappende vedtak`() {
            val clock = TikkendeKlokke(fixedClock)

            val sakOgVedtak1 = vedtakSøknadsbehandlingIverksattInnvilget(
                stønadsperiode = Stønadsperiode.create(år(2021)),
                clock = clock,
            )
            val sakOgVedtak2 = vedtakRevurderingIverksattInnvilget(
                stønadsperiode = Stønadsperiode.create(år(2021)),
                sakOgVedtakSomKanRevurderes = sakOgVedtak1,
                clock = clock,
            )
            val (sak, _) = vedtakRevurderingIverksattInnvilget(
                stønadsperiode = Stønadsperiode.create(år(2021)),
                sakOgVedtakSomKanRevurderes = sakOgVedtak2,
                clock = clock,
            )

            sak.historiskGrunnlagForVedtaketsPeriode(
                vedtakId = sakOgVedtak2.second.id,
                clock = clock,
            ) shouldBe GjeldendeVedtaksdata(
                periode = år(2021),
                vedtakListe = nonEmptyListOf(sakOgVedtak1.second),
                clock = clock,
            ).right()
        }

        @Test
        fun `henter tidligere informasjon for vedtak - ingen tidligere vedtak for periode`() {
            val clock = TikkendeKlokke(fixedClock)

            val (sak1, _, vedtak1) = iverksattSøknadsbehandlingUføre(
                stønadsperiode = Stønadsperiode.create(år(2021)),
                clock = clock,
            )

            sak1.historiskGrunnlagForVedtaketsPeriode(
                vedtakId = vedtak1.id,
                clock = fixedClock,
            ) shouldBe Sak.KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak.IngenTidligereVedtak.left()
        }
    }
}
