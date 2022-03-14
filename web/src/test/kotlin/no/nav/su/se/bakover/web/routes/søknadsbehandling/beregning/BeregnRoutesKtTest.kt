package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning

import arrow.core.nonEmptyListOf
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.BehandlingsStatus
import no.nav.su.se.bakover.domain.søknadsbehandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.ServiceBuilder
import no.nav.su.se.bakover.service.Services
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.søknadsbehandling.VilkårsvurderRequest
import no.nav.su.se.bakover.service.vilkår.BosituasjonValg
import no.nav.su.se.bakover.service.vilkår.FullførBosituasjonRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilBosituasjonEpsRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevurderingerRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUtenlandsoppholdRequest
import no.nav.su.se.bakover.service.vilkår.UførevilkårStatus
import no.nav.su.se.bakover.service.vilkår.UtenlandsoppholdStatus
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.periode2021
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.applicationConfig
import no.nav.su.se.bakover.web.dbMetricsStub
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.util.UUID
import javax.sql.DataSource

internal class BeregnRoutesKtTest {

    private val stønadsperiode = Stønadsperiode.create(
        periode = Periode.create(1.januar(2021), 31.desember(2021)),
        begrunnelse = "begrunnelse",
    )

    private fun repos(dataSource: DataSource) = DatabaseBuilder.build(
        embeddedDatasource = dataSource,
        dbMetrics = dbMetricsStub,
        clock = fixedClock,
    )

    private fun services(dataSource: DataSource, databaseRepos: DatabaseRepos = repos(dataSource)) =
        ServiceBuilder.build(
            databaseRepos = databaseRepos,
            clients = TestClientsBuilder(fixedClock, databaseRepos).build(applicationConfig),
            behandlingMetrics = mock(),
            søknadMetrics = mock(),
            clock = fixedClock,
            unleash = mock(),
        )

    @Test
    fun `opprette beregning for behandling`() {
        withMigratedDb { dataSource ->
            val repos = repos(dataSource)
            val services = services(dataSource, repos)
            val objects = setupMedAlleVilkårOppfylt(services, repos)

            withTestApplication(
                {
                    testSusebakover(
                        services = services,
                        databaseRepos = repos,
                    )
                },
            ) {

                defaultRequest(
                    HttpMethod.Post,
                    "$sakPath/${objects.sak.id}/behandlinger/${objects.søknadsbehandling.id}/beregn",
                    listOf(Brukerrolle.Saksbehandler),
                ) {
                    setBody("{}")
                }.apply {
                    response.status() shouldBe HttpStatusCode.Created
                    val behandlingJson = deserialize<BehandlingJson>(response.content!!)
                    behandlingJson.beregning!!.fraOgMed shouldBe stønadsperiode.periode.fraOgMed.toString()
                    behandlingJson.beregning.tilOgMed shouldBe stønadsperiode.periode.tilOgMed.toString()
                    behandlingJson.beregning.sats shouldBe Sats.HØY.name
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
            val objects = setup(services, repos)
            services.søknadsbehandling.leggTilBosituasjonEpsgrunnlag(
                request = LeggTilBosituasjonEpsRequest(behandlingId = objects.søknadsbehandling.id, epsFnr = null),
            )
            services.søknadsbehandling.vilkårsvurder(
                VilkårsvurderRequest(
                    objects.søknadsbehandling.id,
                    Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
                ),
            )
            withTestApplication(
                {
                    testSusebakover(
                        databaseRepos = repos,
                        services = services,
                    )
                },
            ) {
                defaultRequest(
                    HttpMethod.Post,
                    "$sakPath/${objects.sak.id}/behandlinger/blabla/beregn",
                    listOf(Brukerrolle.Saksbehandler),
                ) {}.apply {
                    assertSoftly {
                        response.status() shouldBe HttpStatusCode.BadRequest
                        response.content shouldContain "ikke en gyldig UUID"
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
                        response.status() shouldBe HttpStatusCode.NotFound
                        response.content shouldContain "Fant ikke behandling"
                    }
                }
                defaultRequest(
                    HttpMethod.Post,
                    "$sakPath/${objects.sak.id}/behandlinger/${objects.søknadsbehandling.id}/beregn",
                    listOf(Brukerrolle.Saksbehandler),
                ).apply {
                    assertSoftly {
                        response.status() shouldBe HttpStatusCode.BadRequest
                        response.content shouldContain "Ugyldig body"
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
            withTestApplication(
                {
                    testSusebakover(
                        services = services,
                        databaseRepos = repos,
                    )
                },
            ) {
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
                    response.status() shouldBe HttpStatusCode.BadRequest
                    response.content shouldContain """{"message":"Kan ikke gå fra tilstanden Uavklart til tilstanden Beregnet","code":"ugyldig_tilstand"}"""
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
        SakFactory(clock = fixedClock).nySakMedNySøknad(fnr, søknadInnhold).also {
            repos.sak.opprettSak(it)
        }
        val sak: Sak = repos.sak.hentSak(fnr)!!
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

        val behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt()
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
        services.søknadsbehandling.leggTilUtenlandsopphold(
            request = LeggTilUtenlandsoppholdRequest(
                behandlingId = objects.søknadsbehandling.id,
                periode = periode2021,
                status = UtenlandsoppholdStatus.SkalHoldeSegINorge,
                begrunnelse = "Veldig bra",
            ),
        )
        services.søknadsbehandling.leggTilBosituasjonEpsgrunnlag(
            LeggTilBosituasjonEpsRequest(
                behandlingId = objects.søknadsbehandling.id,
                epsFnr = null,
            ),
        )
        services.søknadsbehandling.fullførBosituasjongrunnlag(
            FullførBosituasjonRequest(
                behandlingId = objects.søknadsbehandling.id,
                bosituasjon = BosituasjonValg.BOR_ALENE,
                begrunnelse = "fullførBosituasjongrunnlag begrunnelse",
            ),
        )
        services.søknadsbehandling.vilkårsvurder(
            VilkårsvurderRequest(
                objects.søknadsbehandling.id,
                behandlingsinformasjon,
            ),
        )

        return InnvilgetVilkårsvurdertSøknadsbehandling(
            repos.sak.hentSak(objects.sak.id)!!,
            repos.søknadsbehandling.hent(objects.søknadsbehandling.id) as Søknadsbehandling.Vilkårsvurdert.Innvilget,
        )
    }
}
