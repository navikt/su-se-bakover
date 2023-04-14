package no.nav.su.se.bakover.database.søknadsbehandling

import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.persistence.antall
import no.nav.su.se.bakover.common.persistence.hent
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.revurdering.steg.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.steg.Revurderingsteg
import no.nav.su.se.bakover.domain.sak.NySak
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.skatt.Skattereferanser
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingMedSkattegrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.oppdaterStønadsperiodeForSøknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.formue.LeggTilFormuevilkårRequest
import no.nav.su.se.bakover.test.attesteringIverksatt
import no.nav.su.se.bakover.test.enUkeEtterFixedClock
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.formuegrenserFactoryTestPåDato
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlagsdataMedEpsMedFradrag
import no.nav.su.se.bakover.test.iverksattRevurdering
import no.nav.su.se.bakover.test.iverksattSøknadsbehandling
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.nySøknadsbehandlingMedStønadsperiode
import no.nav.su.se.bakover.test.persistence.TestDataHelper
import no.nav.su.se.bakover.test.persistence.withMigratedDb
import no.nav.su.se.bakover.test.persistence.withSession
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simulering.simulerUtbetaling
import no.nav.su.se.bakover.test.skatt.nySkattegrunnlag
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.stønadsperiode2022
import no.nav.su.se.bakover.test.toFormueRequestGrunnlag
import no.nav.su.se.bakover.test.vilkår.institusjonsoppholdvilkårAvslag
import no.nav.su.se.bakover.test.vilkår.utenlandsoppholdAvslag
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import vilkår.personligOppmøtevilkårAvslag
import java.util.UUID

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
                it.shouldBeTypeOf<VilkårsvurdertSøknadsbehandling.Uavklart>()
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
                        mapOf("status" to SøknadsbehandlingStatusDB.VILKÅRSVURDERT_INNVILGET.toString()),
                        session,
                    ) shouldBe 1
                    it.antall(
                        mapOf("status" to SøknadsbehandlingStatusDB.IVERKSATT_AVSLAG.toString()),
                        session,
                    ) shouldBe 1
                }
            }
        }
    }

    @Test
    fun `kan oppdatere med alle vilkår oppfylt`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val vilkårsvurdert: VilkårsvurdertSøknadsbehandling.Innvilget =
                testDataHelper.persisterSøknadsbehandlingVilkårsvurdertInnvilget().second
            val repo = testDataHelper.søknadsbehandlingRepo
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
                it.shouldBeTypeOf<VilkårsvurdertSøknadsbehandling.Avslag>()
            }
        }
    }

    @Test
    fun `kan oppdatere stønadsperiode`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo

            val (sak, vilkårsvurdert) = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertUavklart { (sak, søknad) ->
                nySøknadsbehandlingMedStønadsperiode(
                    sakOgSøknad = sak to søknad,
                    stønadsperiode = Stønadsperiode.create(periode = januar(2021)),
                ).let {
                    it.first to it.second
                }
            }

            repo.lagre(
                sak.oppdaterStønadsperiodeForSøknadsbehandling(
                    søknadsbehandlingId = vilkårsvurdert.id,
                    stønadsperiode = stønadsperiode2021,
                    formuegrenserFactory = formuegrenserFactoryTestPåDato(fixedLocalDate),
                    clock = fixedClock,
                    saksbehandler = saksbehandler,
                    hentPerson = { person().right() },
                    saksbehandlersAvgjørelse = null,
                ).getOrFail().second,
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
                    nySaksbehandler = saksbehandler,
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
                clock = fixedClock,
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
            simulert.leggTilFormuegrunnlag(
                request = LeggTilFormuevilkårRequest(
                    behandlingId = simulert.id,
                    formuegrunnlag = simulert.vilkårsvurderinger.formue.grunnlag.toFormueRequestGrunnlag(),
                    saksbehandler = saksbehandler,
                    tidspunkt = fixedTidspunkt,
                ),
                formuegrenserFactory = formuegrenserFactoryTestPåDato(),
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
        fun `iverksatt innvilget`() {
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
            repo.hent(iverksatt.id).also {
                it shouldBe iverksatt
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

            testDataHelper.søknadsbehandlingRepo.lagre(
                søknadsbehandling = opprettetMedStønadsperiode,
            )

            testDataHelper.søknadsbehandlingRepo.hent(opprettetMedStønadsperiode.id) shouldBe opprettetMedStønadsperiode
            opprettetMedStønadsperiode.shouldBeInstanceOf<IverksattSøknadsbehandling.Avslag.UtenBeregning>()
        }
    }

    @Test
    fun `gjør ingenting med avkorting dersom ingenting har blitt avkortet`() {
        // Dersom det finnes en utestående avkorting på saken, og vi avslår en ny søknadsbehandling, forventer vi at den er uforandret på saken.
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)

            val (sak, revurderingSomFørteTilAvkorting, _) = testDataHelper.persisterIverksattRevurdering(
                sakOgRevurdering = {
                    iverksattRevurdering(
                        sakOgVedtakSomKanRevurderes = it,
                        informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Utenlandsopphold)),
                        vilkårOverrides = listOf(
                            utenlandsoppholdAvslag(
                                opprettet = Tidspunkt.now(testDataHelper.clock),
                            ),
                        ),
                        utbetalingerKjørtTilOgMed = år(2021).tilOgMed,
                        clock = testDataHelper.clock,
                    )
                },
            )
            verifiserUteståendeAvkortingPåSak(
                sak = sak,
                revurderingId = revurderingSomFørteTilAvkorting.id,
                sakRepo = testDataHelper.sakRepo,
            )

            val (sakOppdatertMedSøknad, iverksattAvslagUtenBeregning, _) = testDataHelper.persisterIverksattSøknadsbehandlingAvslag(
                sakOgSøknad = Pair(sak, testDataHelper.persisterJournalførtSøknadMedOppgave(sakId = sak.id).second),
            ) { (sak, søknad) ->
                iverksattSøknadsbehandlingUføre(
                    clock = testDataHelper.clock,
                    stønadsperiode = stønadsperiode2022,
                    sakOgSøknad = sak to søknad,
                    customVilkår = listOf(
                        personligOppmøtevilkårAvslag(
                            periode = år(2022),
                        ),
                    ),
                )
            }
            // Et avslag i 2022 som ikke overlapper med 2021, skal ikke påvirke avkortinga.
            verifiserUteståendeAvkortingPåSak(
                sak = sakOppdatertMedSøknad,
                revurderingId = revurderingSomFørteTilAvkorting.id,
                sakRepo = testDataHelper.sakRepo,
            )

            iverksattAvslagUtenBeregning.avkorting.shouldBeInstanceOf<AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere>()
                .also {
                    it.håndtert.shouldBeInstanceOf<AvkortingVedSøknadsbehandling.Håndtert.AvkortUtestående>().also {
                        it shouldBe AvkortingVedSøknadsbehandling.Håndtert.AvkortUtestående(
                            Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
                                objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                                    id = it.avkortingsvarsel.id,
                                    sakId = sakOppdatertMedSøknad.id,
                                    revurderingId = revurderingSomFørteTilAvkorting.id,
                                    opprettet = it.avkortingsvarsel.opprettet,
                                    simulering = it.avkortingsvarsel.simulering,
                                ),
                            ),
                        )
                    }
                }
        }
    }

    @Test
    fun `oppdaterer avkorting ved lagring av iverksatt innvilget søknadsbehandling med avkorting`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)

            val (sak, revurderingSomFørteTilAvkorting, _) = testDataHelper.persisterIverksattRevurdering(
                sakOgRevurdering = {
                    iverksattRevurdering(
                        sakOgVedtakSomKanRevurderes = it,
                        informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Utenlandsopphold)),
                        vilkårOverrides = listOf(
                            utenlandsoppholdAvslag(
                                opprettet = Tidspunkt.now(testDataHelper.clock),
                            ),
                        ),
                        utbetalingerKjørtTilOgMed = februar(2021).tilOgMed,
                        clock = testDataHelper.clock,
                    )
                },
            )
            verifiserUteståendeAvkortingPåSak(
                sak = sak,
                revurderingId = revurderingSomFørteTilAvkorting.id,
                sakRepo = testDataHelper.sakRepo,
            )

            val (sakOppdatertMedSøknad, iverksattSøknadsbehandlingVedtak, _) = testDataHelper.persisterSøknadsbehandlingIverksattInnvilgetMedKvittertUtbetaling(
                sakOgSøknad = Pair(sak, testDataHelper.persisterJournalførtSøknadMedOppgave(sakId = sak.id).second),
            ) { (sak, søknad) ->
                iverksattSøknadsbehandlingUføre(
                    clock = testDataHelper.clock,
                    stønadsperiode = stønadsperiode2022,
                    sakOgSøknad = sak to søknad,
                )
            }

            // Forventer at den nye søknadsbehandlingen har avkortet alle utestående avkortinger på saken.
            testDataHelper.sakRepo.hentSak(sak.id) shouldBe sakOppdatertMedSøknad
            sakOppdatertMedSøknad.uteståendeAvkorting.shouldBeInstanceOf<Avkortingsvarsel.Ingen>()

            iverksattSøknadsbehandlingVedtak.behandling.avkorting.shouldBeInstanceOf<AvkortingVedSøknadsbehandling.Iverksatt.AvkortUtestående>()
                .also {
                    it.avkortingsvarsel shouldBe Avkortingsvarsel.Utenlandsopphold.Avkortet(
                        Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
                            objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                                id = it.avkortingsvarsel.id,
                                sakId = sakOppdatertMedSøknad.id,
                                revurderingId = revurderingSomFørteTilAvkorting.id,
                                opprettet = it.avkortingsvarsel.opprettet,
                                simulering = it.avkortingsvarsel.simulering,
                            ),
                        ),
                        behandlingId = iverksattSøknadsbehandlingVedtak.behandling.id,
                    )
                }
        }
    }

    @Test
    fun `lagrer & erstatter & fjerner med skattegrunnlag`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val behandling = testDataHelper.persisterSøknadsbehandlingVilkårsvurdertInnvilget().second
            repo.hent(behandling.id).let {
                it shouldNotBe null
                it!!.grunnlagsdata.skattereferanser shouldBe null
                repo.hentSkattegrunnlag(behandling.id) shouldBe null
            }

            val søkersId = UUID.randomUUID()
            val epsId = UUID.randomUUID()
            val oppdatertBehandlingMedSkattereferanserForSøker = behandling.copy(
                grunnlagsdata = behandling.grunnlagsdata.copy(
                    skattereferanser = Skattereferanser(søkersId, null),
                ),
            ).also { oppdatertSøknadsbehandling ->
                repo.lagreMedSkattegrunnlag(
                    SøknadsbehandlingMedSkattegrunnlag(
                        søknadsbehandling = oppdatertSøknadsbehandling,
                        opprettet = fixedTidspunkt,
                        søker = nySkattegrunnlag(),
                        eps = null,
                    ),
                )
                repo.hent(oppdatertSøknadsbehandling.id).let {
                    it shouldNotBe null
                    it!!.grunnlagsdata.skattereferanser shouldNotBe null
                    it.grunnlagsdata.skattereferanser?.eps shouldBe null
                    repo.hentSkattegrunnlag(behandling.id)!!.let {
                        it.eps shouldBe null
                        it.søker shouldNotBe null
                        it.søknadsbehandling shouldBe oppdatertSøknadsbehandling
                    }
                }
            }

            val oppdatertMedEps = oppdatertBehandlingMedSkattereferanserForSøker.copy(
                grunnlagsdata = grunnlagsdataMedEpsMedFradrag(
                    skattereferanser = Skattereferanser(søkersId, epsId),
                ),
            ).also { oppdatertSøknadsbehandlingMedEps ->
                repo.lagreMedSkattegrunnlag(
                    SøknadsbehandlingMedSkattegrunnlag(
                        søknadsbehandling = oppdatertSøknadsbehandlingMedEps,
                        opprettet = fixedTidspunkt,
                        søker = nySkattegrunnlag(),
                        eps = nySkattegrunnlag(),
                    ),
                )
                repo.hent(oppdatertSøknadsbehandlingMedEps.id)!!.let {
                    it.grunnlagsdata.skattereferanser shouldNotBe null
                    it.grunnlagsdata.skattereferanser?.eps shouldNotBe null
                    repo.hentSkattegrunnlag(behandling.id)!!.let {
                        it.eps shouldNotBe null
                        it.søker shouldNotBe null
                        it.søknadsbehandling shouldBe oppdatertSøknadsbehandlingMedEps
                    }
                }
            }
            oppdatertMedEps.copy(
                grunnlagsdata = grunnlagsdataMedEpsMedFradrag(
                    skattereferanser = Skattereferanser(søkersId, null),
                ),
            ).also { oppdatertUtenEps ->
                repo.lagreMedSkattegrunnlag(
                    SøknadsbehandlingMedSkattegrunnlag(
                        søknadsbehandling = oppdatertUtenEps,
                        opprettet = fixedTidspunkt,
                        søker = nySkattegrunnlag(),
                        eps = null,
                    ),
                )
                repo.hentSkattegrunnlag(oppdatertUtenEps.id)!!.let {
                    it.eps shouldBe null
                    it.søker shouldNotBe null
                    it.søknadsbehandling shouldBe oppdatertUtenEps
                }
            }
        }
    }

    @Test
    fun `fjerner skattemelding ved lagring av behandling`() {
        withMigratedDb { dataSource ->
            val testDataHelper = TestDataHelper(dataSource)
            val repo = testDataHelper.søknadsbehandlingRepo
            val persistertSkattegrunnlag = testDataHelper.persisterSkattegrunnlag(eps = nySkattegrunnlag())
            val behandling = persistertSkattegrunnlag.søknadsbehandling as VilkårsvurdertSøknadsbehandling.Uavklart
            val søkersId = persistertSkattegrunnlag.søkersSkatteId

            repo.hent(behandling.id)!!.let {
                it.grunnlagsdata.skattereferanser shouldNotBe null
                it.grunnlagsdata.skattereferanser!!.søkers shouldBe persistertSkattegrunnlag.søkersSkatteId
                it.grunnlagsdata.skattereferanser?.eps shouldNotBe null
                it.grunnlagsdata.skattereferanser?.eps shouldBe persistertSkattegrunnlag.epsSkatteId
            }

            repo.hentSkattegrunnlag(behandling.id)!!.let {
                it.eps shouldNotBe null
                it.søknadsbehandling shouldBe behandling
            }

            behandling.copy(
                grunnlagsdata = behandling.grunnlagsdata.copy(
                    skattereferanser = Skattereferanser(søkersId, null),
                ),
            ).also { oppdatertSøknadsbehandling ->
                repo.lagre(oppdatertSøknadsbehandling)
                repo.hent(oppdatertSøknadsbehandling.id)!!.let {
                    it.grunnlagsdata.skattereferanser shouldNotBe null
                    it.grunnlagsdata.skattereferanser!!.søkers shouldBe persistertSkattegrunnlag.søkersSkatteId
                    it.grunnlagsdata.skattereferanser?.eps shouldBe null
                }
                repo.hentSkattegrunnlag(oppdatertSøknadsbehandling.id)!!.let {
                    it.epsSkatteId shouldBe oppdatertSøknadsbehandling.grunnlagsdata.skattereferanser?.eps
                    it.eps shouldBe null
                    it.epsSkatteId shouldBe null
                    it.søknadsbehandling shouldBe oppdatertSøknadsbehandling
                }
            }
        }
    }

    private fun verifiserUteståendeAvkortingPåSak(
        sak: Sak,
        revurderingId: UUID,
        sakRepo: SakRepo,
    ) {
        sakRepo.hentSak(sak.id) shouldBe sak
        sak.uteståendeAvkorting.shouldBeInstanceOf<Avkortingsvarsel.Utenlandsopphold.SkalAvkortes>().also {
            it shouldBe Avkortingsvarsel.Utenlandsopphold.SkalAvkortes(
                objekt = Avkortingsvarsel.Utenlandsopphold.Opprettet(
                    id = it.id,
                    sakId = sak.id,
                    revurderingId = revurderingId,
                    simulering = it.simulering,
                    opprettet = it.opprettet,
                ),
            )
        }
    }
}
