package no.nav.su.se.bakover.web.routes.klage

import arrow.core.left
import arrow.core.right
import behandling.klage.domain.VurderingerTilKlage
import behandling.klage.domain.VurderingerTilKlage.Vedtaksvurdering.Årsak
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.shouldBe
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.klage.KunneIkkeBekrefteKlagesteg
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.domain.klage.OversendtKlage
import no.nav.su.se.bakover.service.klage.KlageService
import no.nav.su.se.bakover.test.bekreftetVurdertKlage
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

internal class BekreftVurderingerForKlageTest {

    private val sakId: UUID = UUID.randomUUID()
    private val klageId: UUID = UUID.randomUUID()
    private val uri = "$SAK_PATH/$sakId/klager/$klageId/vurderinger/bekreft"

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
            feilkode = KunneIkkeBekrefteKlagesteg.FantIkkeKlage,
            status = HttpStatusCode.NotFound,
            body = "{\"message\":\"Fant ikke klage\",\"code\":\"fant_ikke_klage\"}",
        )
    }

    @Test
    fun `ugyldig tilstand`() {
        verifiserFeilkode(
            feilkode = KunneIkkeBekrefteKlagesteg.UgyldigTilstand(OpprettetKlage::class, OversendtKlage::class),
            status = HttpStatusCode.BadRequest,
            body = "{\"message\":\"Kan ikke gå fra tilstanden OpprettetKlage til tilstanden OversendtKlage\",\"code\":\"ugyldig_tilstand\"}",
        )
    }

    private fun verifiserFeilkode(
        feilkode: KunneIkkeBekrefteKlagesteg,
        status: HttpStatusCode,
        body: String,
    ) {
        val klageServiceMock = mock<KlageService> {
            on { bekreftVurderinger(any(), any()) } doReturn feilkode.left()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services()
                        .copy(klageService = klageServiceMock),
                )
            }
            defaultRequest(HttpMethod.Post, uri, listOf(Brukerrolle.Saksbehandler)).apply {
                status shouldBe status
                this.contentType() shouldBe ContentType.parse("application/json")
                bodyAsText() shouldBe body
            }
        }
    }

    @Test
    fun `kan bekrefte utfylt vurdert klage`() {
        val klage = bekreftetVurdertKlage().second
        val klageServiceMock = mock<KlageService> {
            on { bekreftVurderinger(any(), any()) } doReturn klage.right()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services()
                        .copy(klageService = klageServiceMock),
                )
            }
            defaultRequest(HttpMethod.Post, uri, listOf(Brukerrolle.Saksbehandler)).apply {
                status shouldBe HttpStatusCode.OK
                this.contentType() shouldBe ContentType.parse("application/json")
                JSONAssert.assertEquals(
                    //language=JSON
                    serialize(klage.toJson()),
                    this.bodyAsText(),
                    true,
                )
            }
        }
    }

    @Test
    fun `kan bekrefte utfylt vurdert klage delvis omgjøring i vedtaksenhet`() {
        val klage = bekreftetVurdertKlage(vedtaksvurdering = VurderingerTilKlage.Vedtaksvurdering.createOmgjør(årsak = Årsak.FEIL_LOVANVENDELSE, begrunnelse = "test", erDelvisOmgjøring = true)).second
        val klageServiceMock = mock<KlageService> {
            on { bekreftVurderinger(any(), any()) } doReturn klage.right()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services()
                        .copy(klageService = klageServiceMock),
                )
            }
            defaultRequest(HttpMethod.Post, uri, listOf(Brukerrolle.Saksbehandler)).apply {
                status shouldBe HttpStatusCode.OK
                this.contentType() shouldBe ContentType.parse("application/json")
                val delvisOmgjøringVedtaksenhet = this.bodyAsText()
                JSONAssert.assertEquals(
                    //language=JSON
                    serialize(klage.toJson()),
                    delvisOmgjøringVedtaksenhet,
                    true,
                )

                val jsonNode = ObjectMapper().readTree(delvisOmgjøringVedtaksenhet)
                val vedtakType = jsonNode["vedtaksvurdering"]["type"].asText()
                vedtakType shouldBe KlageJson.VedtaksvurderingJson.Type.DELVIS_OMGJØRING_EGEN_VEDTAKSINSTANS.toString()
            }
        }
    }

    @Test
    fun `kan bekrefte utfylt vurdert klage omgjøring i vedtaksenhet`() {
        val klage = bekreftetVurdertKlage(vedtaksvurdering = VurderingerTilKlage.Vedtaksvurdering.createOmgjør(årsak = Årsak.FEIL_LOVANVENDELSE, begrunnelse = "test", erDelvisOmgjøring = false)).second
        val klageServiceMock = mock<KlageService> {
            on { bekreftVurderinger(any(), any()) } doReturn klage.right()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services()
                        .copy(klageService = klageServiceMock),
                )
            }
            defaultRequest(HttpMethod.Post, uri, listOf(Brukerrolle.Saksbehandler)).apply {
                status shouldBe HttpStatusCode.OK
                this.contentType() shouldBe ContentType.parse("application/json")
                val omgjøringVedtaksenhet = this.bodyAsText()
                JSONAssert.assertEquals(
                    //language=JSON
                    serialize(klage.toJson()),
                    omgjøringVedtaksenhet,
                    true,
                )
                val jsonNode = ObjectMapper().readTree(omgjøringVedtaksenhet)
                val vedtakType = jsonNode["vedtaksvurdering"]["type"].asText()
                vedtakType shouldBe KlageJson.VedtaksvurderingJson.Type.OMGJØR.toString()
            }
        }
    }
}
