package no.nav.su.se.bakover.web.routes.behandling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.collections.shouldHaveSize
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
import no.nav.su.se.bakover.client.person.MicrosoftGraphResponse
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Brukerrolle
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
import no.nav.su.se.bakover.domain.beregning.InntektDelerAvPeriode
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.UtenlandskInntekt
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeFerdigstilleOppgave
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.service.ServiceBuilder
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.Jwt
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.TestClientsBuilder.testClients
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.requestSomAttestant
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.Month
import java.util.UUID

internal class BehandlingRoutesKtTest {

    private val saksbehandler = NavIdentBruker.Saksbehandler("AB12345")

    private val repos = DatabaseBuilder.build(EmbeddedDatabase.instance(), BehandlingFactory(mock()))
    private val services = ServiceBuilder(
        databaseRepos = repos,
        clients = TestClientsBuilder.build(),
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
                extractBehandlingsinformasjon(objects.behandling).withAlleVilkårOppfylt()
            )
            services.behandling.opprettBeregning(
                objects.nySøknadsbehandling.id,
                1.januar(2020),
                31.desember(2020),
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

                        override fun ferdigstillAttesteringsoppgave(aktørId: AktørId): Either<KunneIkkeFerdigstilleOppgave, Unit> {
                            return Unit.right()
                        }

                        override fun lukkOppgave(oppgaveId: OppgaveId): Either<KunneIkkeFerdigstilleOppgave, Unit> {
                            return Unit.right()
                        }
                    }
                )
            )
        }) {
            val objects = setup()
            services.behandling.oppdaterBehandlingsinformasjon(
                objects.nySøknadsbehandling.id,
                extractBehandlingsinformasjon(objects.behandling).withAlleVilkårOppfylt()
            )
            services.behandling.opprettBeregning(
                objects.nySøknadsbehandling.id,
                1.januar(2020),
                31.desember(2020),
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
    fun `opprette beregning for behandling`() {
        withTestApplication({
            testSusebakover()
        }) {
            val objects = setup()
            val fraOgMed = LocalDate.of(2020, Month.JANUARY, 1)
            val tilOgMed = LocalDate.of(2020, Month.DECEMBER, 31)
            val sats = Sats.HØY

            services.behandling.oppdaterBehandlingsinformasjon(
                objects.nySøknadsbehandling.id,
                extractBehandlingsinformasjon(objects.behandling).withAlleVilkårOppfylt()
            )

            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.nySøknadsbehandling.id}/beregn",
                listOf(Brukerrolle.Saksbehandler)
            ) {
                setBody(
                    """
                    {
                        "fraOgMed":"$fraOgMed",
                        "tilOgMed":"$tilOgMed",
                        "sats":"${sats.name}",
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
    fun `Fradrag med utenlandskInntekt og inntektDelerAvPeriode oppretter beregning`() {
        withTestApplication({
            testSusebakover()
        }) {
            val objects = setup()
            val fraOgMed = LocalDate.of(2020, Month.JANUARY, 1)
            val tilOgMed = LocalDate.of(2020, Month.DECEMBER, 31)
            val sats = Sats.HØY

            services.behandling.oppdaterBehandlingsinformasjon(
                objects.nySøknadsbehandling.id,
                extractBehandlingsinformasjon(objects.behandling).withAlleVilkårOppfylt()
            )

            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.nySøknadsbehandling.id}/beregn",
                listOf(Brukerrolle.Saksbehandler)
            ) {
                setBody(
                    """
                    {
                       "fraOgMed":"$fraOgMed",
                       "tilOgMed":"$tilOgMed",
                       "sats":"${sats.name}",
                       "fradrag":[{
                             "type":"Arbeidsinntekt",
                             "beløp":200,
                             "utenlandskInntekt":{
                                "beløpIUtenlandskValuta":200,
                                "valuta":"euro",
                                "kurs":0.5
                             },
                             "inntektDelerAvPeriode":{
                                "fraOgMed":"$fraOgMed",
                                "tilOgMed":"$tilOgMed"
                             }
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
                behandlingJson.beregning.fradrag shouldHaveSize 1
                behandlingJson.beregning.fradrag.all {
                    it.utenlandskInntekt == UtenlandskInntekt(
                        beløpIUtenlandskValuta = 200,
                        valuta = "euro",
                        kurs = 0.5
                    ) && it.inntektDelerAvPeriode == InntektDelerAvPeriode(fraOgMed = fraOgMed, tilOgMed = tilOgMed)
                }
            }
        }
    }

    @Test
    fun `Fradrag med utenlandskInntekt og inntektDelerAvPeriode er null oppretter beregning`() {
        withTestApplication({
            testSusebakover()
        }) {
            val objects = setup()
            val fraOgMed = LocalDate.of(2020, Month.JANUARY, 1)
            val tilOgMed = LocalDate.of(2020, Month.DECEMBER, 31)
            val sats = Sats.HØY

            services.behandling.oppdaterBehandlingsinformasjon(
                objects.nySøknadsbehandling.id,
                extractBehandlingsinformasjon(objects.behandling).withAlleVilkårOppfylt()
            )

            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.nySøknadsbehandling.id}/beregn",
                listOf(Brukerrolle.Saksbehandler)
            ) {
                setBody(
                    """
                    {
                        "fraOgMed":"$fraOgMed",
                        "tilOgMed":"$tilOgMed",
                        "sats":"${sats.name}",
                        "fradrag": [
                                {
                                "type": "Arbeidsinntekt",
                                "beløp": 200,
                                "utenlandskInntekt": null,
                                "inntektDelerAvPeriode": null
                            }
                        ]
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
                behandlingJson.beregning.fradrag shouldHaveSize 1
                behandlingJson.beregning.fradrag.all {
                    it.utenlandskInntekt == null && it.inntektDelerAvPeriode == null
                }
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
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "ikke en gyldig UUID"
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${UUID.randomUUID()}/beregn",
                listOf(Brukerrolle.Saksbehandler)
            ).apply {
                response.status() shouldBe HttpStatusCode.NotFound
                response.content shouldContain "Fant ikke behandling med behandlingId"
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.nySøknadsbehandling.id}/beregn",
                listOf(Brukerrolle.Saksbehandler)
            ).apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "Ugyldig body"
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.nySøknadsbehandling.id}/beregn",
                listOf(Brukerrolle.Saksbehandler)
            ) {
                setBody(
                    """
                    {
                        "fraOgMed":"${LocalDate.of(2020, Month.JANUARY, 16)}",
                        "tilOgMed":"${LocalDate.of(2020, Month.DECEMBER, 31)}",
                        "sats":"ORDINÆR",
                        "fradrag":[]
                    }
                    """.trimIndent()
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "Ugyldige input-parametere"
            }
            val fraOgMed = LocalDate.of(2020, Month.JANUARY, 1)
            val tilOgMed = LocalDate.of(2020, Month.DECEMBER, 31)
            val sats = Sats.HØY

            objects.behandling.oppdaterBehandlingsinformasjon(extractBehandlingsinformasjon(objects.behandling).withAlleVilkårOppfylt())

            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.nySøknadsbehandling.id}/beregn",
                listOf(Brukerrolle.Saksbehandler)
            ) {
                setBody(
                    """{
                           "fraOgMed":"$fraOgMed",
                           "tilOgMed":"$tilOgMed",
                           "sats":"${sats.name}",
                           "fradrag":[
                            {
                                 "type":"Arbeidsinntekt",
                                 "beløp":200,
                                 "utenlandskInntekt":{
                                    "beløpIUtenlandskValuta":-200,
                                    "valuta":"euro",
                                    "kurs":0.5
                                 },
                                 "inntektDelerAvPeriode":null
                              }
                            ]
                        }
                    """.trimIndent()
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
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
                "$sakPath/missing/behandlinger/${objects.nySøknadsbehandling.id}/simuler",
                listOf(Brukerrolle.Saksbehandler)
            ) {}.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "ikke en gyldig UUID"
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${UUID.randomUUID()}/behandlinger/${objects.nySøknadsbehandling.id}/simuler",
                listOf(Brukerrolle.Saksbehandler)
            ).apply {
                response.status() shouldBe HttpStatusCode.NotFound
                response.content shouldContain "Ugyldig kombinasjon av sak og behandling"
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/blabla/simuler",
                listOf(Brukerrolle.Saksbehandler)
            ) {}.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "ikke en gyldig UUID"
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${UUID.randomUUID()}/simuler",
                listOf(Brukerrolle.Saksbehandler)
            ).apply {
                response.status() shouldBe HttpStatusCode.NotFound
                response.content shouldContain "Fant ikke behandling med behandlingId"
            }

            services.behandling.oppdaterBehandlingsinformasjon(
                objects.nySøknadsbehandling.id,
                extractBehandlingsinformasjon(objects.behandling).withAlleVilkårOppfylt()
            )
            services.behandling.opprettBeregning(
                objects.nySøknadsbehandling.id,
                1.januar(2020),
                31.desember(2020),
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
                    """
                    {
                        "fraOgMed":"${1.januar(2020)}",
                        "tilOgMed":"${31.desember(2020)}",
                        "sats":"${Sats.HØY}",
                        "fradrag":[]
                    }
                    """.trimIndent()
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "Illegal operation"
                response.content shouldContain "opprettBeregning"
                response.content shouldContain "state: OPPRETTET"
            }
        }
    }

    @Nested
    inner class `Iverksetting av behandling` {
        fun <R> withFerdigbehandletSakForBruker(
            brukersNavIdent: String,
            test: TestApplicationEngine.(objects: Objects) -> R
        ) =
            withSetupForBruker(
                brukersNavIdent,
                {
                    services.behandling.oppdaterBehandlingsinformasjon(
                        nySøknadsbehandling.id,
                        extractBehandlingsinformasjon(behandling).withAlleVilkårOppfylt()
                    )
                    services.behandling.opprettBeregning(
                        nySøknadsbehandling.id,
                        1.januar(2020),
                        31.desember(2020),
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
                        Jwt.create(
                            subject = "random",
                            roller = listOf(Brukerrolle.Attestant)
                        )
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
                            behandlingJson.attestant shouldBe navIdentAttestant
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
                        extractBehandlingsinformasjon(behandling).withAlleVilkårOppfylt()
                    )
                    services.behandling.opprettBeregning(
                        nySøknadsbehandling.id,
                        1.januar(2020),
                        31.desember(2020),
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
                ).apply {
                    response.status() shouldBe HttpStatusCode.NotFound
                }
            }
        }

        @Test
        fun `BadRequest når begrunnelse ikke er oppgitt`() {
            withFerdigbehandletSakForBruker(navIdentSaksbehandler) { objects ->
                requestSomAttestant(
                    HttpMethod.Patch,
                    "$sakPath/${objects.sak.id}/behandlinger/${objects.nySøknadsbehandling.id}/underkjenn"
                ) {
                    setBody(
                        """
                    {
                        "begrunnelse":""
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
                        Jwt.create(
                            subject = "S123456",
                            roller = listOf(Brukerrolle.Attestant)
                        )
                    )
                    setBody(
                        """
                    {
                        "begrunnelse": "Ser fel ut. Men denna borde bli forbidden eftersom attestant og saksbehandler er samme."
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
                    setBody(
                        """
                    {
                        "begrunnelse":"begrunnelse"
                    }
                        """.trimIndent()
                    )
                }.apply {
                    response.status() shouldBe HttpStatusCode.OK
                    deserialize<BehandlingJson>(response.content!!).let {
                        println(it.hendelser)
                        it.status shouldBe "SIMULERT"
                        it.hendelser?.last()?.melding shouldBe "begrunnelse"
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
                                utbetaling: Utbetaling
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
                    extractBehandlingsinformasjon(objects.behandling).withAlleVilkårOppfylt()
                )
                services.behandling.opprettBeregning(
                    objects.nySøknadsbehandling.id,
                    1.januar(2020),
                    31.desember(2020),
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
        val sak: Sak = SakFactory().nySak(FnrGenerator.random(), søknadInnhold).also {
            repos.sak.opprettSak(it)
        }.toSak()
        val søknad: Søknad = Søknad(sakId = sak.id, søknadInnhold = søknadInnhold).also {
            repos.søknad.opprettSøknad(it)
        }
        val nySøknadsbehandling = NySøknadsbehandling(
            sakId = sak.id,
            søknadId = søknad.id,
            oppgaveId = OppgaveId("1234")
        )
        repos.behandling.opprettSøknadsbehandling(
            nySøknadsbehandling
        )
        return Objects(sak, søknad, nySøknadsbehandling, repos.behandling.hentBehandling(nySøknadsbehandling.id)!!)
    }

    val navIdentSaksbehandler = "random-saksbehandler-id"
    val navIdentAttestant = "random-attestant-id"

    fun graphApiClientForNavIdent(navIdent: String) =
        object : MicrosoftGraphApiOppslag {
            override fun hentBrukerinformasjon(userToken: String): Either<String, MicrosoftGraphResponse> =
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
