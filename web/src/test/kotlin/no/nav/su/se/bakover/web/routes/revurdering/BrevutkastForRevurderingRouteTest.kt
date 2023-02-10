package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.brev.KunneIkkeLageBrevutkastForRevurdering
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class BrevutkastForRevurderingRouteTest {
    private val validBody = """{ "fritekst": "someFritekst"}"""

    private val revurderingId = UUID.randomUUID()

    @Test
    fun `uautoriserte kan ikke lage brevutkast`() {
        testApplication {
            application { testSusebakover() }
            defaultRequest(
                HttpMethod.Post,
                "/saker/$sakId/revurderinger/$revurderingId/brevutkast",
                listOf(Brukerrolle.Veileder),
            ) {
                setBody(validBody)
            }.apply {
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
            on { lagBrevutkastForRevurdering(any(), any()) } doReturn pdfAsBytes.right()
            on { hentRevurdering(any()) } doReturn revurderingMock
        }

        testApplication {
            application { testSusebakover(services = TestServicesBuilder.services(revurdering = revurderingServiceMock)) }
            defaultRequest(
                HttpMethod.Post,
                "/saker/$sakId/revurderinger/$revurderingId/brevutkast",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(validBody)
            }.apply {
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
            error = KunneIkkeLageBrevutkastForRevurdering.KunneIkkeLageBrevutkast,
            expectedStatusCode = HttpStatusCode.InternalServerError,
            expectedJsonResponse = """
                {
                    "message":"Kunne ikke lage brevutkast",
                    "code":"kunne_ikke_lage_brevutkast"
                }
            """.trimIndent(),

        )
    }

    @Test
    fun `fant ikke person`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeLageBrevutkastForRevurdering.FantIkkePerson,
            expectedStatusCode = HttpStatusCode.NotFound,
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
            error = KunneIkkeLageBrevutkastForRevurdering.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant,
            expectedStatusCode = HttpStatusCode.InternalServerError,
            expectedJsonResponse = """
                {
                    "message":"Kunne ikke hente navn for saksbehandler eller attestant",
                    "code":"navneoppslag_feilet"
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
            on { lagBrevutkastForRevurdering(any(), any()) } doReturn error.left()
            on { hentRevurdering(any()) } doReturn mock<OpprettetRevurdering>()
        }

        testApplication {
            application { testSusebakover(services = TestServicesBuilder.services(revurdering = revurderingServiceMock)) }
            defaultRequest(
                HttpMethod.Post,
                "/saker/$sakId/revurderinger/$revurderingId/brevutkast",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(validBody)
            }.apply {
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
