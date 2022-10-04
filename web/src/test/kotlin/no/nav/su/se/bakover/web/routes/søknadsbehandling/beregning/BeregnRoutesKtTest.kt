import arrow.core.nonEmptyListOf
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.call.body
import io.ktor.client.request.setBody
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknadsbehandling.BehandlingsStatus
import no.nav.su.se.bakover.domain.søknadsbehandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.ServiceBuilder
import no.nav.su.se.bakover.service.Services
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.vilkår.BosituasjonValg
import no.nav.su.se.bakover.service.vilkår.FullførBosituasjonRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilBosituasjonEpsRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilFastOppholdINorgeRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilFlereUtenlandsoppholdRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilFlyktningVilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilFormuevilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilInstitusjonsoppholdVilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilLovligOppholdRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilPersonligOppmøteVilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevurderingerRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUtenlandsoppholdRequest
import no.nav.su.se.bakover.service.vilkår.LovligOppholdVilkårStatus
import no.nav.su.se.bakover.service.vilkår.LovligOppholdVurderinger
import no.nav.su.se.bakover.service.vilkår.UførevilkårStatus
import no.nav.su.se.bakover.service.vilkår.UtenlandsoppholdStatus
import no.nav.su.se.bakover.test.applicationConfig
import no.nav.su.se.bakover.test.empty
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.satsFactoryTest
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.veileder
import no.nav.su.se.bakover.test.vilkår.fastOppholdVilkårInnvilget
import no.nav.su.se.bakover.test.vilkår.flyktningVilkårInnvilget
import no.nav.su.se.bakover.test.vilkår.institusjonsoppholdvilkårInnvilget
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.dbMetricsStub
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import vilkår.personligOppmøtevilkårInnvilget
import java.util.UUID
import javax.sql.DataSource

internal class BeregnRoutesKtTest {

    private val stønadsperiode = Stønadsperiode.create(periode = år(2021))

    private fun repos(dataSource: DataSource) = DatabaseBuilder.build(
        embeddedDatasource = dataSource,
        dbMetrics = dbMetricsStub,
        clock = fixedClock,
        satsFactory = satsFactoryTest,
    )

    private fun services(dataSource: DataSource, databaseRepos: DatabaseRepos = repos(dataSource)) =
        ServiceBuilder.build(
            databaseRepos = databaseRepos,
            clients = TestClientsBuilder(fixedClock, databaseRepos).build(applicationConfig()),
            behandlingMetrics = mock(),
            søknadMetrics = mock(),
            clock = fixedClock,
            unleash = mock(),
            satsFactory = satsFactoryTestPåDato(),
            applicationConfig = applicationConfig(),
        )

    @Test
    fun `opprette beregning for behandling`() {
        withMigratedDb { dataSource ->
            val repos = repos(dataSource)
            val services = services(dataSource, repos)
            val objects = setupMedAlleVilkårOppfylt(services, repos)

            testApplication {
                application {
                    testSusebakover(
                        services = services,
                        databaseRepos = repos,
                    )
                }
                defaultRequest(
                    HttpMethod.Post,
                    "$sakPath/${objects.sak.id}/behandlinger/${objects.søknadsbehandling.id}/beregn",
                    listOf(Brukerrolle.Saksbehandler),
                ) {
                    setBody("{}")
                }.apply {
                    status shouldBe HttpStatusCode.Created
                    val behandlingJson = deserialize<BehandlingJson>(body())
                    behandlingJson.beregning!!.fraOgMed shouldBe stønadsperiode.periode.fraOgMed.toString()
                    behandlingJson.beregning.tilOgMed shouldBe stønadsperiode.periode.tilOgMed.toString()
                    behandlingJson.beregning.månedsberegninger shouldHaveSize 12
                }
            }
        }
    }

    @Test
    fun `beregn error handling`() {
        withMigratedDb { dataSource ->
            val repos = repos(dataSource)
            val services = services(dataSource, repos)
            val objects = setupMedAlleVilkårOppfylt(services, repos)

            testApplication {
                defaultRequest(
                    HttpMethod.Post,
                    "$sakPath/${objects.sak.id}/behandlinger/blabla/beregn",
                    listOf(Brukerrolle.Saksbehandler),
                ) {
                    application {
                        testSusebakover(
                            databaseRepos = repos,
                            services = services,
                        )
                    }
                }.apply {
                    assertSoftly {
                        status shouldBe HttpStatusCode.BadRequest
                        body<String>() shouldContain "ikke en gyldig UUID"
                    }
                }
                defaultRequest(
                    HttpMethod.Post,
                    "$sakPath/${objects.sak.id}/behandlinger/${UUID.randomUUID()}/beregn",
                    listOf(Brukerrolle.Saksbehandler),
                ) {
                    setBody("{}")
                }.apply {
                    assertSoftly {
                        status shouldBe HttpStatusCode.NotFound
                        body<String>() shouldContain "Fant ikke behandling"
                    }
                }
                defaultRequest(
                    HttpMethod.Post,
                    "$sakPath/${objects.sak.id}/behandlinger/${objects.søknadsbehandling.id}/beregn",
                    listOf(Brukerrolle.Saksbehandler),
                ).apply {
                    assertSoftly {
                        status shouldBe HttpStatusCode.BadRequest
                        body<String>() shouldContain "Ugyldig body"
                    }
                }
            }
        }
    }

