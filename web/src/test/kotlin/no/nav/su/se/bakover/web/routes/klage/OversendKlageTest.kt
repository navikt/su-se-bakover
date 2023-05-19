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
import no.nav.su.se.bakover.domain.klage.KunneIkkeLageBrevForKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeOversendeKlage
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.service.klage.KlageService
import no.nav.su.se.bakover.test.oversendtKlage
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

internal class OversendKlageTest {
    //language=JSON
    private val validBody = """
        {
            "journalpostId": "1",
             "datoKlageMottatt": "2021-01-01"
        }
    """.trimIndent()

    private val sakId: UUID = UUID.randomUUID()
    private val klageId: UUID = UUID.randomUUID()
    private val uri = "$sakPath/$sakId/klager/$klageId/oversend"

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
            listOf(Brukerrolle.Saksbehandler),
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
                    bodyAsText() shouldBe "{\"message\":\"Bruker mangler en av de tillatte rollene: [Attestant]\",\"code\":\"mangler_rolle\"}"
                }
            }
        }
    }

    @Test
    fun `fant ikke klage`() {
        verifiserFeilkode(
            feilkode = KunneIkkeOversendeKlage.FantIkkeKlage,
            status = HttpStatusCode.NotFound,
            body = "{\"message\":\"Fant ikke klage\",\"code\":\"fant_ikke_klage\"}",
        )
    }

    @Test
    fun `ugyldig tilstand`() {
        verifiserFeilkode(
            feilkode = KunneIkkeOversendeKlage.UgyldigTilstand(OpprettetKlage::class),
            status = HttpStatusCode.BadRequest,
            body = "{\"message\":\"Kan ikke gå fra tilstanden OpprettetKlage til tilstanden OversendtKlage\",\"code\":\"ugyldig_tilstand\"}",
        )
    }

    @Test
    fun `attestant og saksbehandler kan ikke være samme person`() {
        verifiserFeilkode(
            feilkode = KunneIkkeOversendeKlage.AttestantOgSaksbehandlerKanIkkeVæreSammePerson,
            status = HttpStatusCode.Forbidden,
            body = "{\"message\":\"Attestant og saksbehandler kan ikke være samme person\",\"code\":\"attestant_og_saksbehandler_kan_ikke_være_samme_person\"}",
        )
    }

    @Test
    fun `fant ikke person`() {
        verifiserFeilkode(
            feilkode = KunneIkkeOversendeKlage.KunneIkkeLageBrev(KunneIkkeLageBrevForKlage.FantIkkePerson),
            status = HttpStatusCode.InternalServerError,
            body = "{\"message\":\"Fant ikke person\",\"code\":\"fant_ikke_person\"}",
        )
    }

    @Test
    fun `fant ikke saksbehandler`() {
        verifiserFeilkode(
            feilkode = KunneIkkeOversendeKlage.KunneIkkeLageBrev(KunneIkkeLageBrevForKlage.FantIkkeSaksbehandler),
            status = HttpStatusCode.InternalServerError,
            body = "{\"message\":\"Fant ikke saksbehandler eller attestant\",\"code\":\"fant_ikke_saksbehandler_eller_attestant\"}",
        )
    }

    @Test
    fun `kunne ikke generere PDF`() {
        verifiserFeilkode(
            feilkode = KunneIkkeOversendeKlage.KunneIkkeLageBrev(KunneIkkeLageBrevForKlage.KunneIkkeGenererePDF),
            status = HttpStatusCode.InternalServerError,
            body = "{\"message\":\"Kunne ikke generere brev\",\"code\":\"kunne_ikke_generere_brev\"}",
        )
    }

    @Test
    fun `fant ikke vedtak knyttet til klagen`() {
        verifiserFeilkode(
            feilkode = KunneIkkeOversendeKlage.KunneIkkeLageBrev(KunneIkkeLageBrevForKlage.FantIkkeVedtakKnyttetTilKlagen),
            status = HttpStatusCode.InternalServerError,
            body = "{\"message\":\"Fant ikke vedtak\",\"code\":\"fant_ikke_vedtak\"}",
        )
    }

    @Test
    fun `Fant ikke journalpost-id knyttet til vedtaket`() {
        verifiserFeilkode(
            feilkode = KunneIkkeOversendeKlage.FantIkkeJournalpostIdKnyttetTilVedtaket,
            status = HttpStatusCode.InternalServerError,
            body = "{\"message\":\"Fant ikke journalpost-id knyttet til vedtaket. Utviklingsteamet ønsker og bli informert dersom dette oppstår.\",\"code\":\"fant_ikke_journalpostid_knyttet_til_vedtaket\"}",
        )
    }

    @Test
    fun `Kunne ikke oversende til klageinstans`() {
        verifiserFeilkode(
            feilkode = KunneIkkeOversendeKlage.KunneIkkeOversendeTilKlageinstans,
            status = HttpStatusCode.InternalServerError,
            body = "{\"message\":\"Kunne ikke oversende til klageinstans\",\"code\":\"kunne_ikke_oversende_til_klageinstans\"}",
        )
    }

    private fun verifiserFeilkode(
        feilkode: KunneIkkeOversendeKlage,
        status: HttpStatusCode,
        body: String,
    ) {
        val klageServiceMock = mock<KlageService> {
            on { oversend(any(), any()) } doReturn feilkode.left()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services()
                        .copy(klageService = klageServiceMock),
                )
            }
            defaultRequest(HttpMethod.Post, uri, listOf(Brukerrolle.Attestant)) {
                setBody(validBody)
            }.apply {
                this.status shouldBe status
                this.contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
                bodyAsText() shouldBe body
            }
        }
    }

    @Test
    fun `kan iverksette klage`() {
        val oversendtKlage = oversendtKlage().second
        val klageServiceMock = mock<KlageService> {
            on { oversend(any(), any()) } doReturn oversendtKlage.right()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services()
                        .copy(klageService = klageServiceMock),
                )
            }
            defaultRequest(HttpMethod.Post, uri, listOf(Brukerrolle.Attestant)).apply {
                status shouldBe HttpStatusCode.OK
                this.contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
                JSONAssert.assertEquals(
                    //language=JSON
                    """
                {
                  "id":"${oversendtKlage.id}",
                  "sakid":"${oversendtKlage.sakId}",
                  "opprettet":"2021-02-01T01:02:03.456789Z",
                  "journalpostId":"klageJournalpostId",
                  "saksbehandler":"saksbehandler",
                  "datoKlageMottatt":"2021-01-15",
                  "status":"OVERSENDT",
                  "vedtakId":"${oversendtKlage.vilkårsvurderinger.vedtakId}",
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
                      "underkjennelse":null,
                      "opprettet":"2021-02-01T01:02:03.456789Z"
                    }
                  ],
                  "klagevedtakshistorikk": [],
                  "avsluttet": "KAN_IKKE_AVSLUTTES",
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
