package no.nav.su.se.bakover.web.routes.klage

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.contentType
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.klage.KunneIkkeUnderkjenne
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import no.nav.su.se.bakover.service.klage.KlageService
import no.nav.su.se.bakover.test.underkjentKlageTilVurdering
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

internal class UnderkjennKlageTest {
    //language=JSON
    private val validBody = """
        {
            "grunn": "ANDRE_FORHOLD",
            "kommentar": "Ingen kommentar."
        }
    """.trimIndent()

    private val sakId: UUID = UUID.randomUUID()
    private val klageId: UUID = UUID.randomUUID()
    private val uri = "$sakPath/$sakId/klager/$klageId/underkjenn"

    @Test
    fun `ingen tilgang gir unauthorized`() {
        withTestApplication(
            {
                testSusebakover()
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                uri,
                emptyList(),
            ).apply {
                response.status() shouldBe HttpStatusCode.Unauthorized
                response.content shouldBe null
            }
        }
    }

    @Test
    fun `Kun attestant skal ha tilgang`() {
        listOf(
            listOf(Brukerrolle.Veileder),
            listOf(Brukerrolle.Saksbehandler),
            listOf(Brukerrolle.Drift),
        ).forEach {
            withTestApplication(
                {
                    testSusebakover()
                },
            ) {
                defaultRequest(
                    HttpMethod.Post,
                    uri,
                    it,
                ).apply {
                    response.status() shouldBe HttpStatusCode.Forbidden
                    response.content shouldBe "{\"message\":\"Bruker mangler en av de tillatte rollene: Attestant.\"}"
                }
            }
        }
    }

    @Test
    fun `ugyldig underkjennelsesgrunn`() {
        withTestApplication(
            {
                testSusebakover()
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                uri,
                listOf(Brukerrolle.Attestant),
            ) {
                setBody("""{"grunn": "UGYLDIG_GRUNN", "kommentar": "Ingen kommentar."}""")
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldBe "{\"message\":\"Ugyldig underkjennelsesgrunn\",\"code\":\"ugyldig_grunn_for_underkjenning\"}"
            }
        }
    }

    @Test
    fun `fant ikke klage`() {
        verifiserFeilkode(
            feilkode = KunneIkkeUnderkjenne.FantIkkeKlage,
            status = HttpStatusCode.NotFound,
            body = "{\"message\":\"Fant ikke klage\",\"code\":\"fant_ikke_klage\"}",
        )
    }

    @Test
    fun `ugyldig tilstand`() {
        verifiserFeilkode(
            feilkode = KunneIkkeUnderkjenne.UgyldigTilstand(OpprettetKlage::class, OversendtKlage::class),
            status = HttpStatusCode.BadRequest,
            body = "{\"message\":\"Kan ikke gå fra tilstanden OpprettetKlage til tilstanden OversendtKlage\",\"code\":\"ugyldig_tilstand\"}",
        )
    }

    private fun verifiserFeilkode(
        feilkode: KunneIkkeUnderkjenne,
        status: HttpStatusCode,
        body: String,
    ) {
        val klageServiceMock = mock<KlageService> {
            on { underkjenn(any()) } doReturn feilkode.left()
        }
        withTestApplication(
            {
                testSusebakover(
                    services = TestServicesBuilder.services()
                        .copy(klageService = klageServiceMock),
                )
            },
        ) {
            defaultRequest(HttpMethod.Post, uri, listOf(Brukerrolle.Attestant)) {
                setBody(validBody)
            }
        }.apply {
            response.status() shouldBe status
            response.contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
            response.content shouldBe body
        }
    }

    @Test
    fun `kan underkjenne klage`() {
        val underkjentKlage = underkjentKlageTilVurdering().second
        val klageServiceMock = mock<KlageService> {
            on { underkjenn(any()) } doReturn underkjentKlage.right()
        }
        withTestApplication(
            {
                testSusebakover(
                    services = TestServicesBuilder.services()
                        .copy(klageService = klageServiceMock),
                )
            },
        ) {
            defaultRequest(HttpMethod.Post, uri, listOf(Brukerrolle.Attestant)) {
                setBody(validBody)
            }
        }.apply {
            response.status() shouldBe HttpStatusCode.OK
            response.contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
            JSONAssert.assertEquals(
                //language=JSON
                """
                {
                  "id":"${underkjentKlage.id}",
                  "sakid":"${underkjentKlage.sakId}",
                  "opprettet":"2021-01-01T01:02:03.456789Z",
                  "journalpostId":"klageJournalpostId",
                  "saksbehandler":"saksbehandler",
                  "datoKlageMottatt":"2021-12-01",
                  "status":"VURDERT_BEKREFTET",
                  "vedtakId":"${underkjentKlage.vilkårsvurderinger.vedtakId}",
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
                  "attesteringer":[
                    {
                      "attestant":"attestant",
                      "underkjennelse":{
                        "grunn":"ANDRE_FORHOLD",
                        "kommentar":"attesteringskommentar"
                      },
                      "opprettet":"2021-01-01T01:02:03.456789Z"
                    }
                  ],
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