    @Test
    fun `client notified about illegal operations on current state of behandling`() {
        withMigratedDb { dataSource ->
            val repos = repos(dataSource)
            val services = services(dataSource, repos)
            val objects = setup(services, repos)
            testApplication {
                application {
                    testSusebakover(
                        databaseRepos = repos,
                        services = services,
                    )
                }
                services.søknadsbehandling.leggTilBosituasjonEpsgrunnlag(
                    LeggTilBosituasjonEpsRequest(
                        behandlingId = objects.søknadsbehandling.id,
                        epsFnr = null,
                    ),
                )
                objects.søknadsbehandling.status shouldBe BehandlingsStatus.OPPRETTET

                defaultRequest(
                    HttpMethod.Post,
                    "$sakPath/${objects.sak.id}/behandlinger/${objects.søknadsbehandling.id}/beregn",
                    listOf(Brukerrolle.Saksbehandler),
                ) {
                    setBody(
                        //language=JSON
                        """
                    {
                        "stønadsperiode": {
                            "fraOgMed":"${1.januar(2021)}",
                            "tilOgMed":"${31.desember(2021)}"
                        },
                        "fradrag":[]
                    }
                        """.trimIndent(),
                    )
                }.apply {
                    status shouldBe HttpStatusCode.BadRequest
                    this.body<String>() shouldContain """{"message":"Kan ikke gå fra tilstanden Uavklart til tilstanden Beregnet","code":"ugyldig_tilstand"}"""
                }
            }
        }
    }

    data class UavklartVilkårsvurdertSøknadsbehandling(
        val sak: Sak,
        val søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Uavklart,
    )

    private fun setup(services: Services, repos: DatabaseRepos): UavklartVilkårsvurdertSøknadsbehandling {
        val søknadInnhold = SøknadInnholdTestdataBuilder.build()
        val fnr: Fnr = Fnr.generer()
        SakFactory(clock = fixedClock).nySakMedNySøknad(
            fnr = fnr,
            søknadInnhold = søknadInnhold,
            innsendtAv = veileder,
        ).also {
            repos.sak.opprettSak(it)
        }
        val sak: Sak = repos.sak.hentSak(fnr, Sakstype.UFØRE)!!
        val journalpostId = JournalpostId("12")
        val oppgaveId = OppgaveId("12")
        val søknadMedOppgave: Søknad.Journalført.MedOppgave.IkkeLukket = (sak.søknader[0] as Søknad.Ny)
            .journalfør(journalpostId).also { repos.søknad.oppdaterjournalpostId(it) }
            .medOppgave(oppgaveId).also { repos.søknad.oppdaterOppgaveId(it) }

        val nySøknadsbehandling = NySøknadsbehandling(
            id = UUID.randomUUID(),
            opprettet = sak.opprettet,
            sakId = sak.id,
            søknad = søknadMedOppgave,
            oppgaveId = OppgaveId("1234"),
            fnr = sak.fnr,
            avkorting = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående.kanIkke(),
            sakstype = Sakstype.UFØRE,
        )
        repos.søknadsbehandling.lagreNySøknadsbehandling(
            nySøknadsbehandling,
        )

        services.søknadsbehandling.oppdaterStønadsperiode(
            SøknadsbehandlingService.OppdaterStønadsperiodeRequest(
                behandlingId = nySøknadsbehandling.id,
                stønadsperiode = stønadsperiode,
                sakId = sak.id,
            ),
        )

        return UavklartVilkårsvurdertSøknadsbehandling(
            repos.sak.hentSak(sak.id)!!,
            repos.søknadsbehandling.hent(nySøknadsbehandling.id) as Søknadsbehandling.Vilkårsvurdert.Uavklart,
        )
    }

    data class InnvilgetVilkårsvurdertSøknadsbehandling(
        val sak: Sak,
        val søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Innvilget,
    )

