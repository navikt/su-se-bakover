package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsbehandling
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.simulertRevurdering
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

internal class OppdaterTilbakekrevingsbehandlingRouteKtTest {

    @Test
    fun `oppdaterer tilbakekrevingsbehandling`() {
        withTestApplication(
            {
                testSusebakover(
                    services = TestServicesBuilder.services(
                        revurdering = mock {
                            on { oppdaterTilbakekrevingsbehandling(any()) } doReturn simulertRevurdering().let { (sak, revurdering) ->
                                revurdering.oppdaterTilbakekrevingsbehandling(
                                    tilbakekrevingsbehandling = Tilbakekrevingsbehandling.VurderTilbakekreving.Avgjort.Forsto(
                                        id = UUID.randomUUID(),
                                        opprettet = fixedTidspunkt,
                                        sakId = sak.id,
                                        revurderingId = revurdering.id,
                                        periode = revurdering.periode,
                                        oversendtTidspunkt = null,
                                    ),
                                )
                            }.right()
                        },
                    ),
                )
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "${RevurderingRoutesTestData.requestPath}/$revurderingId/tilbakekreving",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    """
                    {
                        "avgjørelse":"FORSTO"
                    }
                    """.trimIndent(),
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.OK
            }
        }
    }

    @Test
    fun `sjekker tilgang`() {
        (Brukerrolle.values().toList() - Brukerrolle.Saksbehandler).forEach {
            withTestApplication(
                {
                    testSusebakover()
                },
            ) {
                defaultRequest(
                    HttpMethod.Post,
                    "${RevurderingRoutesTestData.requestPath}/$revurderingId/tilbakekreving",
                    listOf(it),
                ) {
                    setBody(
                        """
                        {
                            "avgjørelse":"FORSTO"
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    response.status() shouldBe HttpStatusCode.Forbidden
                }
            }
        }
    }

    @Test
    fun `ugyldig input`() {
        withTestApplication(
            {
                testSusebakover()
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "${RevurderingRoutesTestData.requestPath}/$revurderingId/tilbakekreving",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    """
                        {
                            "baluba":"tjohe"
                        }
                    """.trimIndent(),
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
            }
        }
    }
}
