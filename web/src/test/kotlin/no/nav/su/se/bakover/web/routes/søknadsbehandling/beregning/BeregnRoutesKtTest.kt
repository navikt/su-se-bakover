package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning

import com.nhaarman.mockitokotlin2.mock
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
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
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
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.VilkårsvurderRequest
import no.nav.su.se.bakover.service.vilkår.BosituasjonValg
import no.nav.su.se.bakover.service.vilkår.FullførBosituasjonRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilBosituasjonEpsRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevurderingRequest
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.applicationConfig
import no.nav.su.se.bakover.web.dbMetricsStub
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.fixedClock
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.Month
import java.util.UUID

internal class BeregnRoutesKtTest {

    private val stønadsperiode = Stønadsperiode.create(
        periode = Periode.create(1.januar(2021), 31.desember(2021)),
        begrunnelse = "begrunnelse",
    )
    private val repos = DatabaseBuilder.build(
        embeddedDatasource = EmbeddedDatabase.instance(),
        dbMetrics = dbMetricsStub,
    )
    private val services = ServiceBuilder.build(
        databaseRepos = repos,
        clients = TestClientsBuilder.build(applicationConfig),
        behandlingMetrics = mock(),
        søknadMetrics = mock(),
        clock = fixedClock,
        unleash = mock(),
    )

    @Test
    fun `opprette beregning for behandling`() {
        withTestApplication(
            {
                testSusebakover()
            },
        ) {
            val objects = setupMedAlleVilkårOppfylt()

            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.søknadsbehandling.id}/beregn",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    //language=JSON
                    """
                    {
                       "fradrag":[]
                    }
                    """.trimIndent(),
                )
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

