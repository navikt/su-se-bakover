package no.nav.su.se.bakover.web.komponenttest

import io.kotest.matchers.shouldBe
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpStatusCode.Companion.Created
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.client.stubs.oppgave.STUBBEDOPPGAVEID
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.dokument.infrastructure.client.PdfByggerKlientImpl
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.test.application.defaultRequest
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.jwt.DEFAULT_IDENT
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.søknad.søknadinnholdUføre
import no.nav.su.se.bakover.web.TestClientsBuilder
import no.nav.su.se.bakover.web.routes.søknad.OpprettetSøknadJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.SøknadsinnholdJson.Companion.toSøknadsinnholdJson
import no.nav.su.se.bakover.web.routes.søknad.toJson
import no.nav.su.se.bakover.web.søknad.ny.NySøknadJson
import no.nav.su.se.bakover.web.søknad.ny.nyDigitalSøknad
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
internal class NySøknadKomponentTest(private val dataSource: DataSource) {
    @Test
    fun `innsending av ny førstegangssøknad søknad oppretter sak, journalfører søknad og oppretter oppgave`() {
        withKomptestApplication(
            dataSource,
            clock = fixedClock,
        ) { appComponents ->
            val (sakId, søknadId) = nyDigitalSøknad(client = this.client).let {
                UUID.fromString(NySøknadJson.Response.hentSakId(it)) to UUID.fromString(NySøknadJson.Response.hentSøknadId(it))
            }
            val sak = appComponents.services.sak.hentSak(sakId).getOrFail()
            appComponents.services.søknad.hentSøknad(søknadId).getOrFail().also { søknad ->
                søknad.shouldBeType<Søknad.Journalført.MedOppgave.IkkeLukket>().let {
                    it.fnr shouldBe sak.fnr
                    it.type shouldBe Sakstype.UFØRE
                    it.innsendtAv shouldBe NavIdentBruker.Veileder(DEFAULT_IDENT)
                    it.oppgaveId shouldBe OppgaveId(STUBBEDOPPGAVEID)
                    it.journalpostId shouldBe JournalpostId("0")
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = Brukerrolle::class, names = ["Veileder", "Saksbehandler"])
    fun `innsending bekrefter lagret søknad med Created selv om tokenhenting for PDF feiler`(rolle: Brukerrolle) {
        val clientId = "pdf-bygger"
        // mot pdfbygger trenger vi dette for å trigge feilen
        val azureAd = mock<AzureAd> {
            on { getSystemToken(clientId) } doThrow RuntimeException("Tokenhenting feilet")
        }
        val søknadsinnhold = søknadinnholdUføre()

        withKomptestApplication(
            dataSource,
            clock = fixedClock,
            clientsBuilder = { databaseRepos, clock, applicationConfig ->
                TestClientsBuilder(
                    clock = clock,
                    databaseRepos = databaseRepos,
                ).build(applicationConfig).copy(
                    pdfByggerKlient = PdfByggerKlientImpl(
                        baseUrl = "http://localhost",
                        azureAd = azureAd,
                        clientId = clientId,
                    ),
                )
            },
        ) { appComponents ->
            val response = defaultRequest(
                method = Post,
                uri = "/soknad/ufore",
                roller = listOf(rolle),
                client = this.client,
                body = serialize(søknadsinnhold.toSøknadsinnholdJson()),
            )

            response.status shouldBe Created
            val resultat = deserialize<OpprettetSøknadJson>(runBlocking { response.bodyAsText() })
            val lagretSøknad = appComponents.services.søknad.hentSøknad(
                UUID.fromString(resultat.søknad.id),
            ).getOrFail().shouldBeType<Søknad.Ny>()
            val sak = appComponents.services.sak.hentSak(lagretSøknad.sakId).getOrFail()

            resultat shouldBe OpprettetSøknadJson(
                saksnummer = sak.saksnummer.nummer,
                søknad = lagretSøknad.toJson(),
            )
            lagretSøknad.søknadInnhold shouldBe søknadsinnhold
            sak.søknader.single() shouldBe lagretSøknad
            appComponents.databaseRepos.søknad.hentSøknaderUtenJournalpost() shouldBe listOf(lagretSøknad)
            verify(azureAd).getSystemToken(clientId)
        }
    }
}
