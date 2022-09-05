package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.shouldBe
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeBeregneOgSimulereRevurdering
import no.nav.su.se.bakover.service.revurdering.RevurderingOgFeilmeldingerResponse
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nyUtbetalingSimulert
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.vilkårsvurderinger.innvilgetUførevilkår
import no.nav.su.se.bakover.web.argThat
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.periode
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.requestPath
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.testServices
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class BeregnOgSimulerRevurderingRouteKtTest {
    private val validBody = """
        {
            "periode": { "fraOgMed": "${periode.fraOgMed}", "tilOgMed": "${periode.tilOgMed}"},
            "fradrag": []
        }
    """.trimIndent()

    private val revurderingId = UUID.randomUUID()

    @Test
    fun `uautoriserte kan ikke beregne og simulere revurdering`() {
        testApplication {
            application {
                testSusebakover()
            }
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/$revurderingId/beregnOgSimuler",
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
    fun `kan opprette beregning og simulering for revurdering`() {
        val (sak, beregnetRevurdering) = opprettetRevurdering(
            vilkårOverrides = listOf(
                innvilgetUførevilkår(
                    forventetInntekt = 12000,
                )
            ),
        ).let { (sak, revurdering) ->
            sak to revurdering.beregn(
                eksisterendeUtbetalinger = sak.utbetalinger,
                clock = fixedClock,
                gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                    fraOgMed = revurdering.periode.fraOgMed,
                    clock = fixedClock,
                ).getOrFail(),
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail()
        }

        val simulertRevurdering = when (beregnetRevurdering) {
            is BeregnetRevurdering.Innvilget -> {
                beregnetRevurdering.simuler(
                    saksbehandler = saksbehandler,
                    clock = fixedClock
                ) {
                    nyUtbetalingSimulert(
                        sakOgBehandling = sak to beregnetRevurdering,
                        beregning = it.beregning,
                        clock = fixedClock
                    ).right()
                }.getOrFail()
            }
            is BeregnetRevurdering.IngenEndring -> throw RuntimeException("Revurderingen må ha en endring på minst 10 prosent")
            is BeregnetRevurdering.Opphørt -> throw RuntimeException("Beregningen har 0 kroners utbetalinger")
        }

        val revurderingServiceMock = mock<RevurderingService> {
            on {
                beregnOgSimuler(
                    any(),
                    any(),
                )
            } doReturn RevurderingOgFeilmeldingerResponse(simulertRevurdering).right()
        }

        testApplication {
            application {
                testSusebakover(services = testServices.copy(revurdering = revurderingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/${simulertRevurdering.id}/beregnOgSimuler",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(validBody)
            }.apply {
                status shouldBe HttpStatusCode.Created
                val actualResponse = objectMapper.readValue<Map<String, Any>>(bodyAsText())
                val revurdering =
                    objectMapper.readValue<SimulertRevurderingJson>(objectMapper.writeValueAsString(actualResponse["revurdering"]))
                verify(revurderingServiceMock).beregnOgSimuler(
                    argThat { it shouldBe simulertRevurdering.id },
                    argThat { it shouldBe NavIdentBruker.Saksbehandler("Z990Lokal") },
                )
                verifyNoMoreInteractions(revurderingServiceMock)
                revurdering.id shouldBe simulertRevurdering.id.toString()
                revurdering.status shouldBe RevurderingsStatus.SIMULERT_INNVILGET
            }
        }
    }

    @Test
    fun `fant ikke revurdering`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeBeregneOgSimulereRevurdering.FantIkkeRevurdering,
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
    fun `ugyldig tilstand`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeBeregneOgSimulereRevurdering.UgyldigTilstand(
                fra = IverksattRevurdering::class,
                til = OpprettetRevurdering::class,
            ),
            expectedStatusCode = HttpStatusCode.BadRequest,
            expectedJsonResponse = """
                {
                    "message":"Kan ikke gå fra tilstanden IverksattRevurdering til tilstanden OpprettetRevurdering",
                    "code":"ugyldig_tilstand"
                }
            """.trimIndent(),

        )
    }

    @Test
    fun `kan ikke velge siste måned ved nedgang i stønaden`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeBeregneOgSimulereRevurdering.KanIkkeVelgeSisteMånedVedNedgangIStønaden,
            expectedStatusCode = HttpStatusCode.BadRequest,
            expectedJsonResponse = """
                {
                    "message":"Kan ikke velge siste måned av stønadsperioden ved nedgang i stønaden",
                    "code":"siste_måned_ved_nedgang_i_stønaden"
                }
            """.trimIndent(),

        )
    }

    @Test
    fun `simulering feilet`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeBeregneOgSimulereRevurdering.KunneIkkeSimulere(SimuleringFeilet.TEKNISK_FEIL),
            expectedStatusCode = HttpStatusCode.InternalServerError,
            expectedJsonResponse = """
                {
                    "message":"Simulering feilet",
                    "code":"simulering_feilet"
                }
            """.trimIndent(),

        )
    }

    private fun shouldMapErrorCorrectly(
        error: KunneIkkeBeregneOgSimulereRevurdering,
        expectedStatusCode: HttpStatusCode,
        expectedJsonResponse: String,
    ) {
        val revurderingServiceMock = mock<RevurderingService> {
            on { beregnOgSimuler(any(), any()) } doReturn error.left()
        }

        testApplication {
            application {
                testSusebakover(services = testServices.copy(revurdering = revurderingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/$revurderingId/beregnOgSimuler",
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
