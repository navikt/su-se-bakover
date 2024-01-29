package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingOgFeilmeldingerResponse
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.domain.revurdering.vilkår.utenlandsopphold.KunneIkkeLeggeTilUtenlandsopphold
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

internal class LeggTilUtenlandsoppholdVilkårsvurderingerRevurderingRouteKtTest {
    //language=json
    private fun validBody() =
        """
        {
            "vurderinger" : [
               {
                    "periode": {
                        "fraOgMed": "2021-01-01",
                        "tilOgMed": "2021-12-31"
                      },
                    "status": "SkalHoldeSegINorge"
                }
            ]
        }
        """.trimIndent()

    private fun invalidBody() =
        """
        {
            "status": "nkdsfdsfkn",
            "begrunnelse": "begrunnelse"
        }
        """.trimIndent()

    private fun revurderingId() = UUID.randomUUID()

    @Test
    fun `happy case`() {
        val opprettetRevurdering = opprettetRevurdering().second

        val revurderingServiceMock = mock<RevurderingService> {
            on { leggTilUtenlandsopphold(any()) } doReturn RevurderingOgFeilmeldingerResponse(
                opprettetRevurdering,
                emptyList(),
            ).right()
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services(revurdering = revurderingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "/saker/$sakId/revurderinger/${revurderingId()}/utenlandsopphold",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(validBody())
            }.apply {
                status shouldBe HttpStatusCode.OK
                // skal vi sjekke JSON ?
                // kanskje?
            }
        }
    }

    @Test
    fun `feilmelding hvis vi ikke finner revurdering`() {
        val revurderingServiceMock = mock<RevurderingService> {
            on { leggTilUtenlandsopphold(any()) } doReturn KunneIkkeLeggeTilUtenlandsopphold.FantIkkeBehandling.left()
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services(revurdering = revurderingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "/saker/$sakId/revurderinger/${revurderingId()}/utenlandsopphold",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(validBody())
            }.apply {
                status shouldBe HttpStatusCode.NotFound
                bodyAsText() shouldContain "fant_ikke_behandling"
            }
        }
    }

    @Test
    fun `feilmelding hvis revurdering har ugyldig status`() {
        val revurderingServiceMock = mock<RevurderingService> {
            on { leggTilUtenlandsopphold(any()) } doReturn KunneIkkeLeggeTilUtenlandsopphold.UgyldigTilstand(
                fra = IverksattRevurdering::class,
                til = OpprettetRevurdering::class,
            ).left()
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services(revurdering = revurderingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "/saker/$sakId/revurderinger/${revurderingId()}/utenlandsopphold",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(validBody())
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                bodyAsText() shouldContain "ugyldig_tilstand"
            }
        }
    }

    @Test
    fun `feilmelding hvis vi har ugyldig body`() {
        val revurderingServiceMock = mock<RevurderingService>()

        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services(revurdering = revurderingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "/saker/$sakId/revurderinger/${revurderingId()}/utenlandsopphold",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(invalidBody())
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
            }
            verifyNoMoreInteractions(revurderingServiceMock)
        }
    }

    @Test
    fun `feilmelding for ugyldig periode`() {
        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services())
            }
            defaultRequest(
                HttpMethod.Post,
                "/saker/$sakId/revurderinger/${revurderingId()}/utenlandsopphold",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    """
                {
                    "vurderinger" : [
                       {
                            "periode": {
                                "fraOgMed": "2021-05-01",
                                "tilOgMed": "2021-01-31"
                              },
                            "status": "SkalHoldeSegINorge"
                        }
                    ]
                }
                    """.trimIndent(),
                )
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                bodyAsText() shouldContain "ugyldig_periode_start_slutt"
            }
        }
    }
}
