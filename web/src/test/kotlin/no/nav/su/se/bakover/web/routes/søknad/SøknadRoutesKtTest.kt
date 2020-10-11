package no.nav.su.se.bakover.web.routes.søknad

import com.fasterxml.jackson.module.kotlin.readValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.capture
import com.nhaarman.mockitokotlin2.doAnswer
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
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
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
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.DatabaseBuilder
import no.nav.su.se.bakover.database.EmbeddedDatabase
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder.build
import no.nav.su.se.bakover.domain.TrukketSøknadBody
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.service.ServiceBuilder
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.SakJson
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.routes.søknad.SøknadInnholdJson.Companion.toSøknadInnholdJson
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.internal.verification.Times
import kotlin.test.assertEquals

internal class SøknadRoutesKtTest {
    private val fnr = Fnr("01010100001")

    private val søknadInnhold: SøknadInnhold = build(
        personopplysninger = SøknadInnholdTestdataBuilder.personopplysninger(
            fnr = fnr.toString()
        )
    )

    private val databaseRepos = DatabaseBuilder.build(EmbeddedDatabase.instance())
    private val soknadJson: String = objectMapper.writeValueAsString(søknadInnhold.toSøknadInnholdJson())
    private val sakRepo = databaseRepos.sak
    private val søknadRepo = databaseRepos.søknad

    @Test
    fun `lagrer og henter søknad`() {
        withTestApplication({
            testSusebakover()
        }) {
            val createResponse = defaultRequest(
                Post,
                søknadPath
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
            defaultRequest(Post, søknadPath) {
                addHeader(ContentType, Json.toString())
                setBody(soknadJson)
            }.apply {
                assertEquals(Created, response.status())
                sakNr = objectMapper.readValue<SakJson>(response.content!!).id
            }

            defaultRequest(Get, "$sakPath/$sakNr").apply {
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
                services = services
            )
        }) {
            defaultRequest(
                Post,
                søknadPath
            ) {
                addHeader(ContentType, Json.toString())
                setBody(soknadJson)
            }.apply {
                response.status() shouldBe Created
                verify(pdfGenerator, Times(1)).genererPdf(any())
                verify(dokArkiv, Times(1)).opprettJournalpost(any())
                verify(personOppslag, Times(1)).person(any())
                verify(personOppslag, Times(1)).aktørId(any())
                verify(oppgaveClient, Times(1)).opprettOppgave(any())
            }
        }
    }

    @Test
    fun `lager en søknad, så trekker søknaden`() {
        withTestApplication({
            testSusebakover()
        }) {
            val søknadCreateResponse = defaultRequest(
                Post,
                søknadPath
            ) {
                addHeader(ContentType, Json.toString())
                setBody(soknadJson)
            }.apply {
                assertEquals(Created, response.status())
            }.response

            shouldNotThrow<Throwable> { objectMapper.readValue<SakJson>(søknadCreateResponse.content!!) }

            val sak = sakRepo.hentSak(fnr)
            sak shouldNotBe null
            sak!!.søknader() shouldHaveAtLeastSize 1

            defaultRequest(Post, "$søknadPath/${sak.søknader().first().id}/trekkSoknad") {
                addHeader(ContentType, Json.toString())
                setBody(
                    """{
                        "sakId": "${sak.id}",
                        "søknadId": "${sak.søknader().first().id}",
                        "søknadTrukket": true
                    }
                    """.trimIndent()
                )
            }.apply {
                response.status() shouldBe OK
            }.response
        }
    }

    @Test
    fun `en søknad som er trukket, skal ikke kunne bli trukket igjen`() {
        withTestApplication({
            testSusebakover()
        }) {
            val sak = sakRepo.opprettSak(fnr)
            val søknad = søknadRepo.opprettSøknad(
                sakId = sak.id,
                søknad = Søknad(
                    sakId = sak.id,
                    søknadInnhold = build()
                )
            )
            søknadRepo.trekkSøknad(
                TrukketSøknadBody(
                    sakId = sak.id,
                    søknadId = søknad.id,
                    søknadTrukket = true
                )
            )

            defaultRequest(Post, "$søknadPath/${søknad.id}/trekkSoknad") {
                addHeader(ContentType, Json.toString())
                setBody(
                    """{
                        "sakId": "${sak.id}",
                        "søknadId": "${søknad.id}",
                        "søknadTrukket": true
                    }
                    """.trimIndent()
                )
            }.apply {
                response.status() shouldBe InternalServerError
            }.response
        }
    }
}
