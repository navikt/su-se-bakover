package no.nav.su.se.bakover.web.routes.søknad

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.capture
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
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
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.client.stubs.dokarkiv.DokArkivStub
import no.nav.su.se.bakover.client.stubs.oppgave.OppgaveClientStub
import no.nav.su.se.bakover.client.stubs.pdf.PdfGeneratorStub
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder.build
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.service.ServiceBuilder
import no.nav.su.se.bakover.service.Services
import no.nav.su.se.bakover.service.søknad.KunneIkkeLukkeSøknad
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.argThat
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.SakJson
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.routes.søknad.SøknadInnholdJson.Companion.toSøknadInnholdJson
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.internal.verification.Times
import java.util.UUID
import kotlin.test.assertEquals

internal class SøknadRoutesKtTest {

    private fun søknadInnhold(fnr: Fnr): SøknadInnhold = build(
        personopplysninger = SøknadInnholdTestdataBuilder.personopplysninger(
            fnr = fnr.toString()
        )
    )

    private val sakId: UUID = UUID.randomUUID()
    private val tidspunkt = Tidspunkt.EPOCH

    private val databaseRepos = DatabaseBuilder.build(EmbeddedDatabase.instance())
    private val sakRepo = databaseRepos.sak

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
                assertEquals(Created, response.status())
            }.response

            shouldNotThrow<Throwable> { objectMapper.readValue<SakJson>(createResponse.content!!) }

            val sakFraDb = sakRepo.hentSak(fnr)
            sakFraDb shouldNotBe null
            sakFraDb!!.søknader() shouldHaveAtLeastSize 1
        }
    }

    @Test
    fun `knytter søknad til sak ved innsending`() {
        var sakNr: String
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
                assertEquals(Created, response.status())
                sakNr = objectMapper.readValue<SakJson>(response.content!!).id
            }

            defaultRequest(Get, "$sakPath/$sakNr", listOf(Brukerrolle.Veileder)).apply {
                assertEquals(OK, response.status())
                val sakJson = objectMapper.readValue<SakJson>(response.content!!)
                sakJson.søknader.first().søknadInnhold.personopplysninger.fnr shouldMatch fnr.toString()
            }
        }
    }

    @Test
    fun `skal opprette journalpost og oppgave ved opprettelse av søknad`() {
        val pdfGenerator: PdfGenerator = mock {
            val captor = ArgumentCaptor.forClass(SøknadInnhold::class.java)
            on { genererPdf(capture<SøknadInnhold>(captor)) } doAnswer { PdfGeneratorStub.genererPdf(captor.value) }
        }
        val dokArkiv: DokArkiv = mock {
            val captor = ArgumentCaptor.forClass(Journalpost.Søknadspost::class.java)
            on { opprettJournalpost(capture<Journalpost.Søknadspost>(captor)) } doAnswer {
                DokArkivStub.opprettJournalpost(
                    captor.value
                )
            }
        }
        val personOppslag: PersonOppslag = mock {
            val fnrCaptor = ArgumentCaptor.forClass(Fnr::class.java)
            on { person(capture<Fnr>(fnrCaptor)) } doAnswer { PersonOppslagStub.person(fnrCaptor.value) }
        }
        val oppgaveClient: OppgaveClient = mock {
            val captor = ArgumentCaptor.forClass(OppgaveConfig.Saksbehandling::class.java)
            on { opprettOppgave(capture<OppgaveConfig.Saksbehandling>(captor)) } doAnswer {
                OppgaveClientStub.opprettOppgave(
                    captor.value
                )
            }
        }

        val clients = TestClientsBuilder.build().copy(
            pdfGenerator = pdfGenerator,
            dokArkiv = dokArkiv,
            personOppslag = personOppslag,
            oppgaveClient = oppgaveClient
        )

        val services = ServiceBuilder(
            databaseRepos = databaseRepos,
            clients = clients
        ).build()

        val fnr = FnrGenerator.random()
        val søknadInnhold: SøknadInnhold = søknadInnhold(fnr)
        val soknadJson: String = objectMapper.writeValueAsString(søknadInnhold.toSøknadInnholdJson())

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
                verify(pdfGenerator, Times(1)).genererPdf(any<SøknadInnhold>())
                verify(dokArkiv, Times(1)).opprettJournalpost(any())
                // Kalles én gang i AccessCheckProxy og én gang eksplisitt i søknadService
                verify(personOppslag, Times(2)).person(any())
                verify(oppgaveClient, Times(1)).opprettOppgave(any())
            }
        }
    }

    @Test
    fun `lager en søknad, så trekker søknaden`() {
        val søknadId = UUID.randomUUID()
        val navIdent = "navident"
        val sak = Sak(
            id = sakId,
            opprettet = tidspunkt,
            fnr = FnrGenerator.random(),
            oppdrag = Oppdrag(
                id = UUID30.randomUUID(),
                opprettet = tidspunkt,
                sakId = sakId
            )
        )
        val søknadServiceMock = mock<SøknadService> {
            on { trekkSøknad(any(), any(), any()) } doReturn sak.right()
        }
        withTestApplication({
            testSusebakover(
                services = Services(
                    avstemming = mock(),
                    utbetaling = mock(),
                    oppdrag = mock(),
                    behandling = mock(),
                    sak = mock(),
                    søknad = søknadServiceMock,
                    brev = mock()
                )
            )
        }) {
            defaultRequest(
                method = Post,
                uri = "$søknadPath/$søknadId/trekk",
                roller = listOf(Brukerrolle.Saksbehandler)
            ) {
                addHeader(ContentType, Json.toString())
                setBody(objectMapper.writeValueAsString(TrekkSøknadJson(1.januar(2020))))
            }.apply {
                response.status() shouldBe OK
                verify(søknadServiceMock).trekkSøknad(
                    argThat { it shouldBe søknadId },
                    argThat { it shouldBe 1.januar(2020) },
                    argThat { it shouldBe Saksbehandler(navIdent) }
                )
            }
        }
    }

    @Test
    fun `en søknad som er trukket, skal ikke kunne bli trukket igjen`() {
        val søknadId = UUID.randomUUID()
        val navIdent = "navident"
        val søknadServiceMock = mock<SøknadService> {
            on { trekkSøknad(any(), any(), any()) } doReturn KunneIkkeLukkeSøknad.SøknadErAlleredeLukket.left()
        }
        withTestApplication({
            testSusebakover(
                services = Services(
                    avstemming = mock(),
                    utbetaling = mock(),
                    oppdrag = mock(),
                    behandling = mock(),
                    sak = mock(),
                    søknad = søknadServiceMock,
                    brev = mock()
                )
            )
        }) {
            defaultRequest(
                method = Post,
                uri = "$søknadPath/$søknadId/trekk",
                roller = listOf(Brukerrolle.Saksbehandler)
            ) {
                addHeader(ContentType, Json.toString())
                setBody(objectMapper.writeValueAsString(TrekkSøknadJson(1.januar(2020))))
            }.apply {
                response.status() shouldBe BadRequest
                verify(søknadServiceMock).trekkSøknad(
                    argThat { it shouldBe søknadId },
                    argThat { it shouldBe 1.januar(2020) },
                    argThat { it shouldBe Saksbehandler(navIdent) }
                )
            }
        }
    }

    @Test
    fun `kan lage brevutkast av trukket søknad`() {
        val pdf = "".toByteArray()
        val søknadServiceMock = mock<SøknadService> {
            on { lagBrevutkastForTrukketSøknad(any(), any()) } doReturn pdf.right()
        }
        withTestApplication({
            testSusebakover(
                services = Services(
                    avstemming = mock(),
                    utbetaling = mock(),
                    oppdrag = mock(),
                    behandling = mock(),
                    sak = mock(),
                    søknad = søknadServiceMock,
                    brev = mock()
                )
            )
        }) {
            defaultRequest(
                method = Post,
                uri = "$søknadPath/${UUID.randomUUID()}/lukk/brevutkast",
                roller = listOf(Brukerrolle.Saksbehandler)
            ) {
                setBody(
                    objectMapper.writeValueAsString(
                        TrekkSøknadJson(1.januar(2020))
                    )
                )
            }.apply {
                response.status() shouldBe OK
                verify(søknadServiceMock).lagBrevutkastForTrukketSøknad(any(), any())
            }
        }
    }
}
