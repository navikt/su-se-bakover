package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.test.avsluttetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

internal class AvsluttRevurderingRouteTest {

    @Test
    fun `Avslutter revurdering`() {
        val avsluttet = avsluttetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second

        val revurderingServiceMock = mock<RevurderingService> {
            on { avsluttRevurdering(anyOrNull(), anyOrNull(), anyOrNull(), any()) } doReturn avsluttet.right()
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services(revurdering = revurderingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "$REVURDERING_PATH/${avsluttet.id}/avslutt",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    //language=JSON
                    """
                        {
                            "begrunnelse": "sender en request for å avslutte revurdering",
                             "fritekst": ""
                        }
                    """.trimIndent(),
                )
            }.apply {
                status shouldBe HttpStatusCode.OK
            }
        }
    }

    @Test
    fun `lager brevutkast for avslutt av revurdering`() {
        val revurderingId = UUID.randomUUID()

        val revurderingServiceMock = mock<RevurderingService> {
            on { lagBrevutkastForAvslutting(any(), anyOrNull(), any()) } doReturn Pair(
                Fnr.generer(),
                PdfA("byteArray".toByteArray()),
            ).right()
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services(revurdering = revurderingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "/saker/$sakId/revurderinger/$revurderingId/brevutkastForAvslutting",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    //language=JSON
                    """
                        {
                             "fritekst": ""
                        }
                    """.trimIndent(),
                )
            }.apply {
                status shouldBe HttpStatusCode.OK
                this.readBytes() shouldBe "byteArray".toByteArray()
                this.contentType() shouldBe ContentType.Application.Pdf
            }
        }
    }
}
