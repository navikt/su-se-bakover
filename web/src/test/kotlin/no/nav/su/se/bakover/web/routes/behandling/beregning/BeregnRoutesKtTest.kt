package no.nav.su.se.bakover.web.routes.behandling.beregning

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
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.extractBehandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.service.ProdServiceBuilder
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.applicationConfig
import no.nav.su.se.bakover.web.behandlingFactory
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.fixedClock
import no.nav.su.se.bakover.web.routes.behandling.BehandlingJson
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.Month
import java.util.UUID

internal class BeregnRoutesKtTest {

    private val saksbehandler = NavIdentBruker.Saksbehandler("AB12345")

    private val repos = DatabaseBuilder.build(EmbeddedDatabase.instance(), behandlingFactory)
    private val services = ProdServiceBuilder.build(
        databaseRepos = repos,
        clients = TestClientsBuilder.build(applicationConfig),
        behandlingMetrics = mock(),
        søknadMetrics = mock(),
        clock = fixedClock,
        unleash = mock(),
    )

    @Test
    fun `opprette beregning for behandling`() {
        withTestApplication({
            testSusebakover()
        }) {
            val objects = setup()
            val fraOgMed = LocalDate.of(2021, Month.JANUARY, 1)
            val tilOgMed = LocalDate.of(2021, Month.DECEMBER, 31)

            services.behandling.oppdaterBehandlingsinformasjon(
                objects.nySøknadsbehandling.id,
                saksbehandler,
                extractBehandlingsinformasjon(objects.behandling).withAlleVilkårOppfylt()
            )

            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.nySøknadsbehandling.id}/beregn",
                listOf(Brukerrolle.Saksbehandler)
            ) {
                setBody(
                    //language=JSON
                    """
                    {
                      "stønadsperiode": {
                        "fraOgMed":"$fraOgMed",
                        "tilOgMed":"$tilOgMed"
                       },
                       "fradrag":[]
                    }
                    """.trimIndent()
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.Created
                val behandlingJson = deserialize<BehandlingJson>(response.content!!)
                behandlingJson.beregning!!.fraOgMed shouldBe fraOgMed.toString()
                behandlingJson.beregning.tilOgMed shouldBe tilOgMed.toString()
                behandlingJson.beregning.sats shouldBe Sats.HØY.name
                behandlingJson.beregning.månedsberegninger shouldHaveSize 12
            }
        }
    }

    @Test
    fun `ikke lov å opprette fradrag utenfor perioden`() {
        withTestApplication({
            testSusebakover()
        }) {
            val objects = setup()
            val fraOgMed = LocalDate.of(2021, Month.FEBRUARY, 1)
            val tilOgMed = LocalDate.of(2021, Month.DECEMBER, 31)
            val fradragFraOgMed = LocalDate.of(2021, Month.JANUARY, 1)
            val fradragTilOgMed = LocalDate.of(2021, Month.DECEMBER, 31)

            services.behandling.oppdaterBehandlingsinformasjon(
                objects.nySøknadsbehandling.id,
                saksbehandler,
                extractBehandlingsinformasjon(objects.behandling).withAlleVilkårOppfylt()
            )

            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.nySøknadsbehandling.id}/beregn",
                listOf(Brukerrolle.Saksbehandler)
            ) {
                setBody(
                    //language=JSON
                    """
                    {
                      "stønadsperiode": {
                        "fraOgMed":"$fraOgMed",
                        "tilOgMed":"$tilOgMed"
                       },
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
                    """.trimIndent()
                )
            }.apply {
                assertSoftly {
                    response.status() shouldBe HttpStatusCode.BadRequest
                    response.content shouldContain "Fradragsperioden kan ikke være utenfor stønadsperioden"
                }
            }
        }
    }

    @Test
    fun `Fradrag med utenlandskInntekt oppretter beregning`() {
        withTestApplication({
            testSusebakover()
        }) {
            val objects = setup()
            val fraOgMed = LocalDate.of(2021, Month.JANUARY, 1)
            val tilOgMed = LocalDate.of(2021, Month.DECEMBER, 31)

            services.behandling.oppdaterBehandlingsinformasjon(
                objects.nySøknadsbehandling.id,
                saksbehandler,
                extractBehandlingsinformasjon(objects.behandling).withAlleVilkårOppfylt()
            )

            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.nySøknadsbehandling.id}/beregn",
                listOf(Brukerrolle.Saksbehandler)
            ) {
                setBody(
                    //language=JSON
                    """
                    {
                      "stønadsperiode": {
                           "fraOgMed":"$fraOgMed",
                           "tilOgMed":"$tilOgMed"
                       },
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
                    """.trimIndent()
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
                        kurs = 0.5
                    )
                }
            }
        }
    }

    @Test
    fun `Fradrag med utenlandskInntekt er null oppretter beregning`() {
        withTestApplication({
            testSusebakover()
        }) {
            val objects = setup()
            val fraOgMed = LocalDate.of(2021, Month.JANUARY, 1)
            val tilOgMed = LocalDate.of(2021, Month.DECEMBER, 31)

            services.behandling.oppdaterBehandlingsinformasjon(
                objects.nySøknadsbehandling.id,
                saksbehandler,
                extractBehandlingsinformasjon(objects.behandling).withAlleVilkårOppfylt()
            )

            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.nySøknadsbehandling.id}/beregn",
                listOf(Brukerrolle.Saksbehandler)
            ) {
                setBody(
                    //language=JSON
                    """
                    {
                        "stønadsperiode": {
                            "fraOgMed":"$fraOgMed",
                            "tilOgMed":"$tilOgMed"
                        },
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
                    """.trimIndent()
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
        withTestApplication({
            testSusebakover()
        }) {
            val objects = setup()
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/blabla/beregn",
                listOf(Brukerrolle.Saksbehandler)
            ) {}.apply {
                assertSoftly {
                    response.status() shouldBe HttpStatusCode.BadRequest
                    response.content shouldContain "ikke en gyldig UUID"
                }
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${UUID.randomUUID()}/beregn",
                listOf(Brukerrolle.Saksbehandler)
            ).apply {
                assertSoftly {
                    response.status() shouldBe HttpStatusCode.BadRequest
                    response.content shouldContain "Ugyldig body"
                }
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.nySøknadsbehandling.id}/beregn",
                listOf(Brukerrolle.Saksbehandler)
            ).apply {
                assertSoftly {
                    response.status() shouldBe HttpStatusCode.BadRequest
                    response.content shouldContain "Ugyldig body"
                }
            }
            val fraOgMed = LocalDate.of(2021, Month.JANUARY, 1)
            val tilOgMed = LocalDate.of(2021, Month.DECEMBER, 31)

            objects.behandling.oppdaterBehandlingsinformasjon(
                saksbehandler,
                extractBehandlingsinformasjon(objects.behandling).withAlleVilkårOppfylt()
            )

            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.nySøknadsbehandling.id}/beregn",
                listOf(Brukerrolle.Saksbehandler)
            ) {
                setBody(
                    //language=JSON
                    """{
                          "stønadsperiode": {
                               "fraOgMed":"$fraOgMed",
                               "tilOgMed":"$tilOgMed"
                           },
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
                    """.trimIndent()
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
            }
        }
    }

    @Test
    fun `client notified about illegal operations on current state of behandling`() {
        withTestApplication({
            testSusebakover()
        }) {
            val objects = setup()
            objects.behandling.status() shouldBe Behandling.BehandlingsStatus.OPPRETTET

            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.nySøknadsbehandling.id}/beregn",
                listOf(Brukerrolle.Saksbehandler)
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
                    """.trimIndent()
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "Ugyldig statusovergang"
                response.content shouldContain "TilBeregnet"
                response.content shouldContain "Vilkårsvurdert.Uavklart"
            }
        }
    }

    @Test
    fun `ikke lov å beregne tidligere enn 2021`() {
        withTestApplication({
            testSusebakover()
        }) {
            val objects = setup()
            val fraOgMed = LocalDate.of(2020, Month.JANUARY, 1)
            val tilOgMed = LocalDate.of(2020, Month.DECEMBER, 31)

            services.behandling.oppdaterBehandlingsinformasjon(
                objects.nySøknadsbehandling.id,
                saksbehandler,
                extractBehandlingsinformasjon(objects.behandling).withAlleVilkårOppfylt()
            )

            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.nySøknadsbehandling.id}/beregn",
                listOf(Brukerrolle.Saksbehandler)
            ) {
                setBody(
                    //language=JSON
                    """
                    {
                      "stønadsperiode": {
                           "fraOgMed":"$fraOgMed",
                           "tilOgMed":"$tilOgMed"
                       },
                        "fradrag":[]
                    }
                    """.trimIndent()
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "En stønadsperiode kan ikke starte før 2021"
            }
        }
    }

    @Test
    fun `ikke lov å beregne fra en dato som ikke er første dag i en måned`() {
        withTestApplication({
            testSusebakover()
        }) {
            val objects = setup()
            val fraOgMed = LocalDate.of(2021, Month.JANUARY, 2)
            val tilOgMed = LocalDate.of(2021, Month.DECEMBER, 31)

            services.behandling.oppdaterBehandlingsinformasjon(
                objects.nySøknadsbehandling.id,
                saksbehandler,
                extractBehandlingsinformasjon(objects.behandling).withAlleVilkårOppfylt()
            )

            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.nySøknadsbehandling.id}/beregn",
                listOf(Brukerrolle.Saksbehandler)
            ) {
                setBody(
                    //language=JSON
                    """
                    {
                      "stønadsperiode": {
                           "fraOgMed":"$fraOgMed",
                           "tilOgMed":"$tilOgMed"
                       },
                        "fradrag":[]
                    }
                    """.trimIndent()
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "Perioder kan kun starte på første dag i måneden"
            }
        }
    }

    @Test
    fun `ikke lov å beregne til en dato som ikke er siste dag i en måned`() {
        withTestApplication({
            testSusebakover()
        }) {
            val objects = setup()
            val fraOgMed = LocalDate.of(2021, Month.JANUARY, 1)
            val tilOgMed = LocalDate.of(2021, Month.DECEMBER, 30)

            services.behandling.oppdaterBehandlingsinformasjon(
                objects.nySøknadsbehandling.id,
                saksbehandler,
                extractBehandlingsinformasjon(objects.behandling).withAlleVilkårOppfylt()
            )

            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.nySøknadsbehandling.id}/beregn",
                listOf(Brukerrolle.Saksbehandler)
            ) {
                setBody(
                    //language=JSON
                    """
                    {
                      "stønadsperiode": {
                           "fraOgMed":"$fraOgMed",
                           "tilOgMed":"$tilOgMed"
                       },
                        "fradrag":[]
                    }
                    """.trimIndent()
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "Perioder kan kun avsluttes siste dag i måneden"
            }
        }
    }

    @Test
    fun `ikke lov å beregne en periode som er mer enn 12 måneder`() {
        withTestApplication({
            testSusebakover()
        }) {
            val objects = setup()
            val fraOgMed = LocalDate.of(2021, Month.JANUARY, 1)
            val tilOgMed = LocalDate.of(2022, Month.JANUARY, 31)

            services.behandling.oppdaterBehandlingsinformasjon(
                objects.nySøknadsbehandling.id,
                saksbehandler,
                extractBehandlingsinformasjon(objects.behandling).withAlleVilkårOppfylt()
            )

            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.nySøknadsbehandling.id}/beregn",
                listOf(Brukerrolle.Saksbehandler)
            ) {
                setBody(
                    //language=JSON
                    """
                    {
                      "stønadsperiode": {
                        "fraOgMed":"$fraOgMed",
                        "tilOgMed":"$tilOgMed"
                       },
                        "fradrag":[]
                    }
                    """.trimIndent()
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "En stønadsperiode kan være maks 12 måneder"
            }
        }
    }

    @Test
    fun `ikke lov å beregne en periode som starter før den slutter`() {
        withTestApplication({
            testSusebakover()
        }) {
            val objects = setup()
            val fraOgMed = LocalDate.of(2021, Month.FEBRUARY, 1)
            val tilOgMed = LocalDate.of(2021, Month.JANUARY, 31)

            services.behandling.oppdaterBehandlingsinformasjon(
                objects.nySøknadsbehandling.id,
                saksbehandler,
                extractBehandlingsinformasjon(objects.behandling).withAlleVilkårOppfylt()
            )

            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.nySøknadsbehandling.id}/beregn",
                listOf(Brukerrolle.Saksbehandler)
            ) {
                setBody(
                    //language=JSON
                    """
                    {
                      "stønadsperiode": {
                           "fraOgMed":"$fraOgMed",
                           "tilOgMed":"$tilOgMed"
                       },
                        "fradrag":[]
                    }
                    """.trimIndent()
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "Startmåned må være tidligere eller lik sluttmåned"
            }
        }
    }

    @Test
    fun `skal være lov å beregne en periode på bare 1 mnd`() {
        withTestApplication({
            testSusebakover()
        }) {
            val objects = setup()
            val fraOgMed = LocalDate.of(2021, Month.JANUARY, 1)
            val tilOgMed = LocalDate.of(2021, Month.JANUARY, 31)

            services.behandling.oppdaterBehandlingsinformasjon(
                objects.nySøknadsbehandling.id,
                saksbehandler,
                extractBehandlingsinformasjon(objects.behandling).withAlleVilkårOppfylt()
            )

            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.nySøknadsbehandling.id}/beregn",
                listOf(Brukerrolle.Saksbehandler)
            ) {
                setBody(
                    //language=JSON
                    """
                    {
                      "stønadsperiode": {
                           "fraOgMed":"$fraOgMed",
                           "tilOgMed":"$tilOgMed"
                       },
                        "fradrag":[]
                    }
                    """.trimIndent()
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.Created
            }
        }
    }

    data class Objects(
        val sak: Sak,
        val søknad: Søknad,
        val nySøknadsbehandling: NySøknadsbehandling,
        val behandling: Behandling
    )

    private fun setup(): Objects {
        val søknadInnhold = SøknadInnholdTestdataBuilder.build()
        val fnr: Fnr = FnrGenerator.random()
        SakFactory(clock = fixedClock).nySak(fnr, søknadInnhold).also {
            repos.sak.opprettSak(it)
        }
        val sak: Sak = repos.sak.hentSak(fnr)!!

        val søknadId: UUID = sak.søknader()[0].id

        repos.søknad.oppdaterjournalpostId(søknadId, JournalpostId("12"))
        repos.søknad.oppdaterOppgaveId(søknadId, OppgaveId("12"))

        val nySøknadsbehandling = NySøknadsbehandling(
            sakId = sak.id,
            søknadId = søknadId,
            oppgaveId = OppgaveId("1234")
        )
        repos.behandling.opprettSøknadsbehandling(
            nySøknadsbehandling
        )
        return Objects(
            repos.sak.hentSak(sak.id)!!,
            repos.søknad.hentSøknad(søknadId)!!,
            nySøknadsbehandling,
            repos.behandling.hentBehandling(nySøknadsbehandling.id)!!
        )
    }
}
