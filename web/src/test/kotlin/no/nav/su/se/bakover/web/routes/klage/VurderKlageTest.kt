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
import no.nav.su.se.bakover.domain.klage.KunneIkkeVurdereKlage
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.service.klage.KlageService
import no.nav.su.se.bakover.test.påbegyntVurdertKlage
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
    private val uri = "$SAK_PATH/$sakId/klager/$klageId/vurderinger"

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
            body = "{\"message\":\"Ugyldig opprettholdelseshjemler\",\"code\":\"ugyldig_opprettholdelseshjemler\"}",
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
            feilkode = KunneIkkeVurdereKlage.UgyldigTilstand(OpprettetKlage::class),
            status = HttpStatusCode.InternalServerError,
            body = "{\"message\":\"Kan ikke gå fra tilstanden OpprettetKlage til tilstanden VurdertKlage\",\"code\":\"ugyldig_tilstand\"}",
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
                bodyAsText() shouldBe body
            }
        }
    }

    @Test
    fun `kan vurdere klage`() {
        val påbegyntVurdertKlage = påbegyntVurdertKlage().second
        val klageServiceMock = mock<KlageService> {
            on { vurder(any()) } doReturn påbegyntVurdertKlage.right()
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
                  "id":"${påbegyntVurdertKlage.id}",
                  "sakid":"${påbegyntVurdertKlage.sakId}",
                  "opprettet":"2021-02-01T01:02:03.456789Z",
                  "journalpostId":"klageJournalpostId",
                  "saksbehandler":"saksbehandler",
                  "datoKlageMottatt":"2021-01-15",
                  "status":"VURDERT_PÅBEGYNT",
                  "vedtakId":"${påbegyntVurdertKlage.vilkårsvurderinger.vedtakId}",
                "innenforFristen": "JA",
                "innenforFristenBegrunnelse": "Innenfor fristen er JA",
                "klagesDetPåKonkreteElementerIVedtaket": true,
                "klagesDetPåKonkreteElementerIVedtaketBegrunnelse": "texkst",
                "erUnderskrevet": "JA",
                "erUnderskrevetBegrunnelse": "underskrevet",
                "fremsattRettsligKlageinteresse": "JA",
                "fremsattRettsligKlageinteresseBegrunnelse": "underskrevet",
                  "fritekstTilBrev":null,
                  "vedtaksvurdering":null,
                  "attesteringer":[],
                  "klagevedtakshistorikk": [],
                  "avsluttet": "KAN_AVSLUTTES",
                  "avsluttetTidspunkt": null,
                  "avsluttetBegrunnelse": null
                }
                    """.trimIndent(),
                    bodyAsText(),
                    true,
                )
            }
        }
    }
}
