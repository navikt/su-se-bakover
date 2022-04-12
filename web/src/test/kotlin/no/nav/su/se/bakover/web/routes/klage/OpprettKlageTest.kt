package no.nav.su.se.bakover.web.routes.klage

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.http.HttpMethod
import io.ktor.server.server.testing.contentType
import io.ktor.server.server.testing.setBody
import io.ktor.server.server.testing.withTestApplication
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.klage.KunneIkkeOppretteKlage
import no.nav.su.se.bakover.service.klage.KlageService
import no.nav.su.se.bakover.test.opprettetKlage
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class OpprettKlageTest {
    //language=JSON
    private val validBody = """
        {
            "journalpostId": "1",
             "datoKlageMottatt": "2021-01-01"
        }
    """.trimIndent()
    private val sakId = UUID.randomUUID()
    private val uri = "$sakPath/$sakId/klager"

    @Test
    fun `ingen tilgang gir unauthorized`() {
        testApplication(
            {
                testSusebakover()
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                uri,
                emptyList(),
            ).apply {
                status shouldBe HttpStatusCode.Unauthorized
                body<String>()Be null
            }
        }
    }

    @Test
    fun `Kun saksbehandler skal ha tilgang`() {
        listOf(
            listOf(Brukerrolle.Veileder),
            listOf(Brukerrolle.Attestant),
            listOf(Brukerrolle.Drift),
        ).forEach {
            testApplication(
                {
                    testSusebakover()
                },
            ) {
                defaultRequest(
                    HttpMethod.Post,
                    uri,
                    it,
                ).apply {
                    status shouldBe HttpStatusCode.Forbidden
                    body<String>()Be "{\"message\":\"Bruker mangler en av de tillatte rollene: Saksbehandler.\"}"
                }
            }
        }
    }

    @Test
    fun `Sak id må være en uuid`() {
        verifiserFeilkode(
            path = klagePath,
            feilkode = null,
            status = HttpStatusCode.BadRequest,
            body = "{\"message\":\"sakId er ikke en gyldig UUID\",\"code\":\"sakId_mangler_eller_feil_format\"}",
        )
    }

    @Test
    fun `fant ikke sak`() {
        verifiserFeilkode(
            path = uri,
            feilkode = KunneIkkeOppretteKlage.FantIkkeSak,
            status = HttpStatusCode.NotFound,
            body = "{\"message\":\"Fant ikke sak\",\"code\":\"fant_ikke_sak\"}",
        )
    }

    @Test
    fun `finnes allerede en åpen klage`() {
        verifiserFeilkode(
            path = uri,
            feilkode = KunneIkkeOppretteKlage.FinnesAlleredeEnÅpenKlage,
            status = HttpStatusCode.BadRequest,
            body = "{\"message\":\"Det finnes allerede en åpen klage\",\"code\":\"finnes_allerede_en_åpen_klage\"}",
        )
    }

    @Test
    fun `kunne ikke opprette oppgave`() {
        verifiserFeilkode(
            path = uri,
            feilkode = KunneIkkeOppretteKlage.KunneIkkeOppretteOppgave,
            status = HttpStatusCode.InternalServerError,
            body = "{\"message\":\"Kunne ikke opprette oppgave\",\"code\":\"kunne_ikke_opprette_oppgave\"}",
        )
    }

    private fun verifiserFeilkode(
        path: String,
        feilkode: KunneIkkeOppretteKlage?,
        status: HttpStatusCode,
        body: String,
    ) {
        val klageServiceMock = if (feilkode != null) mock<KlageService> {
            on { opprett(any()) } doReturn feilkode.left()
        } else mock()
        testApplication(
            {
                testSusebakover(
                    services = TestServicesBuilder.services()
                        .copy(klageService = klageServiceMock),
                )
            },
        ) {
            defaultRequest(HttpMethod.Post, path, listOf(Brukerrolle.Saksbehandler)) {
                setBody(validBody)
            }
        }.apply {
            status shouldBe status
            response.contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
            body<String>()Be body
        }
    }

    @Test
    fun `kan opprette klage`() {
        val opprettetKlage = opprettetKlage().second
        val klageServiceMock = mock<KlageService> {
            on { opprett(any()) } doReturn opprettetKlage.right()
        }
        testApplication(
            {
                testSusebakover(
                    services = TestServicesBuilder.services()
                        .copy(klageService = klageServiceMock),
                )
            },
        ) {
            defaultRequest(HttpMethod.Post, uri, listOf(Brukerrolle.Saksbehandler)) {
                setBody(validBody)
            }
        }.apply {
            status shouldBe HttpStatusCode.Created
            response.contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
            JSONAssert.assertEquals(
                //language=JSON
                """
                {
                  "id":"${opprettetKlage.id}",
                  "sakid":"${opprettetKlage.sakId}",
                  "opprettet":"2021-01-01T01:02:03.456789Z",
                  "journalpostId":"klageJournalpostId",
                  "saksbehandler":"saksbehandler",
                  "datoKlageMottatt":"2021-12-01",
                  "status":"OPPRETTET",
                  "vedtakId":null,
                  "innenforFristen":null,
                  "klagesDetPåKonkreteElementerIVedtaket":null,
                  "erUnderskrevet":null,
                  "begrunnelse":null,
                  "fritekstTilBrev":null,
                  "vedtaksvurdering":null,
                  "attesteringer":[],
                  "klagevedtakshistorikk": [],
                  "avsluttet": "KAN_AVSLUTTES"
                }
                """.trimIndent(),
                response.content,
                true,
            )
        }
    }
}
