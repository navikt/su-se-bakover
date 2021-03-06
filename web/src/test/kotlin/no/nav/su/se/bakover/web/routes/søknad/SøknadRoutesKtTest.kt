package no.nav.su.se.bakover.web.routes.søknad

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders.ContentType
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.Journalpost
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder.build
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.PersonOppslag
import no.nav.su.se.bakover.domain.søknad.LukkSøknadRequest
import no.nav.su.se.bakover.domain.søknad.SøknadPdfInnhold
import no.nav.su.se.bakover.service.ServiceBuilder
import no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLukkeSøknad
import no.nav.su.se.bakover.service.søknad.lukk.LukkSøknadService
import no.nav.su.se.bakover.service.søknad.lukk.LukketSøknad
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.applicationConfig
import no.nav.su.se.bakover.web.argThat
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.fixedClock
import no.nav.su.se.bakover.web.routes.sak.SakJson
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.routes.søknad.SøknadInnholdJson.Companion.toSøknadInnholdJson
import no.nav.su.se.bakover.web.routes.søknad.lukk.LukketJson
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.random.Random

internal class SøknadRoutesKtTest {

    private fun søknadInnhold(fnr: Fnr): SøknadInnhold = build(
        personopplysninger = SøknadInnholdTestdataBuilder.personopplysninger(
            fnr = fnr.toString()
        )
    )

    private val sakId: UUID = UUID.randomUUID()
    private val saksnummer = Random.nextLong(2021, Long.MAX_VALUE)
    private val tidspunkt = Tidspunkt.EPOCH

    private val sak = Sak(
        id = sakId,
        saksnummer = Saksnummer(saksnummer),
        opprettet = tidspunkt,
        fnr = FnrGenerator.random(),
        utbetalinger = emptyList()
    )
    private val søknadId = UUID.randomUUID()

