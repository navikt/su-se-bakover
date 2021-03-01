package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeBeregneOgSimulereRevurdering
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.argThat
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.behandling.TestBeregning
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesKtTest.Companion.innvilgetSøknadsbehandling
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesKtTest.Companion.periode
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesKtTest.Companion.requestPath
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesKtTest.Companion.testServices
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.LocalDate
import java.util.UUID

internal class BeregnOgSimulerRevurderingRouteKtTest {
    private val validBody = """
        {
            "periode": { "fraOgMed": "${periode.getFraOgMed()}", "tilOgMed": "${periode.getTilOgMed()}"},
            "fradrag": []
        } 
    """.trimIndent()

    private val revurderingId = UUID.randomUUID()

    @Test
    fun `uautoriserte kan ikke beregne og simulere revurdering`() {
        withTestApplication({
            testSusebakover()
        }) {
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/$revurderingId/beregnOgSimuler",
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
    fun `kan opprette beregning og simulering for revurdering`() {
        val månedsberegninger = listOf<Månedsberegning>(
            mock {
                on { getSumYtelse() } doReturn 1
                on { getPeriode() } doReturn TestBeregning.getPeriode()
                on { getSats() } doReturn TestBeregning.getSats()
            }
        )

        val beregning = mock<Beregning> {
            on { getMånedsberegninger() } doReturn månedsberegninger
            on { getId() } doReturn TestBeregning.getId()
            on { getSumYtelse() } doReturn TestBeregning.getSumYtelse()
            on { getFradrag() } doReturn TestBeregning.getFradrag()
            on { getFradragStrategyName() } doReturn TestBeregning.getFradragStrategyName()
            on { getOpprettet() } doReturn TestBeregning.getOpprettet()
            on { getSats() } doReturn TestBeregning.getSats()
            on { getSumFradrag() } doReturn TestBeregning.getSumFradrag()
            on { getPeriode() } doReturn TestBeregning.getPeriode()
        }

        val beregnetRevurdering = OpprettetRevurdering(
            id = UUID.randomUUID(),
            periode = TestBeregning.getPeriode(),
            opprettet = Tidspunkt.now(),
            tilRevurdering = innvilgetSøknadsbehandling.copy(beregning = beregning),
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "")
        ).beregn(
            listOf(
                FradragFactory.ny(
                    type = Fradragstype.BidragEtterEkteskapsloven,
                    månedsbeløp = 12.0,
                    periode = TestBeregning.getMånedsberegninger()[0].getPeriode(),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                )
            )
        ).orNull()!!

        val simulertRevurdering = when (beregnetRevurdering) {
            is BeregnetRevurdering.Innvilget -> {
                beregnetRevurdering.toSimulert(
                    Simulering(
                        gjelderId = innvilgetSøknadsbehandling.fnr,
                        gjelderNavn = "Test",
                        datoBeregnet = LocalDate.now(),
                        nettoBeløp = 0,
                        periodeList = listOf()
                    )
                )
            }
            is BeregnetRevurdering.Avslag -> throw RuntimeException("Revurderingen må ha en endring på minst 10 prosent")
        }

        val revurderingServiceMock = mock<RevurderingService> {
            on { beregnOgSimuler(any(), any(), any()) } doReturn simulertRevurdering.right()
        }

        withTestApplication({
            testSusebakover(services = testServices.copy(revurdering = revurderingServiceMock))
        }) {
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/${simulertRevurdering.id}/beregnOgSimuler",
                listOf(Brukerrolle.Saksbehandler)
            ) {
                setBody(validBody)
            }.apply {
                response.status() shouldBe HttpStatusCode.Created
                val actualResponse = objectMapper.readValue<SimulertRevurderingJson>(response.content!!)
                verify(revurderingServiceMock).beregnOgSimuler(
                    argThat { it shouldBe simulertRevurdering.id },
                    argThat { it shouldBe NavIdentBruker.Saksbehandler("Z990Lokal") },
                    argThat { it shouldBe emptyList() },
                )
                verifyNoMoreInteractions(revurderingServiceMock)
                actualResponse.id shouldBe simulertRevurdering.id.toString()
                actualResponse.status shouldBe RevurderingsStatus.SIMULERT
            }
        }
    }

    @Test
    fun `ugyldig fraOgMed dato`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeBeregneOgSimulereRevurdering.UgyldigPeriode(
                Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden
            ),
            expectedStatusCode = HttpStatusCode.BadRequest,
            expectedJsonResponse = """
                {
                    "message":"FraOgMedDatoMåVæreFørsteDagIMåneden",
                    "code":"ugyldig_periode"
                }
            """.trimIndent()

        )
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
            """.trimIndent()

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
                    "code":"ugyldig_periode"
                }
            """.trimIndent()

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
            """.trimIndent()

        )
    }

    @Test
    fun `simulering feilet`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeBeregneOgSimulereRevurdering.SimuleringFeilet,
            expectedStatusCode = HttpStatusCode.InternalServerError,
            expectedJsonResponse = """
                {
                    "message":"Simulering feilet",
                    "code":"simulering_feilet"
                }
            """.trimIndent()

        )
    }

    private fun shouldMapErrorCorrectly(
        error: KunneIkkeBeregneOgSimulereRevurdering,
        expectedStatusCode: HttpStatusCode,
        expectedJsonResponse: String
    ) {
        val revurderingServiceMock = mock<RevurderingService> {
            on { beregnOgSimuler(any(), any(), any()) } doReturn error.left()
        }

        withTestApplication({
            testSusebakover(services = testServices.copy(revurdering = revurderingServiceMock))
        }) {
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/$revurderingId/beregnOgSimuler",
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
