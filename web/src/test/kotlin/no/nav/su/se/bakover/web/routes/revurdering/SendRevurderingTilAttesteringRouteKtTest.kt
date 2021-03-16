package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlagsdata.Grunnlagsdata
import no.nav.su.se.bakover.service.revurdering.KunneIkkeSendeRevurderingTilAttestering
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.behandling.TestBeregning
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.periode
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.requestPath
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.testServices
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.vedtak
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.LocalDate
import java.util.UUID

internal class SendRevurderingTilAttesteringRouteKtTest {

    private val revurderingId = UUID.randomUUID()

    @Test
    fun `uautoriserte kan ikke sende revurdering til attestering`() {
        withTestApplication({
            testSusebakover()
        }) {
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/$revurderingId/tilAttestering",
                listOf(Brukerrolle.Veileder)
            ).apply {
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
    fun `send til attestering`() {
        val revurderingTilAttestering = RevurderingTilAttestering(
            id = UUID.randomUUID(),
            periode = periode,
            opprettet = Tidspunkt.now(),
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = ""),
            beregning = TestBeregning,
            simulering = Simulering(
                gjelderId = vedtak.behandling.fnr,
                gjelderNavn = "Test",
                datoBeregnet = LocalDate.now(),
                nettoBeløp = 0,
                periodeList = listOf()
            ),
            oppgaveId = OppgaveId("OppgaveId"),
            grunnlagsdata = Grunnlagsdata.EMPTY,
        )

        val revurderingServiceMock = mock<RevurderingService> {
            on { sendTilAttestering(any(), any()) } doReturn revurderingTilAttestering.right()
        }

        withTestApplication({
            testSusebakover(services = testServices.copy(revurdering = revurderingServiceMock))
        }) {
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/${revurderingTilAttestering.id}/tilAttestering",
                listOf(Brukerrolle.Saksbehandler)
            ).apply {
                response.status() shouldBe HttpStatusCode.OK
                val actualResponse = objectMapper.readValue<TilAttesteringJson>(response.content!!)
                actualResponse.id shouldBe revurderingTilAttestering.id.toString()
                actualResponse.status shouldBe RevurderingsStatus.TIL_ATTESTERING
            }
        }
    }

    @Test
    fun `ugyldig fraOgMed dato`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeSendeRevurderingTilAttestering.UgyldigPeriode(
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
            error = KunneIkkeSendeRevurderingTilAttestering.FantIkkeRevurdering,
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
            error = KunneIkkeSendeRevurderingTilAttestering.UgyldigTilstand(
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
    fun `fant ikke aktør id`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeSendeRevurderingTilAttestering.FantIkkeAktørId,
            expectedStatusCode = HttpStatusCode.NotFound,
            expectedJsonResponse = """
                {
                    "message":"Fant ikke aktør id",
                    "code":"fant_ikke_aktør_id"
                }
            """.trimIndent()
        )
    }

    @Test
    fun `kunne ikke opprette oppgave`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeSendeRevurderingTilAttestering.KunneIkkeOppretteOppgave,
            expectedStatusCode = HttpStatusCode.InternalServerError,
            expectedJsonResponse = """
                {
                    "message":"Kunne ikke opprette oppgave",
                    "code":"kunne_ikke_opprette_oppgave"
                }
            """.trimIndent()
        )
    }

    private fun shouldMapErrorCorrectly(
        error: KunneIkkeSendeRevurderingTilAttestering,
        expectedStatusCode: HttpStatusCode,
        expectedJsonResponse: String
    ) {
        val revurderingServiceMock = mock<RevurderingService> {
            on { sendTilAttestering(any(), any()) } doReturn error.left()
        }

        withTestApplication({
            testSusebakover(services = testServices.copy(revurdering = revurderingServiceMock))
        }) {
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/$revurderingId/tilAttestering",
                listOf(Brukerrolle.Saksbehandler)
            ).apply {
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
