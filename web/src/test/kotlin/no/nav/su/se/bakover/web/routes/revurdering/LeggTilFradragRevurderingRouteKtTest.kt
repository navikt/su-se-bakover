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
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilFradragsgrunnlag
import no.nav.su.se.bakover.service.revurdering.RevurderingOgFeilmeldingerResponse
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.sakinfo
import no.nav.su.se.bakover.test.vilkårsvurderingRevurderingIkkeVurdert
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.periode
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.requestPath
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.testServices
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.vedtak
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

internal class LeggTilFradragRevurderingRouteKtTest {
    //language=json
    private val validBody =
        """
        {
            "fradrag":
                [
                    {
                        "periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"},
                        "beløp":9879,
                        "type":"Arbeidsinntekt",
                        "utenlandskInntekt":null,
                        "tilhører":"EPS"
                    },
                    {
                        "periode":{"fraOgMed":"2021-06-01","tilOgMed":"2021-12-31"},
                        "beløp":10000,
                        "type":"Kontantstøtte",
                        "utenlandskInntekt":null,
                        "tilhører":"BRUKER"
                    }
                ]
        }
        """.trimIndent()

    private val revurderingId = UUID.randomUUID()

    @Test
    fun `happy case`() {
        val opprettetRevurdering = OpprettetRevurdering(
            id = UUID.randomUUID(),
            periode = periode,
            opprettet = fixedTidspunkt,
            tilRevurdering = vedtak.id,
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandler"),
            oppgaveId = OppgaveId("oppgaveId"),
            fritekstTilBrev = "",
            revurderingsårsak = Revurderingsårsak(
                Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
            ),
            forhåndsvarsel = null,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = AvkortingVedRevurdering.Uhåndtert.IngenUtestående,
            sakinfo = sakinfo,
        )

        val revurderingServiceMock = mock<RevurderingService> {
            on { leggTilFradragsgrunnlag(any()) } doReturn RevurderingOgFeilmeldingerResponse(
                opprettetRevurdering,
                emptyList(),
            ).right()
        }

        testApplication {
            application {
                testSusebakover(services = testServices.copy(revurdering = revurderingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/$revurderingId/fradrag",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(validBody)
            }.apply {
                status shouldBe HttpStatusCode.OK
                // skal vi sjekke JSON ?
                // kanskje ?
            }
        }
    }

    @Test
    fun `feilmelding hvis vi ikke finner revurdering`() {
        val revurderingServiceMock = mock<RevurderingService> {
            on { leggTilFradragsgrunnlag(any()) } doReturn KunneIkkeLeggeTilFradragsgrunnlag.FantIkkeBehandling.left()
        }

        testApplication {
            application {
                testSusebakover(services = testServices.copy(revurdering = revurderingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/$revurderingId/fradrag",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(validBody)
            }.apply {
                status shouldBe HttpStatusCode.NotFound
                bodyAsText() shouldContain "fant_ikke_revurdering"
            }
        }
    }

    @Test
    fun `feilmelding hvis revurdering har ugyldig status`() {
        val revurderingServiceMock = mock<RevurderingService> {
            on { leggTilFradragsgrunnlag(any()) } doReturn KunneIkkeLeggeTilFradragsgrunnlag.UgyldigTilstand(
                fra = IverksattRevurdering::class,
                til = OpprettetRevurdering::class,
            ).left()
        }

        testApplication {
            application {
                testSusebakover(services = testServices.copy(revurdering = revurderingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/$revurderingId/fradrag",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(validBody)
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                bodyAsText() shouldContain "ugyldig_tilstand"
            }
        }
    }

    @Test
    fun `feilmelding hvis vi mangler periode i et fradrag`() {
        val revurderingServiceMock = mock<RevurderingService>()

        testApplication {
            application {
                testSusebakover(services = testServices.copy(revurdering = revurderingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/$revurderingId/fradrag",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    """
                    {
                        "fradrag":
                            [
                                {
                                    "beløp":9879,
                                    "type":"Arbeidsinntekt",
                                    "utenlandskInntekt":null,
                                    "tilhører":"EPS"
                                },
                                {
                                    "periode":{"fraOgMed":"2021-06-01","tilOgMed":"2021-12-31"},
                                    "beløp":10000,
                                    "type":"Kontantstøtte",
                                    "utenlandskInntekt":null,
                                    "tilhører":"BRUKER"
                                }
                            ]
                    }
                    """.trimIndent(),
                )
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                bodyAsText() shouldContain "Fradrag mangler periode"
            }
            verifyNoMoreInteractions(revurderingServiceMock)
        }
    }
}
