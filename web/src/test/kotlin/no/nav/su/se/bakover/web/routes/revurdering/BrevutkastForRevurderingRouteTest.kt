package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.domain.brev.jsonRequest.FeilVedHentingAvInformasjon
import no.nav.su.se.bakover.domain.dokument.KunneIkkeLageDokument
import no.nav.su.se.bakover.domain.person.KunneIkkeHenteNavnForNavIdent
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.brev.KunneIkkeLageBrevutkastForRevurdering
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class BrevutkastForRevurderingRouteTest {

    private val revurderingId = UUID.randomUUID()

    @Test
    fun `uautoriserte kan ikke lage brevutkast`() {
        testApplication {
            application { testSusebakoverWithMockedDb() }
            defaultRequest(
                HttpMethod.Get,
                "/saker/$sakId/revurderinger/$revurderingId/brevutkast",
                listOf(Brukerrolle.Veileder),
            ).apply {
                status shouldBe HttpStatusCode.Forbidden
                JSONAssert.assertEquals(
                    """
                    {
                        "message":"Bruker mangler en av de tillatte rollene: [Saksbehandler]",
                        "code":"mangler_rolle"
                    }
                    """.trimIndent(),
                    bodyAsText(),
                    true,
                )
            }
        }
    }

    @Test
    fun `kan lage brevutkast`() {
        val pdfAsBytes = "<myPreciousByteArray.org".toByteArray()
        val revurderingMock = mock<IverksattRevurdering.Innvilget> {
            on { fnr } doReturn mock()
        }
        val revurderingServiceMock = mock<RevurderingService> {
            on { lagBrevutkastForRevurdering(any()) } doReturn PdfA(pdfAsBytes).right()
            on { hentRevurdering(any()) } doReturn revurderingMock
        }

        testApplication {
            application { testSusebakoverWithMockedDb(services = TestServicesBuilder.services(revurdering = revurderingServiceMock)) }
            defaultRequest(
                HttpMethod.Get,
                "/saker/$sakId/revurderinger/$revurderingId/brevutkast",
                listOf(Brukerrolle.Saksbehandler),
            ).apply {
                status shouldBe HttpStatusCode.OK
                this.readBytes() shouldBe pdfAsBytes
                this.contentType() shouldBe ContentType.Application.Pdf
            }
        }
    }

    @Test
    fun `fant ikke revurdering`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeLageBrevutkastForRevurdering.FantIkkeRevurdering,
            expectedStatusCode = HttpStatusCode.NotFound,
            expectedJsonResponse = """
                {
                    "message":"Fant ikke revurdering",
                    "code":"fant_ikke_revurdering"
                }
            """.trimIndent(),

        )
    }

    @Test
    fun `kunne ikke lage brevutkast`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeLageBrevutkastForRevurdering.KunneIkkeGenererePdf(
                KunneIkkeLageDokument.FeilVedGenereringAvPdf,
            ),
            expectedStatusCode = HttpStatusCode.InternalServerError,
            expectedJsonResponse = """
                {
                    "message":"Feil ved generering av dokument",
                    "code":"feil_ved_generering_av_dokument"
                }
            """.trimIndent(),

        )
    }

    @Test
    fun `fant ikke person`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeLageBrevutkastForRevurdering.KunneIkkeGenererePdf(
                KunneIkkeLageDokument.FeilVedHentingAvInformasjon(
                    FeilVedHentingAvInformasjon.KunneIkkeHentePerson(
                        KunneIkkeHentePerson.FantIkkePerson,
                    ),
                ),
            ),
            expectedStatusCode = HttpStatusCode.InternalServerError,
            expectedJsonResponse = """
                {
                    "message":"Fant ikke person",
                    "code":"fant_ikke_person"
                }
            """.trimIndent(),

        )
    }

    @Test
    fun `kunne ikke hente navn for saksbehandler eller attestant`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeLageBrevutkastForRevurdering.KunneIkkeGenererePdf(
                KunneIkkeLageDokument.FeilVedHentingAvInformasjon(
                    FeilVedHentingAvInformasjon.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant(
                        KunneIkkeHenteNavnForNavIdent.FantIkkeBrukerForNavIdent,
                    ),
                ),
            ),
            expectedStatusCode = HttpStatusCode.InternalServerError,
            expectedJsonResponse = """
                {
                    "message":"Fant ikke saksbehandler eller attestant",
                    "code":"fant_ikke_saksbehandler_eller_attestant"
                }
            """.trimIndent(),

        )
    }

    private fun shouldMapErrorCorrectly(
        error: KunneIkkeLageBrevutkastForRevurdering,
        expectedStatusCode: HttpStatusCode,
        expectedJsonResponse: String,
    ) {
        val revurderingServiceMock = mock<RevurderingService> {
            on { lagBrevutkastForRevurdering(any()) } doReturn error.left()
            on { hentRevurdering(any()) } doReturn mock<OpprettetRevurdering>()
        }

        testApplication {
            application { testSusebakoverWithMockedDb(services = TestServicesBuilder.services(revurdering = revurderingServiceMock)) }
            defaultRequest(
                HttpMethod.Get,
                "/saker/$sakId/revurderinger/$revurderingId/brevutkast",
                listOf(Brukerrolle.Saksbehandler),
            ).apply {
                status shouldBe expectedStatusCode
                JSONAssert.assertEquals(
                    expectedJsonResponse,
                    bodyAsText(),
                    true,
                )
            }
        }
    }
}
