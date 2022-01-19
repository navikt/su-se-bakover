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
import no.nav.su.se.bakover.domain.klage.KunneIkkeLageBrevForKlage
import no.nav.su.se.bakover.service.klage.KlageService
import no.nav.su.se.bakover.service.klage.KunneIkkeLageBrevutkast
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

internal class ForhåndsvisBrevForKlageTest {
    //language=JSON
    private val validBody = """
        {
            "fritekst": "1"
        }
    """.trimIndent()

    private val sakId: UUID = UUID.randomUUID()
    private val klageId: UUID = UUID.randomUUID()
    private val uri = "$sakPath/$sakId/klager/$klageId/brevutkast"

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
    fun `Kun saksbehandler og attestant skal ha tilgang`() {
        listOf(
            listOf(Brukerrolle.Veileder),
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
                    response.content shouldBe "{\"message\":\"Bruker mangler en av de tillatte rollene: Saksbehandler,Attestant.\"}"
                }
            }
        }
    }

    @Test
    fun `fant ikke klage`() {
        verifiserFeilkode(
            feilkode = KunneIkkeLageBrevutkast.FantIkkeKlage,
            status = HttpStatusCode.NotFound,
            body = "{\"message\":\"Fant ikke klage\",\"code\":\"fant_ikke_klage\"}",
        )
    }

    @Test
    fun `fant ikke person`() {
        verifiserFeilkode(
            feilkode = KunneIkkeLageBrevutkast.GenereringAvBrevFeilet(KunneIkkeLageBrevForKlage.FantIkkePerson),
            status = HttpStatusCode.InternalServerError,
            body = "{\"message\":\"Fant ikke person\",\"code\":\"fant_ikke_person\"}",
        )
    }

    @Test
    fun `fant ikke saksbehandler og eller attestant`() {
        verifiserFeilkode(
            feilkode = KunneIkkeLageBrevutkast.GenereringAvBrevFeilet(KunneIkkeLageBrevForKlage.FantIkkeSaksbehandler),
            status = HttpStatusCode.InternalServerError,
            body = "{\"message\":\"Fant ikke saksbehandler eller attestant\",\"code\":\"fant_ikke_saksbehandler_eller_attestant\"}",
        )
    }

    @Test
    fun `kunne ikke generere PDF`() {
        verifiserFeilkode(
            feilkode = KunneIkkeLageBrevutkast.GenereringAvBrevFeilet(KunneIkkeLageBrevForKlage.KunneIkkeGenererePDF),
            status = HttpStatusCode.InternalServerError,
            body = "{\"message\":\"Kunne ikke generere brev\",\"code\":\"kunne_ikke_generere_brev\"}",
        )
    }

    @Test
    fun `fant ikke vedtak knyttet til klagen`() {
        verifiserFeilkode(
            feilkode = KunneIkkeLageBrevutkast.GenereringAvBrevFeilet(KunneIkkeLageBrevForKlage.FantIkkeVedtakKnyttetTilKlagen),
            status = HttpStatusCode.InternalServerError,
            body = "{\"message\":\"Fant ikke vedtak\",\"code\":\"fant_ikke_vedtak\"}",
        )
    }

    private fun verifiserFeilkode(
        feilkode: KunneIkkeLageBrevutkast,
        status: HttpStatusCode,
        body: String,
    ) {
        val klageServiceMock = mock<KlageService> {
            on { brevutkast(any(), any()) } doReturn feilkode.left()
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
    fun `kan forhåndsvise brev`() {
        val pdfAsBytes = "<myPreciousByteArray.org".toByteArray()
        val klageServiceMock = mock<KlageService> {
            on { brevutkast(any(), any()) } doReturn pdfAsBytes.right()
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
            response.byteContent shouldBe pdfAsBytes
            response.contentType() shouldBe ContentType.Application.Pdf
        }
    }
}
