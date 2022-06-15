package no.nav.su.se.bakover.web.routes.klage

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.klage.KunneIkkeSendeTilAttestering
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.service.klage.KlageService
import no.nav.su.se.bakover.test.vurdertKlageTilAttestering
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

internal class SendKlageTilAttesteringTest {

    private val sakId: UUID = UUID.randomUUID()
    private val klageId: UUID = UUID.randomUUID()
    private val uri = "$sakPath/$sakId/klager/$klageId/tilAttestering"

    @Test
    fun `ingen tilgang gir unauthorized`() {
        testApplication {
            application {
                testSusebakover()
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
                    testSusebakover()
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
            feilkode = KunneIkkeSendeTilAttestering.FantIkkeKlage,
            status = HttpStatusCode.NotFound,
            body = "{\"message\":\"Fant ikke klage\",\"code\":\"fant_ikke_klage\"}",
        )
    }

    @Test
    fun `ugyldig tilstand`() {
        verifiserFeilkode(
            feilkode = KunneIkkeSendeTilAttestering.UgyldigTilstand(OpprettetKlage::class),
            status = HttpStatusCode.BadRequest,
            body = "{\"message\":\"Kan ikke gå fra tilstanden OpprettetKlage til tilstanden KlageTilAttestering\",\"code\":\"ugyldig_tilstand\"}",
        )
    }

    private fun verifiserFeilkode(
        feilkode: KunneIkkeSendeTilAttestering,
        status: HttpStatusCode,
        body: String,
    ) {
        val klageServiceMock = mock<KlageService> {
            on { sendTilAttestering(any(), any()) } doReturn feilkode.left()
        }
        testApplication {
            application {
                testSusebakover(
                    services = TestServicesBuilder.services()
                        .copy(klageService = klageServiceMock),
                )
            }
            defaultRequest(HttpMethod.Post, uri, listOf(Brukerrolle.Saksbehandler)).apply {
                status shouldBe status
                this.contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
                bodyAsText() shouldBe body
            }
        }
    }

    @Test
    fun `kan sende klage til attestering`() {
        val klageTilAttestering = vurdertKlageTilAttestering().second
        val klageServiceMock = mock<KlageService> {
            on { sendTilAttestering(any(), any()) } doReturn klageTilAttestering.right()
        }
        testApplication {
            application {
                testSusebakover(
                    services = TestServicesBuilder.services()
                        .copy(klageService = klageServiceMock),
                )
            }
            defaultRequest(HttpMethod.Post, uri, listOf(Brukerrolle.Saksbehandler)).apply {
                status shouldBe HttpStatusCode.OK
                this.contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
                JSONAssert.assertEquals(
                    //language=JSON
                    """
                {
                  "id":"${klageTilAttestering.id}",
                  "sakid":"${klageTilAttestering.sakId}",
                  "opprettet":"2021-01-01T01:02:03.456789Z",
                  "journalpostId":"klageJournalpostId",
                  "saksbehandler":"saksbehandler",
                  "datoKlageMottatt":"2021-12-01",
                  "status":"TIL_ATTESTERING_TIL_VURDERING",
                  "vedtakId":"${klageTilAttestering.vilkårsvurderinger.vedtakId}",
                  "innenforFristen":"JA",
                  "klagesDetPåKonkreteElementerIVedtaket":true,
                  "erUnderskrevet":"JA",
                  "begrunnelse":"begrunnelse",
                  "fritekstTilBrev":"fritekstTilBrev",
                  "vedtaksvurdering":{
                    "type":"OPPRETTHOLD",
                    "omgjør":null,
                    "oppretthold":{
                      "hjemler":[
                        "SU_PARAGRAF_3",
                        "SU_PARAGRAF_4"
                      ]
                    }
                  },
                  "attesteringer":[],
                  "klagevedtakshistorikk":[],
                  "avsluttet": "KAN_IKKE_AVSLUTTES"
                }
                    """.trimIndent(),
                    this.bodyAsText(),
                    true,
                )
            }
        }
    }
}
