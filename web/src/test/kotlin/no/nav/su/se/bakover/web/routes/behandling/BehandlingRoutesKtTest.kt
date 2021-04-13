package no.nav.su.se.bakover.web.routes.behandling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import com.nhaarman.mockitokotlin2.mock
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.ServiceBuilder
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.BeregnRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.SendTilAttesteringRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.SimulerRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService.VilkårsvurderRequest
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.TestClientsBuilder.testClients
import no.nav.su.se.bakover.web.applicationConfig
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.fixedClock
import no.nav.su.se.bakover.web.jwtStub
import no.nav.su.se.bakover.web.requestSomAttestant
import no.nav.su.se.bakover.web.routes.behandling.BehandlingTestUtils.behandlingsperiode
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.stubs.asBearerToken
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

internal class BehandlingRoutesKtTest {

    private val saksbehandler = NavIdentBruker.Saksbehandler("AB12345")

    private val repos = DatabaseBuilder.build(EmbeddedDatabase.instance())
    private val services = ServiceBuilder.build(
        databaseRepos = repos,
        clients = TestClientsBuilder.build(applicationConfig),
        behandlingMetrics = mock(),
        søknadMetrics = mock(),
        clock = fixedClock,
        unleash = mock(),
    )

    @Nested
    inner class `Henting av behandling` {
        @Test
        fun `Forbidden når bruker bare er veileder`() {
            withTestApplication({
                testSusebakover(services = services)
            }) {
                val objects = setup()
                defaultRequest(
                    HttpMethod.Get,
                    "$sakPath/${objects.sak.id}/behandlinger/${objects.søknadsbehandling.id}",
                    listOf(Brukerrolle.Veileder)
                ).apply {
                    response.status() shouldBe HttpStatusCode.Forbidden
                }
            }
        }

        @Test
        fun `OK når bruker er saksbehandler`() {
            withTestApplication({
                testSusebakover(services = services)
            }) {
                val objects = setup()
                defaultRequest(
                    HttpMethod.Get,
                    "$sakPath/${objects.sak.id}/behandlinger/${objects.søknadsbehandling.id}",
                    listOf(Brukerrolle.Saksbehandler)
                ).apply {
                    objectMapper.readValue<BehandlingJson>(response.content!!).let {
                        it.id shouldBe objects.søknadsbehandling.id.toString()
                        it.behandlingsinformasjon shouldNotBe null
                        it.søknad.id shouldBe objects.søknad.id.toString()
                    }
                }
            }
        }

        @Test
        fun `OK når bruker er attestant`() {
            withTestApplication({
                testSusebakover(services = services)
            }) {
                val objects = setup()
                defaultRequest(
                    HttpMethod.Get,
                    "$sakPath/${objects.sak.id}/behandlinger/${objects.søknadsbehandling.id}",
                    listOf(Brukerrolle.Attestant)
                ).apply {
                    objectMapper.readValue<BehandlingJson>(response.content!!).let {
                        it.id shouldBe objects.søknadsbehandling.id.toString()
                        it.behandlingsinformasjon shouldNotBe null
                        it.søknad.id shouldBe objects.søknad.id.toString()
                    }
                }
            }
        }
    }