    @Test
    fun `ikke lov å opprette fradrag utenfor perioden`() {
        withTestApplication(
            {
                testSusebakover()
            },
        ) {
            val objects = setupMedAlleVilkårOppfylt()
            val fradragFraOgMed = LocalDate.of(2020, Month.JANUARY, 1)
            val fradragTilOgMed = LocalDate.of(2021, Month.DECEMBER, 31)

            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.søknadsbehandling.id}/beregn",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    //language=JSON
                    """
                    {
                        "fradrag":[{
                          "type":"Arbeidsinntekt",
                            "beløp":200,
                            "utenlandskInntekt":null,
                            "periode" : {
                              "fraOgMed":"$fradragFraOgMed",
                              "tilOgMed":"$fradragTilOgMed"
                            },
                            "tilhører": "BRUKER"
                         }]
                    }
                    """.trimIndent(),
                )
            }.apply {
                assertSoftly {
                    response.status() shouldBe HttpStatusCode.BadRequest
                    response.content shouldContain "ikke_lov_med_fradrag_utenfor_perioden"
                }
            }
        }
    }

    @Test
    fun `Fradrag med utenlandskInntekt oppretter beregning`() {
        withTestApplication(
            {
                testSusebakover()
            },
        ) {
            val objects = setupMedAlleVilkårOppfylt()
            val fraOgMed = LocalDate.of(2021, Month.JANUARY, 1)
            val tilOgMed = LocalDate.of(2021, Month.DECEMBER, 31)

            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.søknadsbehandling.id}/beregn",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    //language=JSON
                    """
                    {
                       "fradrag":[{
                         "type":"Arbeidsinntekt",
                         "beløp":200,
                         "utenlandskInntekt":{
                            "beløpIUtenlandskValuta":200,
                            "valuta":"euro",
                            "kurs":0.5
                         },
                         "periode" : {
                            "fraOgMed":"$fraOgMed",
                            "tilOgMed":"$tilOgMed"
                         },
                         "tilhører": "BRUKER"
                      }]
                    }
                    """.trimIndent(),
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.Created
                val behandlingJson = deserialize<BehandlingJson>(response.content!!)
                behandlingJson.beregning!!.fraOgMed shouldBe fraOgMed.toString()
                behandlingJson.beregning.tilOgMed shouldBe tilOgMed.toString()
                behandlingJson.beregning.sats shouldBe Sats.HØY.name
                behandlingJson.beregning.månedsberegninger shouldHaveSize 12
                behandlingJson.beregning.fradrag shouldHaveSize 2 // input + 1 because of forventet inntekt
                behandlingJson.beregning.fradrag.filter { it.type == "Arbeidsinntekt" }.all {
                    it.utenlandskInntekt == UtenlandskInntektJson(
                        beløpIUtenlandskValuta = 200,
                        valuta = "euro",
                        kurs = 0.5,
                    )
                }
            }
        }
    }

    @Test
    fun `Fradrag med utenlandskInntekt er null oppretter beregning`() {
        withTestApplication(
            {
                testSusebakover()
            },
        ) {
            val objects = setupMedAlleVilkårOppfylt()
            val fraOgMed = LocalDate.of(2021, Month.JANUARY, 1)
            val tilOgMed = LocalDate.of(2021, Month.DECEMBER, 31)

            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.søknadsbehandling.id}/beregn",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    //language=JSON
                    """
                    {
                        "fradrag": [{
                            "type": "Arbeidsinntekt",
                            "beløp": 200,
                            "utenlandskInntekt": null,
                            "periode" : {
                                "fraOgMed":"$fraOgMed",
                                "tilOgMed":"$tilOgMed"
                            },
                            "tilhører": "BRUKER"
                        }]
                    }
                    """.trimIndent(),
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.Created
                val behandlingJson = deserialize<BehandlingJson>(response.content!!)
                behandlingJson.beregning!!.fraOgMed shouldBe fraOgMed.toString()
                behandlingJson.beregning.tilOgMed shouldBe tilOgMed.toString()
                behandlingJson.beregning.sats shouldBe Sats.HØY.name
                behandlingJson.beregning.månedsberegninger shouldHaveSize 12
                behandlingJson.beregning.fradrag shouldHaveSize 2 // input + 1 because of forventet inntekt
                behandlingJson.beregning.fradrag.all { it.utenlandskInntekt == null }
            }
        }
    }

    @Test
    fun `beregn error handling`() {
        withTestApplication(
            {
                testSusebakover()
            },
        ) {
            val objects = setup()
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
                setBody(
                    //language=JSON
                    """{
                           "fradrag":[{
                             "type":"Arbeidsinntekt",
                             "beløp":200,
                             "utenlandskInntekt": null,
                             "tilhører": "BRUKER"
                          }]
                        }
                    """.trimIndent(),
                )
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
            services.søknadsbehandling.vilkårsvurder(
                VilkårsvurderRequest(
                    objects.søknadsbehandling.id,
                    Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
                ),
            )

            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.søknadsbehandling.id}/beregn",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    //language=JSON
                    """{
                           "fradrag":[{
                             "type":"Arbeidsinntekt",
                             "beløp":200,
                             "utenlandskInntekt":{
                                "beløpIUtenlandskValuta":-200,
                                "valuta":"euro",
                                "kurs":0.5
                             },
                             "tilhører": "BRUKER"
                          }]
                        }
                    """.trimIndent(),
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
            }
        }
    }

    @Test
    fun `client notified about illegal operations on current state of behandling`() {
        withTestApplication(
            {
                testSusebakover()
            },
        ) {
            val objects = setup()
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
                response.content shouldContain "Ugyldig statusovergang"
                response.content shouldContain "TilBeregnet"
                response.content shouldContain "Vilkårsvurdert.Uavklart"
            }
        }
    }

    data class UavklartVilkårsvurdertSøknadsbehandling(
        val sak: Sak,
        val søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Uavklart,
    )

    private fun setup(): UavklartVilkårsvurdertSøknadsbehandling {
        val søknadInnhold = SøknadInnholdTestdataBuilder.build()
        val fnr: Fnr = FnrGenerator.random()
        SakFactory(clock = fixedClock).nySakMedNySøknad(fnr, søknadInnhold).also {
            repos.sak.opprettSak(it)
        }
        val sak: Sak = repos.sak.hentSak(fnr)!!
        val journalpostId = JournalpostId("12")
        val oppgaveId = OppgaveId("12")
        val søknadMedOppgave: Søknad.Journalført.MedOppgave = (sak.søknader[0] as Søknad.Ny)
            .journalfør(journalpostId).also { repos.søknad.oppdaterjournalpostId(it) }
            .medOppgave(oppgaveId).also { repos.søknad.oppdaterOppgaveId(it) }

        val nySøknadsbehandling = NySøknadsbehandling(
            id = UUID.randomUUID(),
            opprettet = sak.opprettet,
            sakId = sak.id,
            søknad = søknadMedOppgave,
            oppgaveId = OppgaveId("1234"),
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
            fnr = sak.fnr,
        )
        repos.søknadsbehandling.lagreNySøknadsbehandling(
            nySøknadsbehandling,
        )

        services.søknadsbehandling.oppdaterStønadsperiode(
            SøknadsbehandlingService.OppdaterStønadsperiodeRequest(
                behandlingId = nySøknadsbehandling.id,
                stønadsperiode = stønadsperiode,
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

    private fun setupMedAlleVilkårOppfylt(): InnvilgetVilkårsvurdertSøknadsbehandling {
        val objects = setup()

        val behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt()
        services.søknadsbehandling.leggTilUføregrunnlag(
            LeggTilUførevurderingRequest(
                behandlingId = objects.søknadsbehandling.id,
                periode = objects.søknadsbehandling.periode,
                uføregrad = Uføregrad.parse(behandlingsinformasjon.uførhet!!.uføregrad!!),
                forventetInntekt = behandlingsinformasjon.uførhet!!.forventetInntekt,
                oppfylt = behandlingsinformasjon.uførhet!!.status,
                begrunnelse = behandlingsinformasjon.uførhet!!.begrunnelse,
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
                begrunnelse = behandlingsinformasjon.bosituasjon?.begrunnelse,
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
