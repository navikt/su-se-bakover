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
import no.nav.su.se.bakover.domain.klage.KunneIkkeLeggeTilFritekstForAvvist
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.service.klage.KlageService
import no.nav.su.se.bakover.test.avvistKlage
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.SAK_PATH
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class AvvistKlageTest {

    //language=JSON
    private val validBody = """
        {
            "fritekst": "1010101010 1010 10101"
        }
    """.trimIndent()

    private val sakId: UUID = UUID.randomUUID()
    private val klageId: UUID = UUID.randomUUID()
    private val uri = "$SAK_PATH/$sakId/klager/$klageId/avvist/fritekstTilBrev"

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
    fun `fant ikke klage`() {
        verifiserFeilkode(
            feilkode = KunneIkkeLeggeTilFritekstForAvvist.FantIkkeKlage,
            status = HttpStatusCode.NotFound,
            body = "{\"message\":\"Fant ikke klage\",\"code\":\"fant_ikke_klage\"}",
        )
    }

    @Test
    fun `ugyldig tilstand`() {
        verifiserFeilkode(
            feilkode = KunneIkkeLeggeTilFritekstForAvvist.UgyldigTilstand(OpprettetKlage::class),
            status = HttpStatusCode.BadRequest,
            body = "{\"message\":\"Kan ikke gå fra tilstanden OpprettetKlage til tilstanden AvvistKlage\",\"code\":\"ugyldig_tilstand\"}",
        )
    }

    private fun verifiserFeilkode(
        feilkode: KunneIkkeLeggeTilFritekstForAvvist,
        status: HttpStatusCode,
        body: String,
    ) {
        val klageServiceMock = mock<KlageService> {
            on { leggTilAvvistFritekstTilBrev(any(), any(), any()) } doReturn feilkode.left()
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
                this.status shouldBe status
                this.contentType() shouldBe ContentType.parse("application/json")
                this.bodyAsText() shouldBe body
            }
        }
    }

    @Test
    fun `kan legge fritekst til en avvist klage sitt vedtaksbrev`() {
        val klage = avvistKlage().second
        val klageServiceMock = mock<KlageService> {
            on { leggTilAvvistFritekstTilBrev(any(), any(), any()) } doReturn klage.right()
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
                status shouldBe HttpStatusCode.OK
                this.contentType() shouldBe ContentType.parse("application/json")
                JSONAssert.assertEquals(
                    //language=JSON
                    """
                {
                  "id":"${klage.id}",
                  "sakid":"${klage.sakId}",
                  "opprettet":"2021-02-01T01:02:03.456789Z",
                  "journalpostId":"klageJournalpostId",
                  "saksbehandler":"saksbehandler",
                  "datoKlageMottatt":"2021-01-15",
                  "status":"AVVIST",
                  "vedtakId":"${klage.vilkårsvurderinger.vedtakId}",
                  "innenforFristen":"NEI",
                  "klagesDetPåKonkreteElementerIVedtaket":true,
                  "erUnderskrevet":"JA",
                  "begrunnelse":"begrunnelse",
                  "vedtaksvurdering":null,
                  "attesteringer":[],
                  "fritekstTilBrev": "dette er en fritekst med person opplysninger",
                  "klagevedtakshistorikk": [],
                  "avsluttet": "KAN_AVSLUTTES",
                  "avsluttetTidspunkt": null,
                  "avsluttetBegrunnelse": null
                }
                    """.trimIndent(),
                    this.bodyAsText(),
                    true,
                )
            }
        }
    }
}
