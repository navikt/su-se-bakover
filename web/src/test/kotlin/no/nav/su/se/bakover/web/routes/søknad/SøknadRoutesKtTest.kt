package no.nav.su.se.bakover.web.routes.søknad

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.capture
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.ContentType.Application.Pdf
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
import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.database.søknad.LukketSøknadJson
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder.build
import no.nav.su.se.bakover.domain.brev.PdfTemplate
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.service.ServiceBuilder
import no.nav.su.se.bakover.service.Services
import no.nav.su.se.bakover.service.søknad.KunneIkkeLukkeSøknad
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.argThat
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.SakJson
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.routes.søknad.SøknadInnholdJson.Companion.toSøknadInnholdJson
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals

internal class SøknadRoutesKtTest {
    private val fnr = Fnr("01010100001")

    private val søknadInnhold: SøknadInnhold = build(
        personopplysninger = SøknadInnholdTestdataBuilder.personopplysninger(
            fnr = fnr.toString()
        )
    )
    private val sakId: UUID = UUID.randomUUID()
    private val tidspunkt = Tidspunkt.EPOCH
    private val sak: Sak = Sak(
        id = sakId,
        opprettet = tidspunkt,
        fnr = fnr,
        oppdrag = Oppdrag(
            id = UUID30.randomUUID(),
            opprettet = tidspunkt,
            sakId = sakId
        )
    )
    private val lukketSøknad = Søknad.Lukket.Trukket(
        tidspunkt = tidspunkt,
        saksbehandler = Saksbehandler(navIdent = "navident"),
        datoSøkerTrakkSøknad = LocalDate.now()
    )
    private val lukketSøknadBody =
        LukketSøknadBody(typeLukking = LukketSøknadJson.TypeLukking.Trukket, datoSøkerTrakkSøknad = LocalDate.now())

    private val lukketSøknadBodyJson = objectMapper.writeValueAsString(lukketSøknadBody)

    private val databaseRepos = DatabaseBuilder.build(EmbeddedDatabase.instance())

    private val soknadJson: String = objectMapper.writeValueAsString(søknadInnhold.toSøknadInnholdJson())
    private val sakRepo = databaseRepos.sak