    @Test
    fun `Opprette en oppgave til attestering er OK`() {
        withTestApplication({
            testSusebakover(services = services)
        }) {
            val objects = setup()
            services.søknadsbehandling.vilkårsvurder(
                VilkårsvurderRequest(
                    objects.søknadsbehandling.id,
                    saksbehandler,
                    Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt()
                )
            )
            services.søknadsbehandling.beregn(
                BeregnRequest(
                    behandlingId = objects.søknadsbehandling.id,
                    fradrag = emptyList(),
                    begrunnelse = null
                )
            )
            services.søknadsbehandling.simuler(SimulerRequest(objects.søknadsbehandling.id, saksbehandler))
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.søknadsbehandling.id}/tilAttestering",
                listOf(Brukerrolle.Saksbehandler)
            ) {
                setBody("""{ "fritekst": "Fritekst!" }""")
            }.apply {
                response.status() shouldBe HttpStatusCode.OK
                val behandlingJson = deserialize<BehandlingJson>(response.content!!)
                behandlingJson.status shouldBe "TIL_ATTESTERING_INNVILGET"
            }
        }
    }

    @Test
    fun `Opprette en oppgave til attestering feiler mot oppgave`() {
        withTestApplication({
            testSusebakover(
                clients = testClients.copy(
                    oppgaveClient = object : OppgaveClient {
                        override fun opprettOppgave(config: OppgaveConfig): Either<KunneIkkeOppretteOppgave, OppgaveId> {
                            return Either.left(KunneIkkeOppretteOppgave)
                        }

                        override fun opprettOppgaveMedSystembruker(config: OppgaveConfig): Either<KunneIkkeOppretteOppgave, OppgaveId> {
                            return Either.left(KunneIkkeOppretteOppgave)
                        }

                        override fun lukkOppgave(oppgaveId: OppgaveId) = Unit.right()
                        override fun lukkOppgaveMedSystembruker(oppgaveId: OppgaveId) = Unit.right()
                    }
                )
            )
        }) {
            val objects = setup()
            services.søknadsbehandling.vilkårsvurder(
                VilkårsvurderRequest(
                    objects.søknadsbehandling.id,
                    saksbehandler,
                    Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt()
                )
            )
            services.søknadsbehandling.beregn(
                BeregnRequest(
                    behandlingId = objects.søknadsbehandling.id,
                    fradrag = emptyList(),
                    begrunnelse = null,
                )
            )
            services.søknadsbehandling.simuler(
                SimulerRequest(
                    objects.søknadsbehandling.id, saksbehandler
                )
            )
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.søknadsbehandling.id}/tilAttestering",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{ "fritekst": "Fritekst!" }""")
            }.apply {
                response.status() shouldBe HttpStatusCode.InternalServerError
                response.content shouldContain "Kunne ikke opprette oppgave for attestering"
            }
        }
    }

    @Test
    fun simulering() {
        withTestApplication({
            testSusebakover()
        }) {
            val objects = setup()

            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/blabla/simuler",
                listOf(Brukerrolle.Saksbehandler)
            ).apply {
                assertSoftly {
                    response.status() shouldBe HttpStatusCode.BadRequest
                    response.content shouldContain "ikke en gyldig UUID"
                }
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${UUID.randomUUID()}/simuler",
                listOf(Brukerrolle.Saksbehandler)
            ).apply {
                assertSoftly {
                    response.status() shouldBe HttpStatusCode.NotFound
                    response.content shouldContain "Kunne ikke finne behandling"
                }
            }

            services.søknadsbehandling.vilkårsvurder(
                VilkårsvurderRequest(
                    objects.søknadsbehandling.id,
                    saksbehandler,
                    Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt()
                )
            )
            services.søknadsbehandling.beregn(
                BeregnRequest(
                    behandlingId = objects.søknadsbehandling.id,
                    fradrag = emptyList(),
                    begrunnelse = null,
                )
            )

            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.søknadsbehandling.id}/simuler",
                listOf(Brukerrolle.Saksbehandler)
            ).apply {
                response.status() shouldBe HttpStatusCode.OK
            }
        }
    }

    @Nested
    inner class `Iverksetting av behandling` {
        private fun <R> withFerdigbehandletSakForBruker(
            test: TestApplicationEngine.(objects: Objects) -> R
        ) =
            withSetupForBruker(
                {
                    services.søknadsbehandling.vilkårsvurder(
                        VilkårsvurderRequest(
                            søknadsbehandling.id,
                            saksbehandler,
                            søknadsbehandling.behandlingsinformasjon.withAlleVilkårOppfylt()
                        )
                    )
                    services.søknadsbehandling.beregn(
                        BeregnRequest(
                            behandlingId = søknadsbehandling.id,
                            fradrag = emptyList(),
                            begrunnelse = null,
                        )
                    )
                    services.søknadsbehandling.simuler(
                        SimulerRequest(søknadsbehandling.id, saksbehandler)
                    )
                        .map {
                            services.søknadsbehandling.sendTilAttestering(
                                SendTilAttesteringRequest(
                                    søknadsbehandling.id,
                                    NavIdentBruker.Saksbehandler(navIdentSaksbehandler),
                                    "",
                                )
                            )
                        }
                }
            ) { test(it) }

        @Test
        fun `Forbidden når bruker ikke er attestant`() {
            withFerdigbehandletSakForBruker {
                defaultRequest(
                    HttpMethod.Patch,
                    "$sakPath/rubbish/behandlinger/${it.søknadsbehandling.id}/iverksett",
                    listOf(Brukerrolle.Saksbehandler),
                    navIdentSaksbehandler
                ).apply {
                    response.status() shouldBe HttpStatusCode.Forbidden
                }

                defaultRequest(
                    HttpMethod.Patch,
                    "$sakPath/${it.sak.id}/behandlinger/${UUID.randomUUID()}/iverksett",
                    listOf(Brukerrolle.Saksbehandler)
                ).apply {
                    response.status() shouldBe HttpStatusCode.Forbidden
                }
            }
        }

        @Test
        fun `BadRequest når behandlingId er ugyldig uuid eller NotFound når den ikke finnes`() {
            withFerdigbehandletSakForBruker {
                requestSomAttestant(
                    HttpMethod.Patch,
                    "$sakPath/rubbish/behandlinger/${UUID.randomUUID()}/iverksett",
                    navIdentSaksbehandler
                )
                    .apply {
                        response.status() shouldBe HttpStatusCode.NotFound
                    }

                requestSomAttestant(
                    HttpMethod.Patch,
                    "$sakPath/rubbish/behandlinger/rubbish/iverksett",
                    navIdentSaksbehandler
                )
                    .apply {
                        response.status() shouldBe HttpStatusCode.BadRequest
                    }
            }
        }

        @Test
        fun `NotFound når behandling ikke eksisterer`() {
            withFerdigbehandletSakForBruker {
                requestSomAttestant(
                    HttpMethod.Patch,
                    "$sakPath/${it.sak.id}/behandlinger/${UUID.randomUUID()}/iverksett",
                    navIdentSaksbehandler
                )
                    .apply {
                        response.status() shouldBe HttpStatusCode.NotFound
                    }
            }
        }

        @Test
        fun `Forbidden når den som behandlet saken prøver å attestere seg selv`() {
            withFerdigbehandletSakForBruker {
                handleRequest(
                    HttpMethod.Patch,
                    "$sakPath/${it.sak.id}/behandlinger/${it.søknadsbehandling.id}/iverksett",
                ) {
                    addHeader(
                        HttpHeaders.Authorization,
                        jwtStub.createJwtToken(
                            subject = "random",
                            roller = listOf(Brukerrolle.Attestant),
                            navIdent = navIdentSaksbehandler
                        ).asBearerToken()
                    )
                }.apply {
                    response.status() shouldBe HttpStatusCode.Forbidden
                }
            }
        }

        @Test
        fun `OK når bruker er attestant, og sak ble behandlet av en annen person`() {
            withFerdigbehandletSakForBruker {
                requestSomAttestant(
                    HttpMethod.Patch,
                    "$sakPath/${it.sak.id}/behandlinger/${it.søknadsbehandling.id}/iverksett",
                    navIdentAttestant
                )
                    .apply {
                        response.status() shouldBe HttpStatusCode.OK
                        deserialize<BehandlingJson>(response.content!!).let { behandlingJson ->
                            behandlingJson.attestering?.attestant shouldBe navIdentAttestant
                            behandlingJson.status shouldBe "IVERKSATT_INNVILGET"
                            behandlingJson.saksbehandler shouldBe navIdentSaksbehandler
                        }
                    }
            }
        }
    }

    @Nested
    inner class `Underkjenning av behandling` {
        private fun <R> withFerdigbehandletSakForBruker(
            test: TestApplicationEngine.(objects: Objects) -> R
        ) =
            withSetupForBruker(
                {
                    services.søknadsbehandling.vilkårsvurder(
                        VilkårsvurderRequest(
                            søknadsbehandling.id,
                            saksbehandler,
                            søknadsbehandling.behandlingsinformasjon.withAlleVilkårOppfylt()
                        )
                    )
                    services.søknadsbehandling.beregn(
                        BeregnRequest(
                            behandlingId = søknadsbehandling.id,
                            fradrag = emptyList(),
                            begrunnelse = null,
                        )
                    )
                    services.søknadsbehandling.simuler(
                        SimulerRequest(
                            søknadsbehandling.id,
                            saksbehandler
                        )
                    )
                        .map {
                            services.søknadsbehandling.sendTilAttestering(
                                SendTilAttesteringRequest(
                                    søknadsbehandling.id,
                                    NavIdentBruker.Saksbehandler(navIdentSaksbehandler),
                                    "",
                                )
                            )
                        }
                }
            ) { test(it) }

        @Test
        fun `Forbidden når bruker ikke er attestant`() {
            withFerdigbehandletSakForBruker { objects ->
                defaultRequest(
                    HttpMethod.Patch,
                    "$sakPath/rubbish/behandlinger/${objects.søknadsbehandling.id}/underkjenn",
                    listOf(Brukerrolle.Saksbehandler),
                    navIdentSaksbehandler
                ).apply {
                    response.status() shouldBe HttpStatusCode.Forbidden
                }

                defaultRequest(
                    HttpMethod.Patch,
                    "$sakPath/${objects.sak.id}/behandlinger/rubbish/underkjenn",
                    listOf(Brukerrolle.Saksbehandler)
                ).apply {
                    response.status() shouldBe HttpStatusCode.Forbidden
                }

                defaultRequest(
                    HttpMethod.Patch,
                    "$sakPath/${objects.sak.id}/behandlinger/${objects.søknadsbehandling.id}/underkjenn",
                    listOf(Brukerrolle.Saksbehandler)
                ).apply {
                    response.status() shouldBe HttpStatusCode.Forbidden
                }
            }
        }

        @Test
        fun `BadRequest når sakId eller behandlingId er ugyldig`() {
            withFerdigbehandletSakForBruker { objects ->
                requestSomAttestant(
                    HttpMethod.Patch,
                    "$sakPath/rubbish/behandlinger/${objects.søknadsbehandling.id}/underkjenn",
                    navIdentSaksbehandler
                ).apply {
                    response.status() shouldBe HttpStatusCode.BadRequest
                }

                requestSomAttestant(
                    HttpMethod.Patch,
                    "$sakPath/${objects.sak.id}/behandlinger/rubbish/underkjenn",
                    navIdentSaksbehandler
                ).apply {
                    response.status() shouldBe HttpStatusCode.BadRequest
                }
            }
        }

        @Test
        fun `NotFound når behandling ikke finnes`() {
            withFerdigbehandletSakForBruker { objects ->
                requestSomAttestant(
                    HttpMethod.Patch,
                    "$sakPath/${objects.sak.id}/behandlinger/${UUID.randomUUID()}/underkjenn",
                    navIdentSaksbehandler
                ) {
                    setBody("""{"kommentar":"b", "grunn": "BEREGNINGEN_ER_FEIL"}""")
                }.apply {
                    response.content shouldContain "Fant ikke behandling"
                    response.status() shouldBe HttpStatusCode.NotFound
                }
            }
        }

        @Test
        fun `BadRequest når kommentar ikke er oppgitt`() {
            withFerdigbehandletSakForBruker { objects ->
                requestSomAttestant(
                    HttpMethod.Patch,
                    "$sakPath/${objects.sak.id}/behandlinger/${objects.søknadsbehandling.id}/underkjenn",
                    navIdentSaksbehandler
                ) {
                    setBody(
                        """
                    {
                        "grunn":"BEREGNINGEN_ER_FEIL",
                        "kommentar":""
                    }
                        """.trimIndent()
                    )
                }.apply {
                    response.status() shouldBe HttpStatusCode.BadRequest
                    response.content shouldContain "Må angi en begrunnelse"
                }
            }
        }

        @Test
        fun `Forbidden når saksbehandler og attestant er samme person`() {
            withFerdigbehandletSakForBruker { objects ->
                handleRequest(
                    HttpMethod.Patch,
                    "$sakPath/${objects.sak.id}/behandlinger/${objects.søknadsbehandling.id}/underkjenn",
                ) {
                    addHeader(
                        HttpHeaders.Authorization,
                        jwtStub.createJwtToken(
                            subject = "S123456",
                            roller = listOf(Brukerrolle.Attestant),
                            navIdent = navIdentSaksbehandler
                        ).asBearerToken()
                    )
                    setBody(
                        """
                    {
                        "grunn": "BEREGNINGEN_ER_FEIL",
                        "kommentar": "Ser fel ut. Men denna borde bli forbidden eftersom attestant og saksbehandler er samme."
                    }
                        """.trimIndent()
                    )
                }.apply {
                    response.status() shouldBe HttpStatusCode.Forbidden
                }
            }
        }

        @Test
        fun `OK når alt er som det skal være`() {
            withFerdigbehandletSakForBruker { objects ->
                requestSomAttestant(
                    HttpMethod.Patch,
                    "$sakPath/${objects.sak.id}/behandlinger/${objects.søknadsbehandling.id}/underkjenn",
                    navIdentAttestant
                ) {
                    setBody("""{"kommentar":"kommentar", "grunn": "BEREGNINGEN_ER_FEIL" }""")
                }.apply {
                    response.status() shouldBe HttpStatusCode.OK
                    deserialize<BehandlingJson>(response.content!!).let {
                        it.status shouldBe "UNDERKJENT_INNVILGET"
                    }
                }
            }
        }

        @Test
        fun `Feiler dersom man ikke får sendt til utbetaling`() {
            withTestApplication({
                testSusebakover(
                    clients = testClients.copy(
                        utbetalingPublisher = object : UtbetalingPublisher {
                            override fun publish(
                                utbetaling: Utbetaling.SimulertUtbetaling
                            ): Either<UtbetalingPublisher.KunneIkkeSendeUtbetaling, Utbetalingsrequest> =
                                UtbetalingPublisher.KunneIkkeSendeUtbetaling(
                                    Utbetalingsrequest("")
                                ).left()
                        },
                    )
                )
            }) {
                val objects = setup()
                services.søknadsbehandling.vilkårsvurder(
                    VilkårsvurderRequest(
                        objects.søknadsbehandling.id,
                        saksbehandler,
                        Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt()
                    )
                )
                services.søknadsbehandling.beregn(
                    BeregnRequest(
                        behandlingId = objects.søknadsbehandling.id,
                        fradrag = emptyList(),
                        begrunnelse = null,
                    )
                )
                services.søknadsbehandling.simuler(
                    SimulerRequest(objects.søknadsbehandling.id, saksbehandler)
                ).fold(
                    { it },
                    {

                        services.søknadsbehandling.sendTilAttestering(
                            SendTilAttesteringRequest(
                                objects.søknadsbehandling.id,
                                saksbehandler,
                                ""
                            )
                        )
                    }
                )

                requestSomAttestant(
                    HttpMethod.Patch,
                    "$sakPath/${objects.sak.id}/behandlinger/${objects.søknadsbehandling.id}/iverksett"
                ).apply {
                    response.status() shouldBe HttpStatusCode.InternalServerError
                }
            }
        }
    }

    data class Objects(
        val sak: Sak,
        val søknad: Søknad,
        val søknadsbehandling: Søknadsbehandling.Vilkårsvurdert.Uavklart
    )

    private fun setup(): Objects {
        val søknadInnhold = SøknadInnholdTestdataBuilder.build()
        val fnr: Fnr = FnrGenerator.random()
        SakFactory(clock = fixedClock).nySak(fnr, søknadInnhold).also {
            repos.sak.opprettSak(it)
        }
        val sak: Sak = repos.sak.hentSak(fnr)!!
        val journalpostId = JournalpostId("12")
        val oppgaveId = OppgaveId("12")
        val søknadMedOppgave: Søknad.Journalført.MedOppgave = (sak.søknader[0] as Søknad.Ny)
            .journalfør(journalpostId).also { repos.søknad.oppdaterjournalpostId(it) }
            .medOppgave(oppgaveId).also { repos.søknad.oppdaterOppgaveId(it) }

        val søknadsbehandling = Søknadsbehandling.Vilkårsvurdert.Uavklart(
            id = UUID.randomUUID(),
            opprettet = sak.opprettet,
            sakId = sak.id,
            søknad = søknadMedOppgave,
            oppgaveId = OppgaveId("1234"),
            saksnummer = sak.saksnummer,
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
            fnr = sak.fnr,
            fritekstTilBrev = "",
            behandlingsperiode = behandlingsperiode,
        )
        repos.søknadsbehandling.lagre(
            søknadsbehandling
        )
        return Objects(
            repos.sak.hentSak(sak.id)!!,
            repos.søknad.hentSøknad(søknadMedOppgave.id)!!,
            søknadsbehandling
        )
    }

    val navIdentSaksbehandler = "random-saksbehandler-id"
    val navIdentAttestant = "random-attestant-id"

    fun <R> withSetupForBruker(
        s: Objects.() -> Unit,
        test: TestApplicationEngine.(objects: Objects) -> R
    ) =
        withTestApplication({
            testSusebakover(
                clients = testClients
            )
        }) {
            val objects = setup()
            s(objects)
            test(objects)
        }
}
