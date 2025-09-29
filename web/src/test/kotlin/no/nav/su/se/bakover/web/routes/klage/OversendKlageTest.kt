package no.nav.su.se.bakover.web.routes.klage

import arrow.core.left
import arrow.core.right
import dokument.domain.KunneIkkeLageDokument
import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.domain.klage.KunneIkkeLageBrevKommandoForKlage
import no.nav.su.se.bakover.domain.klage.KunneIkkeOversendeKlage
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import no.nav.su.se.bakover.service.klage.KlageService
import no.nav.su.se.bakover.test.oversendtKlage
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
    private val uri = "$SAK_PATH/$sakId/klager/$klageId/oversend"

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
    fun `finner ikke personinformasjon`() {
        verifiserFeilkode(
            feilkode = KunneIkkeOversendeKlage.KunneIkkeLageDokument(
                KunneIkkeLageDokument.FeilVedHentingAvInformasjon,
            ),
            status = HttpStatusCode.InternalServerError,
            body = "{\"message\":\"Feil ved henting av personinformasjon\",\"code\":\"feil_ved_henting_av_personInformasjon\"}",
        )
    }

    @Test
    fun `kunne ikke generere PDF`() {
        verifiserFeilkode(
            feilkode = KunneIkkeOversendeKlage.KunneIkkeLageDokument(
                KunneIkkeLageDokument.FeilVedGenereringAvPdf,
            ),
            status = HttpStatusCode.InternalServerError,
            body = "{\"message\":\"Feil ved generering av dokument\",\"code\":\"feil_ved_generering_av_dokument\"}",
        )
    }

    @Test
    fun `fant ikke vedtak knyttet til klagen`() {
        verifiserFeilkode(
            feilkode = KunneIkkeOversendeKlage.KunneIkkeLageBrevRequest(
                KunneIkkeLageBrevKommandoForKlage.FeilVedHentingAvVedtaksbrevDato,
            ),
            status = HttpStatusCode.InternalServerError,
            body = "{\"message\":\"Feil ved henting av vedtak dato\",\"code\":\"feil_ved_henting_av_vedtak_dato\"}",
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
            on { oversend(any(), any(), any()) } doReturn feilkode.left()
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
                this.contentType() shouldBe ContentType.parse("application/json")
                bodyAsText() shouldBe body
            }
        }
    }

    @Test
    fun `kan iverksette klage`() {
        val oversendtKlage = oversendtKlage().second
        val klageServiceMock = mock<KlageService> {
            on { oversend(any(), any(), any()) } doReturn oversendtKlage.right()
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
                this.contentType() shouldBe ContentType.parse("application/json")
                JSONAssert.assertEquals(
                    //language=JSON
                    """
                {
                  "id":"${oversendtKlage.id}",
                  "sakid":"${oversendtKlage.sakId}",
                  "opprettet":"2021-02-01T01:02:04.456789Z",
                  "journalpostId":"klageJournalpostId",
                  "saksbehandler":"saksbehandler",
                  "datoKlageMottatt":"2021-01-15",
                  "status":"OVERSENDT",
                  "begrunnelse":"",
                  "vedtakId":"${oversendtKlage.vilkårsvurderinger.vedtakId}",
                  "innenforFristen":"JA",
                  "klagesDetPåKonkreteElementerIVedtaket":true,
                  "erUnderskrevet":"JA",
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
                      "opprettet":"2021-02-01T01:02:04.456789Z"
                    }
                  ],
                  "klagevedtakshistorikk": [],
                  "avsluttet": "KAN_IKKE_AVSLUTTES",
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
