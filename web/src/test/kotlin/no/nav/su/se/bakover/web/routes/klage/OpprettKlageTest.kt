package no.nav.su.se.bakover.web.routes.klage

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.domain.klage.KunneIkkeOppretteKlage
import no.nav.su.se.bakover.service.klage.KlageService
import no.nav.su.se.bakover.test.opprettetKlage
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
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
        testApplication {
            application {
                testSusebakoverWithMockedDb()
            }
            defaultRequest(
                HttpMethod.Post,
                uri,
                emptyList(),
            ).apply {
                status shouldBe HttpStatusCode.Unauthorized
                bodyAsText() shouldBe ""
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
            testApplication {
                application {
                    testSusebakoverWithMockedDb()
                }
                defaultRequest(
                    HttpMethod.Post,
                    uri,
                    it,
                ).apply {
                    status shouldBe HttpStatusCode.Forbidden
                    bodyAsText() shouldBe "{\"message\":\"Bruker mangler en av de tillatte rollene: [Saksbehandler]\",\"code\":\"mangler_rolle\"}"
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
        val klageServiceMock = if (feilkode != null) {
            mock<KlageService> {
                on { opprett(any()) } doReturn feilkode.left()
            }
        } else {
            mock()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services()
                        .copy(klageService = klageServiceMock),
                )
            }
            defaultRequest(HttpMethod.Post, path, listOf(Brukerrolle.Saksbehandler)) {
                setBody(validBody)
            }.apply {
                this.status shouldBe status
                this.contentType() shouldBe ContentType.parse("application/json")
                this.bodyAsText() shouldBe body
            }
        }
    }

    @Test
    fun `kan opprette klage`() {
        val opprettetKlage = opprettetKlage().second
        val klageServiceMock = mock<KlageService> {
            on { opprett(any()) } doReturn opprettetKlage.right()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services()
                        .copy(klageService = klageServiceMock),
                )
            }
            defaultRequest(HttpMethod.Post, uri, listOf(Brukerrolle.Saksbehandler)) {
                setBody(validBody)
            }.apply {
                status shouldBe HttpStatusCode.Created
                this.contentType() shouldBe ContentType.parse("application/json")
                JSONAssert.assertEquals(
                    //language=JSON
                    """
                {
                  "id":"${opprettetKlage.id}",
                  "sakid":"${opprettetKlage.sakId}",
                  "opprettet":"2021-02-01T01:02:03.456789Z",
                  "journalpostId":"klageJournalpostId",
                  "saksbehandler":"saksbehandler",
                  "datoKlageMottatt":"2021-01-15",
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
                  "avsluttet": "KAN_AVSLUTTES",
                  "avsluttetTidspunkt": null
                }
                    """.trimIndent(),
                    bodyAsText(),
                    true,
                )
            }
        }
    }
}
