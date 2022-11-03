package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.right
import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrev
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.sakId
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
        testApplication {
            application {
                testSusebakover(
                    services = TestServicesBuilder.services(
                        revurdering = mock {
                            on { oppdaterTilbakekrevingsbehandling(any()) } doReturn simulertRevurdering().let { (sak, revurdering) ->
                                revurdering.oppdaterTilbakekrevingsbehandling(
                                    tilbakekrevingsbehandling = Tilbakekrev(
                                        id = UUID.randomUUID(),
                                        opprettet = fixedTidspunkt,
                                        sakId = sak.id,
                                        revurderingId = revurdering.id,
                                        periode = revurdering.periode,
                                    ),
                                )
                            }.right()
                        },
                    ),
                )
            }
            defaultRequest(
                HttpMethod.Post,
                "/saker/$sakId/revurderinger/$revurderingId/tilbakekreving",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    """
                    {
                        "avgjørelse":"TILBAKEKREV"
                    }
                    """.trimIndent(),
                )
            }.apply {
                status shouldBe HttpStatusCode.OK
            }
        }
    }

    @Test
    fun `sjekker tilgang`() {
        (Brukerrolle.values().toList() - Brukerrolle.Saksbehandler).forEach {
            testApplication {
                application {
                    testSusebakover()
                }
                defaultRequest(
                    HttpMethod.Post,
                    "/saker/$sakId/revurderinger/$revurderingId/tilbakekreving",
                    listOf(it),
                ) {
                    setBody(
                        """
                        {
                            "avgjørelse":"IKKE_TILBAKEKREV"
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    status shouldBe HttpStatusCode.Forbidden
                }
            }
        }
    }

    @Test
    fun `ugyldig input`() {
        testApplication {
            application {
                testSusebakover()
            }
            defaultRequest(
                HttpMethod.Post,
                "/saker/$sakId/revurderinger/$revurderingId/tilbakekreving",
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
                status shouldBe HttpStatusCode.BadRequest
            }
        }
    }
}