    private fun setupMedAlleVilkårOppfylt(
        services: Services,
        repos: DatabaseRepos,
    ): InnvilgetVilkårsvurdertSøknadsbehandling {
        val objects = setup(services, repos)

        /**
         *  Legges til automatisk dersom det ikke er eksplisitt lagt til fra før.
         services.søknadsbehandling.leggTilOpplysningspliktVilkår(
         request = LeggTilOpplysningspliktRequest.Søknadsbehandling(
         behandlingId = uavklartVilkårsvurdertSøknadsbehandling.søknadsbehandling.id,
         vilkår = tilstrekkeligDokumentert(periode = år(2021))
         )
         )
         */
        services.søknadsbehandling.leggTilUførevilkår(
            LeggTilUførevurderingerRequest(
                behandlingId = objects.søknadsbehandling.id,
                vurderinger = nonEmptyListOf(
                    LeggTilUførevilkårRequest(
                        behandlingId = objects.søknadsbehandling.id,
                        periode = objects.søknadsbehandling.periode,
                        uføregrad = Uføregrad.parse(100),
                        forventetInntekt = 0,
                        oppfylt = UførevilkårStatus.VilkårOppfylt,
                        begrunnelse = "Må få være ufør vel",
                    ),
                ),
            ),
        )
        services.søknadsbehandling.leggTilLovligOpphold(
            LeggTilLovligOppholdRequest(
                behandlingId = objects.søknadsbehandling.id,
                vurderinger = listOf(
                    LovligOppholdVurderinger(periode = år(2021), status = LovligOppholdVilkårStatus.VilkårOppfylt),
                ),
            ),
        )
        services.søknadsbehandling.leggTilUtenlandsopphold(
            LeggTilFlereUtenlandsoppholdRequest(
                behandlingId = objects.søknadsbehandling.id,
                request = nonEmptyListOf(
                    LeggTilUtenlandsoppholdRequest(
                        behandlingId = objects.søknadsbehandling.id,
                        periode = år(2021),
                        status = UtenlandsoppholdStatus.SkalHoldeSegINorge,
                    ),
                ),
            ),
        )
        services.søknadsbehandling.leggTilBosituasjonEpsgrunnlag(
            LeggTilBosituasjonEpsRequest(
                behandlingId = objects.søknadsbehandling.id,
                epsFnr = null,
            ),
        )
        services.søknadsbehandling.leggTilFormuevilkår(
            LeggTilFormuevilkårRequest(
                behandlingId = objects.søknadsbehandling.id,
                formuegrunnlag = nonEmptyListOf(
                    LeggTilFormuevilkårRequest.Grunnlag.Søknadsbehandling(
                        periode = år(2021),
                        epsFormue = null,
                        søkersFormue = Formuegrunnlag.Verdier.empty(),
                        begrunnelse = "søknadsbehandling.leggTilFormuegrunnlag",
                        måInnhenteMerInformasjon = false,
                    ),
                ),
            ),
        )
        services.søknadsbehandling.leggTilFlyktningVilkår(
            request = LeggTilFlyktningVilkårRequest(
                behandlingId = objects.søknadsbehandling.id,
                vilkår = flyktningVilkårInnvilget(periode = objects.søknadsbehandling.periode),
            ),
        )
        services.søknadsbehandling.leggTilFastOppholdINorgeVilkår(
            request = LeggTilFastOppholdINorgeRequest(
                behandlingId = objects.søknadsbehandling.id,
                vilkår = fastOppholdVilkårInnvilget(periode = objects.søknadsbehandling.periode),
            ),
        )
        services.søknadsbehandling.leggTilPersonligOppmøteVilkår(
            request = LeggTilPersonligOppmøteVilkårRequest(
                behandlingId = objects.søknadsbehandling.id,
                vilkår = personligOppmøtevilkårInnvilget(periode = objects.søknadsbehandling.periode),
            ),
        )
        services.søknadsbehandling.fullførBosituasjongrunnlag(
            FullførBosituasjonRequest(
                behandlingId = objects.søknadsbehandling.id,
                bosituasjon = BosituasjonValg.BOR_ALENE,
            ),
        )
        services.søknadsbehandling.leggTilInstitusjonsoppholdVilkår(
            LeggTilInstitusjonsoppholdVilkårRequest(
                behandlingId = objects.søknadsbehandling.id,
                vilkår = institusjonsoppholdvilkårInnvilget(),
            ),
        )
        return InnvilgetVilkårsvurdertSøknadsbehandling(
            repos.sak.hentSak(objects.sak.id)!!,
            repos.søknadsbehandling.hent(objects.søknadsbehandling.id) as Søknadsbehandling.Vilkårsvurdert.Innvilget,
        )
    }
}
