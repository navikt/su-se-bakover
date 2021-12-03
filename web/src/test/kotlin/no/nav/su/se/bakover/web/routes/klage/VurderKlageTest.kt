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
import no.nav.su.se.bakover.domain.klage.IverksattKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeVurdereKlage
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.service.klage.KlageService
import no.nav.su.se.bakover.test.påbegyntVurdertKlage
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

internal class VurderKlageTest {
    //language=JSON
    private val validBody = """
        {
            "journalpostId": "1",
             "datoKlageMottatt": "2021-01-01"
        }
    """.trimIndent()

    private val sakId: UUID = UUID.randomUUID()
    private val klageId: UUID = UUID.randomUUID()
    private val uri = "$sakPath/$sakId/klager/$klageId/vurderinger"

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
    fun `fant ikke klage`() {
        verifiserFeilkode(
            feilkode = KunneIkkeVurdereKlage.FantIkkeKlage,
            status = HttpStatusCode.NotFound,
            body = "{\"message\":\"Fant ikke klage\",\"code\":\"fant_ikke_klage\"}",
        )
    }

    @Test
    fun `ugyldig omgjøringsårsak`() {
        verifiserFeilkode(
            feilkode = KunneIkkeVurdereKlage.UgyldigOmgjøringsårsak,
            status = HttpStatusCode.BadRequest,
            body = "{\"message\":\"Ugyldig omgjøringsårsak\",\"code\":\"ugyldig_omgjøringsårsak\"}",
        )
    }

    @Test
    fun `ugyldig omgjøringsutfall`() {
        verifiserFeilkode(
            feilkode = KunneIkkeVurdereKlage.UgyldigOmgjøringsutfall,
            status = HttpStatusCode.BadRequest,
            body = "{\"message\":\"Ugyldig omgjøringsutfall\",\"code\":\"ugyldig_omgjøringsutfall\"}",
        )
    }

    @Test
    fun `ugyldig opprettholdelseshjemler`() {
        verifiserFeilkode(
            feilkode = KunneIkkeVurdereKlage.UgyldigOpprettholdelseshjemler,
            status = HttpStatusCode.BadRequest,
            body = "{\"message\":\"Ugyldig opprettholdelseshjemler\",\"code\":\"ugyldig_opprettholdesleshjemler\"}",
        )
    }

    @Test
    fun `kan ikke velge både omgjør og oppretthold`() {
        verifiserFeilkode(
            feilkode = KunneIkkeVurdereKlage.KanIkkeVelgeBådeOmgjørOgOppretthold,
            status = HttpStatusCode.BadRequest,
            body = "{\"message\":\"Kan ikke velge både omgjør og oppretthold.\",\"code\":\"kan_ikke_velge_både_omgjør_og_oppretthold\"}",
        )
    }

    @Test
    fun `ugyldig tilstand`() {
        verifiserFeilkode(
            feilkode = KunneIkkeVurdereKlage.UgyldigTilstand(OpprettetKlage::class, IverksattKlage::class),
            status = HttpStatusCode.BadRequest,
            body = "{\"message\":\"Kan ikke gå fra tilstanden OpprettetKlage til tilstanden IverksattKlage\",\"code\":\"ugyldig_tilstand\"}",
        )
    }

    private fun verifiserFeilkode(
        feilkode: KunneIkkeVurdereKlage,
        status: HttpStatusCode,
        body: String,
    ) {
        val klageServiceMock = mock<KlageService> {
            on { vurder(any()) } doReturn feilkode.left()
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
                setBody(validBody)
            }
        }.apply {
            response.status() shouldBe status
            response.contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
            response.content shouldBe body
        }
    }

    @Test
    fun `kan vurdere klage`() {
        val påbegyntVurdertKlage = påbegyntVurdertKlage()
        val klageServiceMock = mock<KlageService> {
            on { vurder(any()) } doReturn påbegyntVurdertKlage.right()
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
                setBody(validBody)
            }
        }.apply {
            response.status() shouldBe HttpStatusCode.OK
            response.contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
            JSONAssert.assertEquals(
                //language=JSON
                """
                {
                  "id":"${påbegyntVurdertKlage.id}",
                  "sakid":"${påbegyntVurdertKlage.sakId}",
                  "opprettet":"2021-01-01T01:02:03.456789Z",
                  "journalpostId":"klageJournalpostId",
                  "saksbehandler":"saksbehandler",
                  "datoKlageMottatt":"2021-12-01",
                  "status":"VURDERT_PÅBEGYNT",
                  "vedtakId":"${påbegyntVurdertKlage.vilkårsvurderinger.vedtakId}",
                  "innenforFristen":"JA",
                  "klagesDetPåKonkreteElementerIVedtaket":true,
                  "erUnderskrevet":"JA",
                  "begrunnelse":"begrunnelse",
                  "fritekstTilBrev":null,
                  "vedtaksvurdering":null,
                  "attesteringer":[]
                }
                """.trimIndent(),
                response.content,
                true,
            )
        }
    }
}
