package no.nav.su.se.bakover.database.søknadsbehandling

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.juni
import no.nav.su.se.bakover.common.persistence.antall
import no.nav.su.se.bakover.common.persistence.hent
import no.nav.su.se.bakover.database.avkorting.AvkortingsvarselPostgresRepo
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.avslag.AvslagManglendeDokumentasjon
import no.nav.su.se.bakover.domain.sak.NySak
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.søknadsbehandling.BehandlingsStatus
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.attesteringIverksatt
import no.nav.su.se.bakover.test.enUkeEtterFixedClock
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.formuegrenserFactoryTestPåDato
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattSøknadsbehandling
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.nySøknadsbehandling
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.persistence.withSession
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simulerUtbetaling
import no.nav.su.se.bakover.test.simuleringFeilutbetaling
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.vilkår.innvilgetFormueVilkår
import no.nav.su.se.bakover.test.vilkår.institusjonsoppholdvilkårAvslag
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import vilkår.personligOppmøtevilkårAvslag

internal class SøknadsbehandlingPostgresRepoTest {

    @Test
    fun `hent for sak`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            testDataHelper.persisterSøknadsbehandlingBeregnetAvslag().second.also {
                testDataHelper.sessionFactory.withSessionContext { session ->
                    repo.hentForSak(it.sakId, session)
                }
            }
        }
    }

    @Test
    fun `kan sette inn tom saksbehandling`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val vilkårsvurdert = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart().second
            repo.hent(vilkårsvurdert.id).also {
                it shouldBe vilkårsvurdert
                it.shouldBeTypeOf<Søknadsbehandling.Vilkårsvurdert.Uavklart>()
            }
        }
    }

    @Test
    fun `lagring av vilkårsvurdert behandling påvirker ikke andre behandlinger`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            testDataHelper.persisterSøknadsbehandlingIverksattAvslagMedBeregning()
            testDataHelper.persisterSøknadsbehandlingVilkårsvurdertInnvilget()

            dataSource.withSession { session ->
                "select count(1) from behandling where status = :status ".let {
                    it.antall(
                        mapOf("status" to BehandlingsStatus.VILKÅRSVURDERT_INNVILGET.toString()),
                        session,
                    ) shouldBe 1
                    it.antall(mapOf("status" to BehandlingsStatus.IVERKSATT_AVSLAG.toString()), session) shouldBe 1
                }
            }
        }
    }

    @Test
    fun `kan oppdatere med alle vilkår oppfylt`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val vilkårsvurdert: Søknadsbehandling.Vilkårsvurdert.Innvilget =
                testDataHelper.persisterSøknadsbehandlingVilkårsvurdertInnvilget().second
            repo.hent(vilkårsvurdert.id).also {
                it shouldBe vilkårsvurdert
            }
        }
    }

    @Test
    fun `kan oppdatere med vilkår som fører til avslag`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val vilkårsvurdert = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertAvslag().second
            repo.hent(vilkårsvurdert.id).also {
                it shouldBe vilkårsvurdert
                it.shouldBeTypeOf<Søknadsbehandling.Vilkårsvurdert.Avslag>()
            }
        }
    }

    @Test
    fun `kan oppdatere stønadsperiode`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo

            val (_, vilkårsvurdert) = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart { (sak, søknad) ->
                nySøknadsbehandling(
                    sakOgSøknad = sak to søknad,
                    stønadsperiode = Stønadsperiode.create(periode = januar(2021)),
                )
            }

            repo.lagre(
                vilkårsvurdert.oppdaterStønadsperiode(
                    oppdatertStønadsperiode = stønadsperiode2021,
                    formuegrenserFactory = formuegrenserFactoryTestPåDato(fixedLocalDate),
                    clock = fixedClock,
                ).getOrFail(),
            )

            repo.hent(vilkårsvurdert.id).also {
                it?.stønadsperiode shouldBe stønadsperiode2021
            }
        }
    }

    @Test
    fun `oppdaterer status og vilkår og sletter beregning og simulering hvis de eksisterer`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val (sak, innvilgetVilkårsvurdering) = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertInnvilget()
                .also { (_, innvilget) ->
                    repo.hent(innvilget.id) shouldBe innvilget
                    dataSource.withSession { session ->
                        "select * from behandling where id = :id".hent(mapOf("id" to innvilget.id), session) { row ->
                            row.stringOrNull("beregning") shouldBe null
                            row.stringOrNull("simulering") shouldBe null
                        }
                    }
                }

            val beregnet = innvilgetVilkårsvurdering
                .beregn(
                    begrunnelse = null,
                    clock = fixedClock,
                    satsFactory = satsFactoryTestPåDato(),
                ).getOrFail()
                .also {
                    repo.lagre(it)
                    repo.hent(it.id) shouldBe it
                    dataSource.withSession { session ->
                        "select * from behandling where id = :id".hent(
                            mapOf("id" to innvilgetVilkårsvurdering.id),
                            session,
                        ) { row ->
                            row.stringOrNull("beregning") shouldNotBe null
                            row.stringOrNull("simulering") shouldBe null
                        }
                    }
                }

            val simulert = beregnet.simuler(
                saksbehandler = saksbehandler,
            ) { _, _ ->
                simulerUtbetaling(
                    sak = sak,
                    søknadsbehandling = beregnet,
                    strict = false,
                ).map {
                    it.simulering
                }
            }.getOrFail().also { simulert ->
                repo.lagre(simulert)
                repo.hent(simulert.id) shouldBe simulert
                dataSource.withSession {
                    "select * from behandling where id = :id".hent(
                        mapOf("id" to innvilgetVilkårsvurdering.id),
                        it,
                    ) { row ->
                        row.stringOrNull("beregning") shouldNotBe null
                        row.stringOrNull("simulering") shouldNotBe null
                    }
                }
            }

            // Tilbake til vilkårsvurdert
            simulert.leggTilFormuevilkår(
                vilkår = innvilgetFormueVilkår(),
            ).getOrFail().also { vilkårsvurdert ->
                repo.lagre(vilkårsvurdert)
                repo.hent(vilkårsvurdert.id) shouldBe vilkårsvurdert
                dataSource.withSession {
                    "select * from behandling where id = :id".hent(
                        mapOf("id" to innvilgetVilkårsvurdering.id),
                        it,
                    ) { row ->
                        row.stringOrNull("beregning") shouldBe null
                        row.stringOrNull("simulering") shouldBe null
                    }
                }
            }
        }
    }

    @Nested
    inner class TilAttestering {
        @Test
        fun `til attestering innvilget`() {
            withMigratedDb { dataSource ->
                val testDataHelper = TestDataHelper(dataSource)
                val repo = testDataHelper.søknadsbehandlingRepo
                val tilAttestering = testDataHelper.persisterSøknadsbehandlingTilAttesteringInnvilget().second
                repo.hent(tilAttestering.id) shouldBe tilAttestering
            }
        }
    }

    @Test
    fun `til attestering avslag uten beregning`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val tilAttestering = testDataHelper.persisterSøknadsbehandlingTilAttesteringAvslagUtenBeregning().second
            repo.hent(tilAttestering.id) shouldBe tilAttestering
        }
    }

    @Test
    fun `til attestering avslag med beregning`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val tilAttestering = testDataHelper.persisterSøknadsbehandlingTilAttesteringAvslagMedBeregning().second
            repo.hent(tilAttestering.id) shouldBe tilAttestering
        }
    }

    @Nested
    inner class Underkjent {
        @Test
        fun `underkjent innvilget`() {
            withMigratedDb { dataSource ->
                val testDataHelper = TestDataHelper(dataSource)
                val repo = testDataHelper.søknadsbehandlingRepo
                val tilAttestering = testDataHelper.persisterSøknadsbehandlingUnderkjentInnvilget().second
                repo.hent(tilAttestering.id) shouldBe tilAttestering
            }
        }
    }

    @Test
    fun `underkjent avslag uten beregning`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val tilAttestering = testDataHelper.persisterSøknadsbehandlingUnderkjentAvslagUtenBeregning().second
            repo.hent(tilAttestering.id) shouldBe tilAttestering
        }
    }

    @Test
    fun `underkjent avslag med beregning`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val tilAttestering = testDataHelper.persisterSøknadsbehandlingUnderkjentAvslagMedBeregning().second
            repo.hent(tilAttestering.id) shouldBe tilAttestering
        }
    }

    @Nested
    inner class Iverksatt {
        @Test
        fun `iverksatt avslag innvilget`() {
            withMigratedDb { dataSource ->
                val testDataHelper = TestDataHelper(dataSource)
                val repo = testDataHelper.søknadsbehandlingRepo
                val iverksatt = testDataHelper.persisterSøknadsbehandlingIverksattInnvilget().second
                repo.hent(iverksatt.id) shouldBe iverksatt
            }
        }
    }

    @Test
    fun `iverksatt avslag uten beregning`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val (_, iverksatt) = testDataHelper.persisterSøknadsbehandlingIverksatt { (sak, søknad) ->
                iverksattSøknadsbehandling(
                    sakOgSøknad = sak to søknad,
                    fritekstTilBrev = "Dette er fritekst",
                    customVilkår = listOf(institusjonsoppholdvilkårAvslag()),
                    attestering = attesteringIverksatt(clock = enUkeEtterFixedClock),
                )
            }
            val expected = Søknadsbehandling.Iverksatt.Avslag.UtenBeregning(
                id = iverksatt.id,
                opprettet = iverksatt.opprettet,
                sakId = iverksatt.sakId,
                saksnummer = iverksatt.saksnummer,
                søknad = iverksatt.søknad,
                oppgaveId = iverksatt.oppgaveId,
                fnr = iverksatt.fnr,
                saksbehandler = saksbehandler,
                attesteringer = Attesteringshistorikk.empty().leggTilNyAttestering(attesteringIverksatt(clock = enUkeEtterFixedClock)),
                fritekstTilBrev = "Dette er fritekst",
                stønadsperiode = stønadsperiode2021,
                grunnlagsdata = iverksatt.grunnlagsdata,
                vilkårsvurderinger = iverksatt.vilkårsvurderinger,
                avkorting = AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere(
                    håndtert = AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående,
                ),
                sakstype = iverksatt.sakstype,
            )
            repo.hent(iverksatt.id).also {
                it shouldBe expected
            }
        }
    }

    @Test
    fun `iverksatt avslag med beregning`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val iverksatt = testDataHelper.persisterSøknadsbehandlingIverksattAvslagMedBeregning().second
            repo.hent(iverksatt.id) shouldBe iverksatt
        }
    }

    @Test
    fun `iverksatt avslag med beregning med grunnlag og vilkårsvurderinger`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val iverksatt = testDataHelper.persisterSøknadsbehandlingIverksattAvslagMedBeregning().second
            repo.hent(iverksatt.id) shouldBe iverksatt
        }
    }

    @Test
    fun `søknad har ikke påbegynt behandling`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadsbehandlingRepo = testDataHelper.søknadsbehandlingRepo
            val nySak: NySak = testDataHelper.persisterSakMedSøknadUtenJournalføringOgOppgave()
            søknadsbehandlingRepo.hentForSøknad(nySak.søknad.id) shouldBe null
        }
    }

    @Test
    fun `søknad har påbegynt behandling`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val søknadsbehandlingRepo = testDataHelper.søknadsbehandlingRepo
            testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart().second.let {
                søknadsbehandlingRepo.hentForSøknad(it.søknad.id) shouldBe it
            }
        }
    }

    @Test
    fun `kan lagre og hente avslag manglende dokumentasjon`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val opprettetMedStønadsperiode =
                testDataHelper.persisterSøknadsbehandlingIverksattAvslagUtenBeregning().second

            testDataHelper.søknadsbehandlingRepo.lagreAvslagManglendeDokumentasjon(
                avslag = AvslagManglendeDokumentasjon(søknadsbehandling = opprettetMedStønadsperiode),
            )

            testDataHelper.søknadsbehandlingRepo.hent(opprettetMedStønadsperiode.id) shouldBe Søknadsbehandling.Iverksatt.Avslag.UtenBeregning(
                id = opprettetMedStønadsperiode.id,
                opprettet = opprettetMedStønadsperiode.opprettet,
                sakId = opprettetMedStønadsperiode.sakId,
                saksnummer = opprettetMedStønadsperiode.saksnummer,
                søknad = opprettetMedStønadsperiode.søknad,
                oppgaveId = opprettetMedStønadsperiode.oppgaveId,
                fnr = opprettetMedStønadsperiode.fnr,
                saksbehandler = saksbehandler,
                attesteringer = Attesteringshistorikk.create(
                    attesteringer = listOf(
                        Attestering.Iverksatt(
                            attestant = NavIdentBruker.Attestant(attestant.navIdent),
                            opprettet = opprettetMedStønadsperiode.attesteringer.hentSisteAttestering().opprettet,
                        ),
                    ),
                ),
                fritekstTilBrev = "",
                stønadsperiode = opprettetMedStønadsperiode.stønadsperiode,
                grunnlagsdata = opprettetMedStønadsperiode.grunnlagsdata,
                vilkårsvurderinger = opprettetMedStønadsperiode.vilkårsvurderinger,
                avkorting = AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere(
                    håndtert = AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående,
                ),
                sakstype = opprettetMedStønadsperiode.sakstype,
            )
        }
    }

    @Test
    fun `gjør ingenting med avkorting dersom ingenting har blitt avkortet`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val revurdering = testDataHelper.persisterRevurderingIverksattInnvilget() // juker litt for å koble avkortingsvarsel mot en revurdering
            val avkorting = AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting(
                Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
                    objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                        sakId = revurdering.sakId,
                        revurderingId = revurdering.id,
                        simulering = simuleringFeilutbetaling(juni(2021)),
                        opprettet = Tidspunkt.now(fixedClock),
                    ),
                ),
            )
            testDataHelper.sessionFactory.withTransaction { tx ->
                (testDataHelper.databaseRepos.avkortingsvarselRepo as AvkortingsvarselPostgresRepo).lagre(avkortingsvarsel = avkorting.avkortingsvarsel, tx)
            }

            val (_, iverksattAvslagUtenBeregning, _) = testDataHelper.persisterIverksattSøknadsbehandlingAvslag { (sak, søknad) ->
                iverksattSøknadsbehandlingUføre(
                    sakInfo = SakInfo(
                        sakId = sak.id,
                        saksnummer = sak.saksnummer,
                        fnr = sak.fnr,
                        type = sak.type,
                    ),
                    sakOgSøknad = sak to søknad,
                    customVilkår = listOf(personligOppmøtevilkårAvslag()),
                    avkorting = avkorting,
                )
            }

            iverksattAvslagUtenBeregning.avkorting shouldBe avkorting.håndter().kanIkke().iverksett(iverksattAvslagUtenBeregning.id)
            testDataHelper.databaseRepos.avkortingsvarselRepo.hent(avkorting.avkortingsvarsel.id) shouldBe avkorting.avkortingsvarsel
        }
    }

    @Test
    fun `oppdaterer avkorting ved lagring av iverksatt innvilget søknadsbehandling med avkorting`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val revurdering = testDataHelper.persisterRevurderingIverksattInnvilget() // juker litt for å koble avkortingsvarsel mot en revurdering
            val avkorting = AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting(
                Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
                    objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                        sakId = revurdering.sakId,
                        revurderingId = revurdering.id,
                        simulering = simuleringFeilutbetaling(juni(2021)),
                        opprettet = Tidspunkt.now(fixedClock),
                    ),
                ),
            )
            testDataHelper.sessionFactory.withTransaction { tx ->
                (testDataHelper.databaseRepos.avkortingsvarselRepo as AvkortingsvarselPostgresRepo).lagre(avkortingsvarsel = avkorting.avkortingsvarsel, tx)
            }

            val (_, iverksattInnvilgetAvkortet, _) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilget { (sak, søknad) ->
                iverksattSøknadsbehandlingUføre(
                    sakInfo = SakInfo(
                        sakId = sak.id,
                        saksnummer = sak.saksnummer,
                        fnr = sak.fnr,
                        type = sak.type,
                    ),
                    sakOgSøknad = sak to søknad,
                    avkorting = avkorting,
                )
            }

            testDataHelper.søknadsbehandlingRepo.hent(iverksattInnvilgetAvkortet.id)!!.let {
                val oppdatertAvkorting = avkorting.håndter().iverksett(iverksattInnvilgetAvkortet.id)
                it.avkorting shouldBe oppdatertAvkorting
                testDataHelper.avkortingsvarselRepo.hent(avkorting.avkortingsvarsel.id) shouldBe oppdatertAvkorting.avkortingsvarsel
            }
        }
    }
}
