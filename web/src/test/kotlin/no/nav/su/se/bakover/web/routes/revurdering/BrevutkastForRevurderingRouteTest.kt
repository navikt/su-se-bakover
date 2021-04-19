package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.contentType
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLageBrevutkastForRevurdering
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class BrevutkastForRevurderingRouteTest {
    private val validBody = """{ "fritekst": "someFritekst"}"""

    private val revurderingId = UUID.randomUUID()

    @Test
    fun `uautoriserte kan ikke lage brevutkast`() {
        withTestApplication({
            testSusebakover()
        }) {
            defaultRequest(
                HttpMethod.Post,
                "${RevurderingRoutesTestData.requestPath}/$revurderingId/brevutkast",
                listOf(Brukerrolle.Veileder)
            ) {
                setBody(validBody)
            }.apply {
                response.status() shouldBe HttpStatusCode.Forbidden
                JSONAssert.assertEquals(
                    """
                    {
                        "message":"Bruker mangler en av de tillatte rollene: Saksbehandler."
                    }
                    """.trimIndent(),
                    response.content,
                    true
                )
            }
        }
    }

    @Test
    fun `kan lage brevutkast`() {
        val pdfAsBytes = "<myPreciousByteArray.org".toByteArray()
        val revurderingMock = mock<Revurdering> {
            on { fnr } doReturn mock()
        }
        val revurderingServiceMock = mock<RevurderingService> {
            on { lagBrevutkast(any(), any()) } doReturn pdfAsBytes.right()
            on { hentRevurdering(any()) } doReturn revurderingMock
        }

        withTestApplication({
            testSusebakover(services = RevurderingRoutesTestData.testServices.copy(revurdering = revurderingServiceMock))
        }) {
            defaultRequest(
                HttpMethod.Post,
                "${RevurderingRoutesTestData.requestPath}/$revurderingId/brevutkast",
                listOf(Brukerrolle.Saksbehandler)
            ) {
                setBody(validBody)
            }.apply {
                response.status() shouldBe HttpStatusCode.OK
                response.byteContent shouldBe pdfAsBytes
                response.contentType() shouldBe ContentType.Application.Pdf
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
            """.trimIndent()

        )
    }

    @Test
    fun `kunne ikke lage brevutkast`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeLageBrevutkastForRevurdering.KunneIkkeLageBrevutkast,
            expectedStatusCode = HttpStatusCode.InternalServerError,
            expectedJsonResponse = """
                {
                    "message":"Kunne ikke lage brevutkast",
                    "code":"kunne_ikke_lage_brevutkast"
                }
            """.trimIndent()

        )
    }

    @Test
    fun `fant ikke person`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeLageBrevutkastForRevurdering.FantIkkePerson,
            expectedStatusCode = HttpStatusCode.InternalServerError,
            expectedJsonResponse = """
                {
                    "message":"Fant ikke person",
                    "code":"fant_ikke_person"
                }
            """.trimIndent()

        )
    }

    @Test
    fun `kunne ikke hente navn for saksbehandler eller attestant`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeLageBrevutkastForRevurdering.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant,
            expectedStatusCode = HttpStatusCode.InternalServerError,
            expectedJsonResponse = """
                {
                    "message":"Kunne ikke hente navn for saksbehandler eller attestant",
                    "code":"navneoppslag_feilet"
                }
            """.trimIndent()

        )
    }

    private fun shouldMapErrorCorrectly(
        error: KunneIkkeLageBrevutkastForRevurdering,
        expectedStatusCode: HttpStatusCode,
        expectedJsonResponse: String
    ) {
        val revurderingServiceMock = mock<RevurderingService> {
            on { lagBrevutkast(any(), any()) } doReturn error.left()
            on { hentRevurdering(any()) } doReturn mock()
        }

        withTestApplication({
            testSusebakover(services = RevurderingRoutesTestData.testServices.copy(revurdering = revurderingServiceMock))
        }) {
            defaultRequest(
                HttpMethod.Post,
                "${RevurderingRoutesTestData.requestPath}/$revurderingId/brevutkast",
                listOf(Brukerrolle.Saksbehandler)
            ) {
                setBody(validBody)
            }.apply {
                response.status() shouldBe expectedStatusCode
                JSONAssert.assertEquals(
                    expectedJsonResponse,
                    response.content,
                    true
                )
            }
        }
    }
}
