package no.nav.su.se.bakover.web.routes.behandling

import arrow.core.Either
import arrow.core.left
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.client.stubs.oppdrag.SimuleringStub
import no.nav.su.se.bakover.client.stubs.oppgave.OppgaveClientStub
import no.nav.su.se.bakover.common.Config
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.extractBehandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.Jwt
import no.nav.su.se.bakover.web.TestClientsBuilder.testClients
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.requestSomAttestant
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.Month
import java.util.UUID

internal class BehandlingRoutesKtTest {

    private val repo = DatabaseBuilder.build(EmbeddedDatabase.instance()).objectRepo

    @Test
    fun `henter en behandling`() {
        withTestApplication({
            testSusebakover()
        }) {
            val objects = setup()
            defaultRequest(HttpMethod.Get, "$sakPath/${objects.sak.id}/behandlinger/${objects.behandling.id}").apply {
                objectMapper.readValue<BehandlingJson>(response.content!!).let {
                    it.id shouldBe objects.behandling.id.toString()
                    it.behandlingsinformasjon shouldNotBe null
                    it.søknad.id shouldBe objects.søknad.id.toString()
                }
            }
        }
    }

    @Test
    fun `Opprette en oppgave til attestering er OK`() {
        withTestApplication({
            testSusebakover()
        }) {
            val objects = setup()
            objects.behandling.oppdaterBehandlingsinformasjon(extractBehandlingsinformasjon(objects.behandling).withAlleVilkårOppfylt())
            objects.behandling.opprettBeregning(1.januar(2020), 31.desember(2020))
            objects.behandling.simuler(SimuleringStub)
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.behandling.id}/tilAttestering"
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
                        override fun opprettOppgave(config: OppgaveConfig): Either<KunneIkkeOppretteOppgave, Long> {
                            return Either.left(KunneIkkeOppretteOppgave(500, "Kunne ikke opprette oppgave"))
                        }
                    }
                )
            )
        }) {
            val objects = setup()
            objects.behandling.oppdaterBehandlingsinformasjon(extractBehandlingsinformasjon(objects.behandling).withAlleVilkårOppfylt())
            objects.behandling.opprettBeregning(1.januar(2020), 31.desember(2020))
            objects.behandling.simuler(SimuleringStub)
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.behandling.id}/tilAttestering"
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

            objects.behandling.oppdaterBehandlingsinformasjon(extractBehandlingsinformasjon(objects.behandling).withAlleVilkårOppfylt())

            defaultRequest(HttpMethod.Post, "$sakPath/${objects.sak.id}/behandlinger/${objects.behandling.id}/beregn") {
                setBody(
                    """
                    {
                        "fom":"$fraOgMed",
                        "fraOgMed":"$fraOgMed",
                        "tom":"$tilOgMed",
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
    fun `beregn error handling`() {
        withTestApplication({
            testSusebakover()
        }) {
            val objects = setup()
            defaultRequest(HttpMethod.Post, "$sakPath/${objects.sak.id}/behandlinger/blabla/beregn") {}.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "ikke en gyldig UUID"
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${UUID.randomUUID()}/beregn"
            ).apply {
                response.status() shouldBe HttpStatusCode.NotFound
                response.content shouldContain "Fant ikke behandling med behandlingId"
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.behandling.id}/beregn"
            ).apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "Ugyldig body"
            }
            defaultRequest(HttpMethod.Post, "$sakPath/${objects.sak.id}/behandlinger/${objects.behandling.id}/beregn") {
                setBody(
                    """
                    {
                        "fom":"${LocalDate.of(2020, Month.JANUARY, 16)}",
                        "fraOgMed":"${LocalDate.of(2020, Month.JANUARY, 16)}",
                        "tom":"${LocalDate.of(2020, Month.DECEMBER, 31)}",
                        "tilOgMed":"${LocalDate.of(2020, Month.DECEMBER, 31)}",
                        "sats":"LAV",
                        "fradrag":[]
                    }
                    """.trimIndent()
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "Ugyldige input-parametere"
            }
        }
    }

    @Test
    fun simulering() {
        withTestApplication({
            testSusebakover()
        }) {
            val objects = setup()
            defaultRequest(HttpMethod.Post, "$sakPath/missing/behandlinger/${objects.behandling.id}/simuler") {}.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "ikke en gyldig UUID"
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${UUID.randomUUID()}/behandlinger/${objects.behandling.id}/simuler"
            ).apply {
                response.status() shouldBe HttpStatusCode.NotFound
                response.content shouldContain "Ugyldig kombinasjon av sak og behandling"
            }
            defaultRequest(HttpMethod.Post, "$sakPath/${objects.sak.id}/behandlinger/blabla/simuler") {}.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "ikke en gyldig UUID"
            }
            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${UUID.randomUUID()}/simuler"
            ).apply {
                response.status() shouldBe HttpStatusCode.NotFound
                response.content shouldContain "Fant ikke behandling med behandlingId"
            }

            objects.behandling.oppdaterBehandlingsinformasjon(extractBehandlingsinformasjon(objects.behandling).withAlleVilkårOppfylt())
            objects.behandling.opprettBeregning(1.januar(2020), 31.desember(2020))

            defaultRequest(
                HttpMethod.Post,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.behandling.id}/simuler"
            ) {}.apply {
                response.status() shouldBe HttpStatusCode.OK
                response.content shouldContain "utbetaling"
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

            defaultRequest(HttpMethod.Post, "$sakPath/${objects.sak.id}/behandlinger/${objects.behandling.id}/beregn") {
                setBody(
                    """
                    {
                        "fom":"${1.januar(2020)}",
                        "fraOgMed":"${1.januar(2020)}",
                        "tom":"${31.desember(2020)}",
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

    @Test
    fun `iverksetter behandling`() {
        withTestApplication({
            testSusebakover()
        }) {
            val objects = setup()
            objects.behandling.oppdaterBehandlingsinformasjon(extractBehandlingsinformasjon(objects.behandling).withAlleVilkårOppfylt())
            objects.behandling.opprettBeregning(1.januar(2020), 31.desember(2020))
            objects.behandling.simuler(SimuleringStub)
            objects.behandling.sendTilAttestering(AktørId("aktørId"), OppgaveClientStub, Saksbehandler("randomoid"))

            defaultRequest(
                HttpMethod.Patch,
                "$sakPath/rubbish/behandlinger/${objects.behandling.id}/iverksett"
            ).apply {
                response.status() shouldBe HttpStatusCode.Forbidden
            }

            requestSomAttestant(HttpMethod.Patch, "$sakPath/rubbish/behandlinger/${UUID.randomUUID()}/iverksett")
                .apply {
                    response.status() shouldBe HttpStatusCode.BadRequest
                }

            defaultRequest(
                HttpMethod.Patch,
                "$sakPath/${objects.sak.id}/behandlinger/${UUID.randomUUID()}/iverksett"
            ).apply {
                response.status() shouldBe HttpStatusCode.Forbidden
            }

            requestSomAttestant(HttpMethod.Patch, "$sakPath/${objects.sak.id}/behandlinger/rubbish/iverksett")
                .apply {
                    response.status() shouldBe HttpStatusCode.BadRequest
                }

            requestSomAttestant(
                HttpMethod.Patch,
                "$sakPath/${objects.sak.id}/behandlinger/${UUID.randomUUID()}/iverksett"
            )
                .apply {
                    response.status() shouldBe HttpStatusCode.NotFound
                }

            handleRequest(
                HttpMethod.Patch,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.behandling.id}/iverksett"
            ) {
                addHeader(
                    HttpHeaders.Authorization,
                    Jwt.create(
                        subject = "random",
                        groups = listOf(Config.azureRequiredGroup, Config.azureGroupAttestant)
                    )
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.Forbidden
            }

            requestSomAttestant(
                HttpMethod.Patch,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.behandling.id}/iverksett"
            )
                .apply {
                    response.status() shouldBe HttpStatusCode.OK
                    deserialize<BehandlingJson>(response.content!!).let {
                        it.attestant shouldBe "enSaksbehandleroid"
                        it.status shouldBe "IVERKSATT_INNVILGET"
                        it.saksbehandler shouldBe "randomoid"
                    }
                }
        }
    }

    @Test
    fun `underkjenn`() {
        withTestApplication({
            testSusebakover()
        }) {
            val objects = setup()
            objects.behandling.oppdaterBehandlingsinformasjon(extractBehandlingsinformasjon(objects.behandling).withAlleVilkårOppfylt())
            objects.behandling.opprettBeregning(1.januar(2020), 31.desember(2020))
            objects.behandling.simuler(SimuleringStub)
            objects.behandling.sendTilAttestering(AktørId("aktørId"), OppgaveClientStub, Saksbehandler("S123456oid"))

            defaultRequest(
                HttpMethod.Patch,
                "$sakPath/rubbish/behandlinger/${objects.behandling.id}/underkjenn"
            ).apply {
                response.status() shouldBe HttpStatusCode.Forbidden
            }

            requestSomAttestant(
                HttpMethod.Patch,
                "$sakPath/rubbish/behandlinger/${objects.behandling.id}/underkjenn"
            ).apply {
                response.status() shouldBe HttpStatusCode.BadRequest
            }

            defaultRequest(
                HttpMethod.Patch,
                "$sakPath/${objects.sak.id}/behandlinger/rubbish/underkjenn",
            ).apply {
                response.status() shouldBe HttpStatusCode.Forbidden
            }

            requestSomAttestant(
                HttpMethod.Patch,
                "$sakPath/${objects.sak.id}/behandlinger/rubbish/underkjenn"
            ).apply {
                response.status() shouldBe HttpStatusCode.BadRequest
            }

            requestSomAttestant(
                HttpMethod.Patch,
                "$sakPath/${objects.sak.id}/behandlinger/${UUID.randomUUID()}/underkjenn"
            ).apply {
                response.status() shouldBe HttpStatusCode.NotFound
            }

            defaultRequest(
                HttpMethod.Patch,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.behandling.id}/underkjenn",
            ).apply {
                response.status() shouldBe HttpStatusCode.Forbidden
            }

            requestSomAttestant(
                HttpMethod.Patch,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.behandling.id}/underkjenn"
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
                response.content shouldContain "Må anngi en begrunnelse"
            }

            handleRequest(
                HttpMethod.Patch,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.behandling.id}/underkjenn"
            ) {
                addHeader(
                    HttpHeaders.Authorization,
                    Jwt.create(
                        subject = "S123456",
                        groups = listOf(Config.azureRequiredGroup, Config.azureGroupAttestant)
                    )
                )
                setBody(
                    """
                    {
                        "begrunnelse":"Ser fel ut. Men denna borde bli forbidden eftersom attestant og saksbehandler er samme."
                    }
                    """.trimIndent()
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.Forbidden
            }

            requestSomAttestant(
                HttpMethod.Patch,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.behandling.id}/underkjenn"
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

        withTestApplication({
            testSusebakover(
                testClients.copy(
                    utbetalingPublisher = object : UtbetalingPublisher {
                        override fun publish(
                            nyUtbetaling: NyUtbetaling
                        ): Either<UtbetalingPublisher.KunneIkkeSendeUtbetaling, Oppdragsmelding> =
                            UtbetalingPublisher.KunneIkkeSendeUtbetaling("").left()
                    }
                )
            )
        }) {
            val objects = setup()
            objects.behandling.oppdaterBehandlingsinformasjon(extractBehandlingsinformasjon(objects.behandling).withAlleVilkårOppfylt())
            objects.behandling.opprettBeregning(1.januar(2020), 31.desember(2020))
            objects.behandling.simuler(SimuleringStub)
            objects.behandling.sendTilAttestering(AktørId("aktørId"), OppgaveClientStub, Saksbehandler("S123456"))

            requestSomAttestant(
                HttpMethod.Patch,
                "$sakPath/${objects.sak.id}/behandlinger/${objects.behandling.id}/iverksett"
            ).apply {
                response.status() shouldBe HttpStatusCode.InternalServerError
            }
        }
    }

    data class Objects(
        val sak: Sak,
        val søknad: Søknad,
        val behandling: Behandling
    )

    private fun setup(): Objects {
        val sak = repo.opprettSak(FnrGenerator.random())
        val søknad = sak.nySøknad(SøknadInnholdTestdataBuilder.build())
        val behandling = sak.opprettSøknadsbehandling(søknad.id)
        return Objects(sak, søknad, behandling)
    }
}