    private val databaseRepos = DatabaseBuilder.build(EmbeddedDatabase.instance())
    private val sakRepo = databaseRepos.sak
    private val trekkSøknadRequest = LukkSøknadRequest.MedBrev.TrekkSøknad(
        søknadId = søknadId,
        saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "Z990Lokal"),
        trukketDato = 1.januar(2020)
    )

    private val mockServices = TestServicesBuilder.services()

    @Test
    fun `lagrer og henter søknad`() {
        val fnr = FnrGenerator.random()
        val søknadInnhold: SøknadInnhold = søknadInnhold(fnr)
        val soknadJson: String = objectMapper.writeValueAsString(søknadInnhold.toSøknadInnholdJson())
        withTestApplication({
            testSusebakover()
        }) {
            val createResponse = defaultRequest(
                Post,
                søknadPath,
                listOf(Brukerrolle.Veileder)
            ) {
                addHeader(ContentType, Json.toString())
                setBody(soknadJson)
            }.apply {
                response.status() shouldBe Created
            }.response

            shouldNotThrow<Throwable> { objectMapper.readValue<OpprettetSøknadJson>(createResponse.content!!) }

            val sakFraDb = sakRepo.hentSak(fnr)
            sakFraDb shouldNotBe null
            sakFraDb!!.søknader shouldHaveAtLeastSize 1
        }
    }

    @Test
    fun `knytter søknad til sak ved innsending`() {
        var sakId: String
        var saksnummer: Long
        withTestApplication({
            testSusebakover()
        }) {
            val fnr = FnrGenerator.random()
            val søknadInnhold: SøknadInnhold = søknadInnhold(fnr)
            val soknadJson: String = objectMapper.writeValueAsString(søknadInnhold.toSøknadInnholdJson())
            defaultRequest(Post, søknadPath, listOf(Brukerrolle.Veileder)) {
                addHeader(ContentType, Json.toString())
                setBody(soknadJson)
            }.apply {
                response.status() shouldBe Created
                val response = objectMapper.readValue<OpprettetSøknadJson>(response.content!!)
                sakId = response.søknad.sakId
                saksnummer = response.saksnummer
            }

            defaultRequest(Get, "$sakPath/$sakId", listOf(Brukerrolle.Veileder)).apply {
                response.status() shouldBe OK
                val sakJson = objectMapper.readValue<SakJson>(response.content!!)
                sakJson.søknader.first().søknadInnhold.personopplysninger.fnr shouldMatch fnr.toString()
                sakJson.saksnummer shouldBe saksnummer
            }
        }
    }

    @Test
    fun `skal opprette journalpost og oppgave ved opprettelse av søknad`() {
        val fnr = FnrGenerator.random()
        val søknadInnhold: SøknadInnhold = søknadInnhold(fnr)
        val soknadJson: String = objectMapper.writeValueAsString(søknadInnhold.toSøknadInnholdJson())

        val pdfGenerator: PdfGenerator = mock {
            on { genererPdf(any<SøknadPdfInnhold>()) } doReturn "pdf innhold".toByteArray().right()
        }
        val dokArkiv: DokArkiv = mock {
            on { opprettJournalpost(any<Journalpost.Søknadspost>()) } doReturn JournalpostId("9").right()
        }

        val personOppslag: PersonOppslag = mock {
            on { person(any()) } doReturn PersonOppslagStub.person(fnr)
            on { aktørId(any()) } doReturn PersonOppslagStub.aktørId(fnr)
            on { sjekkTilgangTilPerson(any()) } doReturn PersonOppslagStub.sjekkTilgangTilPerson(fnr)
        }
        val oppgaveClient: OppgaveClient = mock {
            on { opprettOppgave(any<OppgaveConfig.Saksbehandling>()) } doReturn OppgaveId("11").right()
        }

        val clients = TestClientsBuilder.build(applicationConfig).copy(
            pdfGenerator = pdfGenerator,
            dokArkiv = dokArkiv,
            personOppslag = personOppslag,
            oppgaveClient = oppgaveClient
        )

        val services = ServiceBuilder.build(
            databaseRepos = databaseRepos,
            clients = clients,
            behandlingMetrics = mock(),
            søknadMetrics = mock(),
            clock = fixedClock,
            unleash = mock(),
        )

        withTestApplication({
            testSusebakover(
                clients = clients,
                services = services,
            )
        }) {
            defaultRequest(
                Post,
                søknadPath,
                listOf(Brukerrolle.Veileder)
            ) {
                addHeader(ContentType, Json.toString())
                setBody(soknadJson)
            }.apply {
                response.status() shouldBe Created
                verify(pdfGenerator).genererPdf(any<SøknadPdfInnhold>())
                verify(dokArkiv).opprettJournalpost(any())
                // Kalles én gang i AccessCheckProxy og én gang eksplisitt i søknadService
                verify(personOppslag).person(argThat { it shouldBe fnr })
                verify(personOppslag).aktørId(argThat { it shouldBe fnr })
                verify(oppgaveClient).opprettOppgave(any())
            }
        }
    }

    @Test
    fun `lager en søknad, så trekker søknaden`() {
        val lukketSøknad: Søknad.Lukket = mock()
        val lukkSøknadServiceMock = mock<LukkSøknadService> {
            on { lukkSøknad(any()) } doReturn LukketSøknad.UtenMangler(sak, lukketSøknad).right()
        }

        withTestApplication({
            testSusebakover(services = mockServices.copy(lukkSøknad = lukkSøknadServiceMock))
        }) {
            defaultRequest(
                method = Post,
                uri = "$søknadPath/$søknadId/lukk",
                roller = listOf(Brukerrolle.Saksbehandler)
            ) {
                addHeader(ContentType, Json.toString())
                setBody(
                    objectMapper.writeValueAsString(
                        LukketJson.TrukketJson(
                            datoSøkerTrakkSøknad = 1.januar(2020),
                            type = Søknad.Lukket.LukketType.TRUKKET
                        )
                    )
                )
            }.apply {
                response.status() shouldBe OK
                verify(lukkSøknadServiceMock).lukkSøknad(
                    argThat { it shouldBe trekkSøknadRequest }
                )
            }
        }
    }

    @Test
    fun `en søknad som er trukket, skal ikke kunne bli trukket igjen`() {
        val lukkSøknadServiceMock = mock<LukkSøknadService> {
            on { lukkSøknad(any()) } doReturn KunneIkkeLukkeSøknad.SøknadErAlleredeLukket.left()
        }
        withTestApplication({
            testSusebakover(
                services = mockServices.copy(lukkSøknad = lukkSøknadServiceMock)
            )
        }) {
            defaultRequest(
                method = Post,
                uri = "$søknadPath/$søknadId/lukk",
                roller = listOf(Brukerrolle.Saksbehandler)
            ) {
                addHeader(ContentType, Json.toString())
                setBody(
                    objectMapper.writeValueAsString(
                        LukketJson.TrukketJson(
                            datoSøkerTrakkSøknad = 1.januar(2020),
                            type = Søknad.Lukket.LukketType.TRUKKET
                        )
                    )
                )
            }.apply {
                response.status() shouldBe BadRequest
                verify(lukkSøknadServiceMock).lukkSøknad(
                    argThat { it shouldBe trekkSøknadRequest }
                )
            }
        }
    }

    @Test
    fun `Krever fritekst`() {
        val lukkSøknadServiceMock = mock<LukkSøknadService> {
            on { lukkSøknad(any()) } doReturn KunneIkkeLukkeSøknad.SøknadErAlleredeLukket.left()
        }
        withTestApplication({
            testSusebakover(
                services = mockServices.copy(lukkSøknad = lukkSøknadServiceMock)
            )
        }) {
            defaultRequest(
                method = Post,
                uri = "$søknadPath/$søknadId/lukk",
                roller = listOf(Brukerrolle.Saksbehandler)
            ) {
                addHeader(ContentType, Json.toString())
                setBody(
                    objectMapper.writeValueAsString(
                        LukketJson.AvvistJson(
                            type = Søknad.Lukket.LukketType.AVVIST,
                            brevConfig = LukketJson.AvvistJson.BrevConfigJson(
                                brevtype = LukketJson.BrevType.FRITEKST,
                                null
                            )
                        )
                    )
                )
            }.apply {
                response.status() shouldBe BadRequest
                verifyNoMoreInteractions(lukkSøknadServiceMock)
            }
        }
    }

    @Test
    fun `en søknad med ugyldig json gir badrequest og gjør ingen kall`() {
        val lukkSøknadServiceMock = mock<LukkSøknadService> {
            on { lukkSøknad(any()) } doReturn KunneIkkeLukkeSøknad.SøknadErAlleredeLukket.left()
        }
        withTestApplication({
            testSusebakover(
                services = mockServices.copy(lukkSøknad = lukkSøknadServiceMock)
            )
        }) {
            defaultRequest(
                method = Post,
                uri = "$søknadPath/$søknadId/lukk",
                roller = listOf(Brukerrolle.Saksbehandler)
            ) {
                addHeader(ContentType, Json.toString())
                setBody(
                    """
                        {
                        "type" : "ugyldigtype",
                        "datoSøkerTrakkSøknad" : "2020-01-01"
                        }
                    """.trimIndent()
                )
            }.apply {
                response.status() shouldBe BadRequest
                verifyNoMoreInteractions(lukkSøknadServiceMock)
            }
        }
    }

    @Test
    fun `kan lage brevutkast av trukket søknad`() {
        val pdf = "".toByteArray()
        val lukkSøknadServiceMock = mock<LukkSøknadService> {
            on { lagBrevutkast(any()) } doReturn pdf.right()
        }
        withTestApplication({
            testSusebakover(services = mockServices.copy(lukkSøknad = lukkSøknadServiceMock))
        }) {
            defaultRequest(
                method = Post,
                uri = "$søknadPath/$søknadId/lukk/brevutkast",
                roller = listOf(Brukerrolle.Saksbehandler)
            ) {
                setBody(
                    objectMapper.writeValueAsString(
                        LukketJson.TrukketJson(
                            datoSøkerTrakkSøknad = 1.januar(2020),
                            type = Søknad.Lukket.LukketType.TRUKKET
                        )
                    )
                )
            }.apply {
                response.status() shouldBe OK
                verify(lukkSøknadServiceMock).lagBrevutkast(
                    argThat { it shouldBe trekkSøknadRequest }
                )
            }
        }
    }
}
