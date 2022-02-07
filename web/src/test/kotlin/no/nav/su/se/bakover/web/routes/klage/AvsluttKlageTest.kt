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
import no.nav.su.se.bakover.domain.klage.IverksattAvvistKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeAvslutteKlage
import no.nav.su.se.bakover.domain.klage.VurdertKlage
import no.nav.su.se.bakover.service.klage.KlageService
import no.nav.su.se.bakover.test.avsluttetKlage
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

internal class AvsluttKlageTest {
    private val sakId = UUID.randomUUID()
    private val klageId = UUID.randomUUID()
    private val uri = "$sakPath/$sakId/klager/$klageId/avslutt"

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
    fun `Kun saksbehandler skal ha tilgang`() {
        listOf(
            listOf(Brukerrolle.Veileder),
            listOf(Brukerrolle.Attestant),
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
                    response.content shouldBe "{\"message\":\"Bruker mangler en av de tillatte rollene: Saksbehandler.\"}"
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
    fun `fant ikke klage`() {
        verifiserFeilkode(
            path = uri,
            feilkode = KunneIkkeAvslutteKlage.FantIkkeKlage,
            status = HttpStatusCode.NotFound,
            body = "{\"message\":\"Fant ikke klage\",\"code\":\"fant_ikke_klage\"}",
        )
    }

    @Test
    fun `ugyldig tilstand`() {
        verifiserFeilkode(
            path = uri,
            feilkode = KunneIkkeAvslutteKlage.UgyldigTilstand(IverksattAvvistKlage::class),
            status = HttpStatusCode.BadRequest,
            body = "{\"message\":\"Kan ikke gå fra tilstanden IverksattAvvistKlage til tilstanden AvsluttetKlage\",\"code\":\"ugyldig_tilstand\"}",
        )
    }

    private fun verifiserFeilkode(
        path: String,
        feilkode: KunneIkkeAvslutteKlage?,
        status: HttpStatusCode,
        body: String,
    ) {
        val klageServiceMock = if (feilkode != null) mock<KlageService> {
            on { avslutt(any(), any(), any()) } doReturn feilkode.left()
        } else mock()
        withTestApplication(
            {
                testSusebakover(
                    services = TestServicesBuilder.services()
                        .copy(klageService = klageServiceMock),
                )
            },
        ) {
            defaultRequest(HttpMethod.Post, path, listOf(Brukerrolle.Saksbehandler)) {
                setBody("""{"begrunnelse":"Begrunnelse av hvorfor vi avsluttet klagen"}""")
            }
        }.apply {
            response.status() shouldBe status
            response.contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
            response.content shouldBe body
        }
    }

    @Test
    fun `kan avslutte klage`() {
        val klage = avsluttetKlage().second
        val klageServiceMock = mock<KlageService> {
            on { avslutt(any(), any(), any()) } doReturn klage.right()
        }
        withTestApplication(
            {
                testSusebakover(
                    services = TestServicesBuilder.services()
                        .copy(klageService = klageServiceMock),
                )
            },
        ) {
            defaultRequest(HttpMethod.Post, uri, listOf(Brukerrolle.Saksbehandler)) {
                setBody("""{"begrunnelse":"Begrunnelse av hvorfor vi avsluttet klagen"}""")
            }
        }.apply {
            response.status() shouldBe HttpStatusCode.OK
            response.contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
            JSONAssert.assertEquals(
                //language=JSON
                """
                {
                  "id":"${klage.id}",
                  "sakid":"${klage.sakId}",
                  "opprettet":"2021-01-01T01:02:03.456789Z",
                  "journalpostId":"klageJournalpostId",
                  "saksbehandler":"saksbehandler",
                  "datoKlageMottatt":"2021-12-01",
                  "status":"VURDERT_BEKREFTET",
                  "vedtakId":"${(klage.forrigeSteg as VurdertKlage.Bekreftet).vilkårsvurderinger.vedtakId}",
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
                  "klagevedtakshistorikk": [],
                  "avsluttet": "ER_AVSLUTTET"
                }
                """.trimIndent(),
                response.content,
                true,
            )
        }
    }
}