    @Test
    fun `lagrer og henter søknad`() {
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
            on {
                genererPdf(innholdJson = any(), pdfTemplate = argThat { it shouldBe PdfTemplate.Søknad })
            } doReturn "some-pdf-document".toByteArray().right()
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
            val aktørIdCaptor = ArgumentCaptor.forClass(Fnr::class.java)
            on { person(capture<Fnr>(fnrCaptor)) } doAnswer { PersonOppslagStub.person(fnrCaptor.value) }
            on { aktørId(capture<Fnr>(aktørIdCaptor)) } doAnswer { PersonOppslagStub.aktørId(aktørIdCaptor.value) }
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
                verify(pdfGenerator).genererPdf(any(), eq(PdfTemplate.Søknad))
                verify(dokArkiv).opprettJournalpost(any())
                verify(personOppslag).person(any())
                verify(personOppslag).aktørId(any())
                verify(oppgaveClient).opprettOppgave(any())
            }
        }
    }

    @Test
    fun `lager en søknad, så trekker søknaden`() {
        val søknadId = UUID.randomUUID()
        val søknadServiceMock = mock<SøknadService> {
            on { lukkSøknad(any(), any()) } doReturn sak.right()
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
                    stansUtbetaling = mock(),
                    startUtbetalinger = mock()
                )
            )
        }) {
            defaultRequest(
                method = Post,
                uri = "$søknadPath/$søknadId/lukk",
                roller = listOf(Brukerrolle.Saksbehandler)
            ) {
                addHeader(ContentType, Json.toString())
                setBody(lukketSøknadBodyJson)
            }.apply {
                response.status() shouldBe OK
                verify(søknadServiceMock).lukkSøknad(
                    argThat { it shouldBe søknadId },
                    argThat {
                        it shouldBe Søknad.Lukket.Trukket(
                            tidspunkt = it.tidspunkt,
                            saksbehandler = Saksbehandler(navIdent = "navident"),
                            datoSøkerTrakkSøknad = LocalDate.now()
                        )
                    }
                )
            }.response
        }
    }

    @Test
    fun `ugyldig body på lukk gir 400`() {
        val søknadId = UUID.randomUUID()
        val søknadServiceMock = mock<SøknadService> {
            on { lukkSøknad(any(), any()) } doReturn sak.right()
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
                    stansUtbetaling = mock(),
                    startUtbetalinger = mock()
                )
            )
        }) {
            defaultRequest(
                method = Post,
                uri = "$søknadPath/$søknadId/lukk",
                roller = listOf(Brukerrolle.Saksbehandler)
            ) {
                addHeader(ContentType, Json.toString())
            }.apply {
                response.status() shouldBe BadRequest
                verify(søknadServiceMock, times(0)).lukkSøknad(
                    argThat { it shouldBe søknadId },
                    argThat {
                        it shouldBe Søknad.Lukket.Trukket(
                            tidspunkt = it.tidspunkt,
                            saksbehandler = Saksbehandler(navIdent = "navident"),
                            datoSøkerTrakkSøknad = LocalDate.now()
                        )
                    }

                )
            }.response
        }
    }

    @Test
    fun `en søknad som er trukket, skal ikke kunne bli trukket igjen`() {
        val søknadId = UUID.randomUUID()
        val søknadServiceMock = mock<SøknadService> {
            on { lukkSøknad(any(), any()) } doReturn KunneIkkeLukkeSøknad.SøknadErAlleredeLukket.left()
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
                    stansUtbetaling = mock(),
                    startUtbetalinger = mock()
                )
            )
        }) {
            defaultRequest(
                method = Post,
                uri = "$søknadPath/$søknadId/lukk",
                roller = listOf(Brukerrolle.Saksbehandler)
            ) {
                addHeader(ContentType, Json.toString())
                setBody(lukketSøknadBodyJson)
            }.apply {
                response.status() shouldBe BadRequest
                verify(søknadServiceMock, times(1)).lukkSøknad(
                    argThat { it shouldBe søknadId },
                    argThat {
                        it shouldBe Søknad.Lukket.Trukket(
                            tidspunkt = it.tidspunkt,
                            saksbehandler = Saksbehandler(navIdent = "navident"),
                            datoSøkerTrakkSøknad = LocalDate.now()
                        )
                    }
                )
            }.response
        }
    }

    @Test
    fun `kall mot brevutkast skal gi status 200 OK`() {
        val pdf = "some-pdf-document".toByteArray()

        val søknadId = UUID.randomUUID()
        val søknadServiceMock = mock<SøknadService> {
            on {
                lagLukketSøknadBrevutkast(
                    argThat {
                        it shouldBe søknadId
                    },
                    argThat {
                        it.saksbehandler shouldBe lukketSøknad.saksbehandler
                    }
                )
            } doReturn pdf.right()
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
                    stansUtbetaling = mock(),
                    startUtbetalinger = mock()
                )
            )
        }) {
            defaultRequest(
                method = Post,
                uri = "$søknadPath/$søknadId/lukk/brevutkast",
                roller = listOf(Brukerrolle.Saksbehandler)
            ) {
                addHeader(ContentType, Pdf.contentType)
                setBody(lukketSøknadBodyJson)
            }.apply {
                response.status() shouldBe OK
                verify(søknadServiceMock).lagLukketSøknadBrevutkast(
                    argThat { it shouldBe søknadId },
                    argThat {
                        it shouldBe Søknad.Lukket.Trukket(
                            tidspunkt = it.tidspunkt,
                            saksbehandler = Saksbehandler(navIdent = "navident"),
                            datoSøkerTrakkSøknad = LocalDate.now()
                        )
                    }

                )
            }.response
        }
    }

    @Test
    fun `ingen body på brevutkast gir 400`() {
        val pdf = "some-pdf-document".toByteArray()

        val søknadId = UUID.randomUUID()
        val søknadServiceMock = mock<SøknadService> {
            on { lagLukketSøknadBrevutkast(søknadId, lukketSøknad) } doReturn pdf.right()
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
                    stansUtbetaling = mock(),
                    startUtbetalinger = mock()
                )
            )
        }) {
            defaultRequest(
                method = Post,
                uri = "$søknadPath/$søknadId/lukk/brevutkast",
                roller = listOf(Brukerrolle.Saksbehandler)
            ) {
                addHeader(ContentType, Pdf.contentType)
            }.apply {
                response.status() shouldBe BadRequest
                verify(søknadServiceMock, times(0)).lagLukketSøknadBrevutkast(
                    argThat { it shouldBe søknadId },
                    argThat {
                        it shouldBe Søknad.Lukket.Trukket(
                            tidspunkt = it.tidspunkt,
                            saksbehandler = Saksbehandler(navIdent = "navident"),
                            datoSøkerTrakkSøknad = LocalDate.now()
                        )
                    }

                )
            }.response
        }
    }

    @Test
    fun `ugyldig body på brevutkast gir 400`() {
        val pdf = "some-pdf-document".toByteArray()

        val søknadId = UUID.randomUUID()
        val søknadServiceMock = mock<SøknadService> {
            on { lagLukketSøknadBrevutkast(søknadId, lukketSøknad) } doReturn pdf.right()
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
                    stansUtbetaling = mock(),
                    startUtbetalinger = mock()
                )
            )
        }) {
            defaultRequest(
                method = Post,
                uri = "$søknadPath/$søknadId/lukk/brevutkast",
                roller = listOf(Brukerrolle.Saksbehandler)
            ) {
                addHeader(ContentType, Pdf.contentType)
                setBody(
                    objectMapper.writeValueAsString(
                        LukketSøknadJson(
                            Tidspunkt.now().toString(),
                            Saksbehandler("12345").toString(),
                            LukketSøknadJson.TypeLukking.Trukket
                        )
                    )
                )
            }.apply {
                response.status() shouldBe BadRequest
                verify(søknadServiceMock, times(0)).lagLukketSøknadBrevutkast(
                    argThat { it shouldBe søknadId },
                    argThat { it shouldBe LukketSøknadJson.TypeLukking.Trukket }
                )
            }.response
        }
    }
}
