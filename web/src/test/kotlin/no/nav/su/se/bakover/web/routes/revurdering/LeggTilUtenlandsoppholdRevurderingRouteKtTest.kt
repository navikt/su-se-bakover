package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilUtenlandsopphold
import no.nav.su.se.bakover.service.revurdering.RevurderingOgFeilmeldingerResponse
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.requestPath
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

internal class LeggTilUtenlandsoppholdRevurderingRouteKtTest {
    //language=json
    private fun validBody() =
        """
        {
            "utenlandsopphold" : [ 
               {
                    "periode": {
                        "fraOgMed": "2021-01-01", 
                        "tilOgMed": "2021-12-31"
                      },
                    "status": "SkalHoldeSegINorge",
                    "begrunnelse": "begrunnelse"
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
        val opprettetRevurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak().second

        val revurderingServiceMock = mock<RevurderingService> {
            on { leggTilUtenlandsopphold(any()) } doReturn RevurderingOgFeilmeldingerResponse(
                opprettetRevurdering,
                emptyList(),
            ).right()
        }

        withTestApplication(
            {
                testSusebakover(services = TestServicesBuilder.services(revurdering = revurderingServiceMock))
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/${revurderingId()}/utenlandsopphold",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(validBody())
            }.apply {
                response.status() shouldBe HttpStatusCode.OK
                // skal vi sjekke JSON ?
            }
        }
    }

    @Test
    fun `feilmelding hvis vi ikke finner revurdering`() {
        val revurderingServiceMock = mock<RevurderingService> {
            on { leggTilUtenlandsopphold(any()) } doReturn KunneIkkeLeggeTilUtenlandsopphold.FantIkkeBehandling.left()
        }

        withTestApplication(
            {
                testSusebakover(services = TestServicesBuilder.services(revurdering = revurderingServiceMock))
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/${revurderingId()}/utenlandsopphold",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(validBody())
            }.apply {
                response.status() shouldBe HttpStatusCode.NotFound
                response.content shouldContain "fant_ikke_revurdering"
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

        withTestApplication(
            {
                testSusebakover(services = TestServicesBuilder.services(revurdering = revurderingServiceMock))
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/${revurderingId()}/utenlandsopphold",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(validBody())
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "ugyldig_tilstand"
            }
        }
    }

    @Test
    fun `feilmelding hvis vi har ugyldig body`() {
        val revurderingServiceMock = mock<RevurderingService>()

        withTestApplication(
            {
                testSusebakover(services = TestServicesBuilder.services(revurdering = revurderingServiceMock))
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/${revurderingId()}/utenlandsopphold",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(invalidBody())
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
            }
            verifyNoMoreInteractions(revurderingServiceMock)
        }
    }

    @Test
    fun `feilmelding for ugyldig periode`() {
        withTestApplication(
            {
                testSusebakover(services = TestServicesBuilder.services())
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/${revurderingId()}/utenlandsopphold",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    """
                {
                    "utenlandsopphold" : [ 
                       {
                            "periode": {
                                "fraOgMed": "2021-05-01", 
                                "tilOgMed": "2021-01-31"
                              },
                            "status": "SkalHoldeSegINorge",
                            "begrunnelse": "begrunnelse"
                        }
                    ]
                }
                    """.trimIndent()
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.BadRequest
                response.content shouldContain "ugyldig_periode_start_slutt"
            }
        }
    }
}
