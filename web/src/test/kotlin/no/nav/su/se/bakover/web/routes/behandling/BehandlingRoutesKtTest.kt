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
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslagFeil
import no.nav.su.se.bakover.client.person.MicrosoftGraphResponse
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
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
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import no.nav.su.se.bakover.domain.behandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.extractBehandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.service.ServiceBuilder
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.TestClientsBuilder.testClients
import no.nav.su.se.bakover.web.applicationConfig
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.jwtStub
import no.nav.su.se.bakover.web.requestSomAttestant
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.stubs.asBearerToken
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

internal class BehandlingRoutesKtTest {

    private val saksbehandler = NavIdentBruker.Saksbehandler("AB12345")

    private val repos = DatabaseBuilder.build(EmbeddedDatabase.instance(), BehandlingFactory(mock()))
    private val services = ServiceBuilder(
        databaseRepos = repos,
        clients = TestClientsBuilder.build(applicationConfig),
        behandlingMetrics = mock(),
        søknadMetrics = mock()
    ).build()

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
                    "$sakPath/${objects.sak.id}/behandlinger/${objects.nySøknadsbehandling.id}",
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
                    "$sakPath/${objects.sak.id}/behandlinger/${objects.nySøknadsbehandling.id}",
                    listOf(Brukerrolle.Saksbehandler)
                ).apply {
                    objectMapper.readValue<BehandlingJson>(response.content!!).let {
                        it.id shouldBe objects.nySøknadsbehandling.id.toString()
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
                    "$sakPath/${objects.sak.id}/behandlinger/${objects.nySøknadsbehandling.id}",
                    listOf(Brukerrolle.Attestant)
                ).apply {
                    objectMapper.readValue<BehandlingJson>(response.content!!).let {
                        it.id shouldBe objects.nySøknadsbehandling.id.toString()
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
            services.behandling.oppdaterBehandlingsinformasjon(
                objects.nySøknadsbehandling.id,
                saksbehandler,
                extractBehandlingsinformasjon(objects.behandling).withAlleVilkårOppfylt()
            )
            services.behandling.opprettBeregning(
                objects.nySøknadsbehandling.id,
                saksbehandler,
                Periode.create(1.januar(2021), 31.desember(2021)),
                emptyList()
            )
            services.behandling.simuler(objects.nySøknadsbehandling.id, saksbehandler)
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.nySøknadsbehandling.id}/tilAttestering",
                listOf(Brukerrolle.Saksbehandler)
            ) {
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
            services.behandling.oppdaterBehandlingsinformasjon(
                objects.nySøknadsbehandling.id,
                saksbehandler,
                extractBehandlingsinformasjon(objects.behandling).withAlleVilkårOppfylt()
            )
            services.behandling.opprettBeregning(
                objects.nySøknadsbehandling.id,
                saksbehandler,
                Periode.create(1.januar(2021), 31.desember(2021)),
                emptyList()
            )
            services.behandling.simuler(objects.nySøknadsbehandling.id, saksbehandler)
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.nySøknadsbehandling.id}/tilAttestering",
                listOf(Brukerrolle.Saksbehandler)
            ) {
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
            ) {}.apply {
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

            services.behandling.oppdaterBehandlingsinformasjon(
                objects.nySøknadsbehandling.id,
                saksbehandler,
                extractBehandlingsinformasjon(objects.behandling).withAlleVilkårOppfylt()
            )
            services.behandling.opprettBeregning(
                objects.nySøknadsbehandling.id,
                saksbehandler,
                Periode.create(1.januar(2021), 31.desember(2021)),
                emptyList()
            )

            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.nySøknadsbehandling.id}/simuler",
                listOf(Brukerrolle.Saksbehandler)
            ) {}.apply {
                response.status() shouldBe HttpStatusCode.OK
            }
        }
    }

    @Nested
    inner class `Iverksetting av behandling` {
        private fun <R> withFerdigbehandletSakForBruker(
            brukersNavIdent: String,
            test: TestApplicationEngine.(objects: Objects) -> R
        ) =
            withSetupForBruker(
                brukersNavIdent,
                {
                    services.behandling.oppdaterBehandlingsinformasjon(
                        nySøknadsbehandling.id,
                        saksbehandler,
                        extractBehandlingsinformasjon(behandling).withAlleVilkårOppfylt()
                    )
                    services.behandling.opprettBeregning(
                        nySøknadsbehandling.id,
                        saksbehandler,
                        Periode.create(1.januar(2021), 31.desember(2021)),
                        emptyList()
                    )
                    services.behandling.simuler(nySøknadsbehandling.id, saksbehandler)
                        .map {
                            services.behandling.sendTilAttestering(
                                nySøknadsbehandling.id,
                                NavIdentBruker.Saksbehandler(navIdentSaksbehandler)
                            )
                        }
                }
            ) { test(it) }

        @Test
        fun `Forbidden når bruker ikke er attestant`() {
            withFerdigbehandletSakForBruker(navIdentSaksbehandler) {
                defaultRequest(
                    HttpMethod.Patch,
                    "$sakPath/rubbish/behandlinger/${it.nySøknadsbehandling.id}/iverksett",
                    listOf(Brukerrolle.Saksbehandler)
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
            withFerdigbehandletSakForBruker(navIdentSaksbehandler) {
                requestSomAttestant(HttpMethod.Patch, "$sakPath/rubbish/behandlinger/${UUID.randomUUID()}/iverksett")
                    .apply {
                        response.status() shouldBe HttpStatusCode.NotFound
                    }

                requestSomAttestant(HttpMethod.Patch, "$sakPath/rubbish/behandlinger/rubbish/iverksett")
                    .apply {
                        response.status() shouldBe HttpStatusCode.BadRequest
                    }
            }
        }

        @Test
        fun `NotFound når behandling ikke eksisterer`() {
            withFerdigbehandletSakForBruker(navIdentSaksbehandler) {
                requestSomAttestant(
                    HttpMethod.Patch,
                    "$sakPath/${it.sak.id}/behandlinger/${UUID.randomUUID()}/iverksett"
                )
                    .apply {
                        response.status() shouldBe HttpStatusCode.NotFound
                    }
            }
        }

        @Test
        fun `Forbidden når den som behandlet saken prøver å attestere seg selv`() {
            withFerdigbehandletSakForBruker(navIdentSaksbehandler) {
                handleRequest(
                    HttpMethod.Patch,
                    "$sakPath/${it.sak.id}/behandlinger/${it.nySøknadsbehandling.id}/iverksett"
                ) {
                    addHeader(
                        HttpHeaders.Authorization,
                        jwtStub.createJwtToken(
                            subject = "random",
                            roller = listOf(Brukerrolle.Attestant)
                        ).asBearerToken()
                    )
                }.apply {
                    response.status() shouldBe HttpStatusCode.Forbidden
                }
            }
        }

        @Test
        fun `OK når bruker er attestant, og sak ble behandlet av en annen person`() {
            withFerdigbehandletSakForBruker(navIdentAttestant) {
                requestSomAttestant(
                    HttpMethod.Patch,
                    "$sakPath/${it.sak.id}/behandlinger/${it.nySøknadsbehandling.id}/iverksett"
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
            brukersNavIdent: String,
            test: TestApplicationEngine.(objects: Objects) -> R
        ) =
            withSetupForBruker(
                brukersNavIdent,
                {
                    services.behandling.oppdaterBehandlingsinformasjon(
                        nySøknadsbehandling.id,
                        saksbehandler,
                        extractBehandlingsinformasjon(behandling).withAlleVilkårOppfylt()
                    )
                    services.behandling.opprettBeregning(
                        nySøknadsbehandling.id,
                        saksbehandler,
                        Periode.create(1.januar(2021), 31.desember(2021)),
                        emptyList()
                    )
                    services.behandling.simuler(nySøknadsbehandling.id, saksbehandler)
                        .map {
                            services.behandling.sendTilAttestering(
                                nySøknadsbehandling.id,
                                NavIdentBruker.Saksbehandler(navIdentSaksbehandler)
                            )
                        }
                }
            ) { test(it) }

        @Test
        fun `Forbidden når bruker ikke er attestant`() {
            withFerdigbehandletSakForBruker(navIdentSaksbehandler) { objects ->
                defaultRequest(
                    HttpMethod.Patch,
                    "$sakPath/rubbish/behandlinger/${objects.nySøknadsbehandling.id}/underkjenn",
                    listOf(Brukerrolle.Saksbehandler)
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
                    "$sakPath/${objects.sak.id}/behandlinger/${objects.nySøknadsbehandling.id}/underkjenn",
                    listOf(Brukerrolle.Saksbehandler)
                ).apply {
                    response.status() shouldBe HttpStatusCode.Forbidden
                }
            }
        }

        @Test
        fun `BadRequest når sakId eller behandlingId er ugyldig`() {
            withFerdigbehandletSakForBruker(navIdentSaksbehandler) { objects ->
                requestSomAttestant(
                    HttpMethod.Patch,
                    "$sakPath/rubbish/behandlinger/${objects.nySøknadsbehandling.id}/underkjenn"
                ).apply {
                    response.status() shouldBe HttpStatusCode.BadRequest
                }

                requestSomAttestant(
                    HttpMethod.Patch,
                    "$sakPath/${objects.sak.id}/behandlinger/rubbish/underkjenn"
                ).apply {
                    response.status() shouldBe HttpStatusCode.BadRequest
                }
            }
        }

        @Test
        fun `NotFound når behandling ikke finnes`() {
            withFerdigbehandletSakForBruker(navIdentSaksbehandler) { objects ->
                requestSomAttestant(
                    HttpMethod.Patch,
                    "$sakPath/${objects.sak.id}/behandlinger/${UUID.randomUUID()}/underkjenn"
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
            withFerdigbehandletSakForBruker(navIdentSaksbehandler) { objects ->
                requestSomAttestant(
                    HttpMethod.Patch,
                    "$sakPath/${objects.sak.id}/behandlinger/${objects.nySøknadsbehandling.id}/underkjenn"
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
            withFerdigbehandletSakForBruker(navIdentSaksbehandler) { objects ->
                handleRequest(
                    HttpMethod.Patch,
                    "$sakPath/${objects.sak.id}/behandlinger/${objects.nySøknadsbehandling.id}/underkjenn"
                ) {
                    addHeader(
                        HttpHeaders.Authorization,
                        jwtStub.createJwtToken(
                            subject = "S123456",
                            roller = listOf(Brukerrolle.Attestant)
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
            withFerdigbehandletSakForBruker(navIdentAttestant) { objects ->
                requestSomAttestant(
                    HttpMethod.Patch,
                    "$sakPath/${objects.sak.id}/behandlinger/${objects.nySøknadsbehandling.id}/underkjenn"
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
                    testClients.copy(
                        utbetalingPublisher = object : UtbetalingPublisher {
                            override fun publish(
                                utbetaling: Utbetaling.SimulertUtbetaling
                            ): Either<UtbetalingPublisher.KunneIkkeSendeUtbetaling, Utbetalingsrequest> =
                                UtbetalingPublisher.KunneIkkeSendeUtbetaling(
                                    Utbetalingsrequest("")
                                ).left()
                        },
                        microsoftGraphApiClient = graphApiClientForNavIdent(navIdentAttestant)
                    )
                )
            }) {
                val objects = setup()
                services.behandling.oppdaterBehandlingsinformasjon(
                    objects.nySøknadsbehandling.id,
                    saksbehandler,
                    extractBehandlingsinformasjon(objects.behandling).withAlleVilkårOppfylt()
                )
                services.behandling.opprettBeregning(
                    objects.nySøknadsbehandling.id,
                    saksbehandler,
                    Periode.create(1.januar(2021), 31.desember(2021)),
                    emptyList()
                )
                services.behandling.simuler(objects.nySøknadsbehandling.id, saksbehandler).fold(
                    { it },
                    {

                        services.behandling.sendTilAttestering(
                            objects.nySøknadsbehandling.id,
                            saksbehandler
                        )
                    }
                )

                requestSomAttestant(
                    HttpMethod.Patch,
                    "$sakPath/${objects.sak.id}/behandlinger/${objects.nySøknadsbehandling.id}/iverksett"
                ).apply {
                    response.status() shouldBe HttpStatusCode.InternalServerError
                }
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
        SakFactory().nySak(fnr, søknadInnhold).also {
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

    val navIdentSaksbehandler = "random-saksbehandler-id"
    val navIdentAttestant = "random-attestant-id"

    fun graphApiClientForNavIdent(navIdent: String) =
        object : MicrosoftGraphApiOppslag {
            override fun hentBrukerinformasjon(userToken: String): Either<MicrosoftGraphApiOppslagFeil, MicrosoftGraphResponse> =
                Either.right(
                    MicrosoftGraphResponse(
                        onPremisesSamAccountName = navIdent,
                        displayName = "displayName",
                        givenName = "givenName",
                        mail = "mail",
                        officeLocation = "officeLocation",
                        surname = "surname",
                        userPrincipalName = "userPrincipalName",
                        id = "id",
                        jobTitle = "jobTitle",
                    )
                )

            override fun hentBrukerinformasjonForNavIdent(navIdent: NavIdentBruker): Either<MicrosoftGraphApiOppslagFeil, MicrosoftGraphResponse> =
                Either.right(
                    MicrosoftGraphResponse(
                        onPremisesSamAccountName = navIdent.toString(),
                        displayName = "displayName",
                        givenName = "givenName",
                        mail = "mail",
                        officeLocation = "officeLocation",
                        surname = "surname",
                        userPrincipalName = "userPrincipalName",
                        id = "id",
                        jobTitle = "jobTitle",
                    )
                )
        }

    fun <R> withSetupForBruker(
        brukersNavIdent: String,
        s: Objects.() -> Unit,
        test: TestApplicationEngine.(objects: Objects) -> R
    ) =
        withTestApplication({
            testSusebakover(
                clients = testClients.copy(
                    microsoftGraphApiClient = graphApiClientForNavIdent(brukersNavIdent)
                )
            )
        }) {
            val objects = setup()
            s(objects)
            test(objects)
        }
}
