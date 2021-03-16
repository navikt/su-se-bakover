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
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlagsdata.Grunnlagsdata
import no.nav.su.se.bakover.service.revurdering.KunneIkkeOppdatereRevurderingsperiode
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.periode
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.requestPath
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.testServices
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.vedtak
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class OppdaterRevurderingsperiodeRouteKtTest {
    private val validBody = """{ "fraOgMed": "${periode.getFraOgMed()}"}"""

    private val revurderingId = UUID.randomUUID()

    @Test
    fun `uautoriserte kan ikke oppdatere revurderingsperioden`() {
        withTestApplication({
            testSusebakover()
        }) {
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/$revurderingId/oppdaterPeriode",
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
    fun `kan oppdatere revurderingsperioden`() {
        val opprettetRevurdering = OpprettetRevurdering(
            id = UUID.randomUUID(),
            periode = periode,
            opprettet = Tidspunkt.now(),
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler(""),
            oppgaveId = OppgaveId("oppgaveId"),
            grunnlagsdata = Grunnlagsdata.EMPTY,
        )
        val revurderingServiceMock = mock<RevurderingService> {
            on { oppdaterRevurderingsperiode(any(), any(), any()) } doReturn opprettetRevurdering.right()
        }

        withTestApplication({
            testSusebakover(services = testServices.copy(revurdering = revurderingServiceMock))
        }) {
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/$revurderingId/oppdaterPeriode",
                listOf(Brukerrolle.Saksbehandler)
            ) {
                setBody("""{ "fraOgMed": "${periode.getFraOgMed()}"}""")
            }.apply {
                response.status() shouldBe HttpStatusCode.Created
                val actualResponse = objectMapper.readValue<OpprettetRevurderingJson>(response.content!!)
                actualResponse.id shouldBe opprettetRevurdering.id.toString()
                actualResponse.status shouldBe RevurderingsStatus.OPPRETTET
            }
        }
    }

    @Test
    fun `ugyldig fraOgMed dato`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeOppdatereRevurderingsperiode.UgyldigPeriode(
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
            error = KunneIkkeOppdatereRevurderingsperiode.FantIkkeRevurdering,
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
    fun `perioden må være innenfor allerede valgt stønadsperiode`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeOppdatereRevurderingsperiode.PeriodenMåVæreInnenforAlleredeValgtStønadsperiode(
                Periode.create(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.januar(2020),
                )
            ),
            expectedStatusCode = HttpStatusCode.BadRequest,
            expectedJsonResponse = """
                {
                    "message":"Perioden må være innenfor allerede valgt stønadsperiode",
                    "code":"perioden_må_være_innenfor_stønadsperioden"
                }
            """.trimIndent()

        )
    }

    @Test
    fun `ugyldig tilstand`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeOppdatereRevurderingsperiode.UgyldigTilstand(
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

    private fun shouldMapErrorCorrectly(
        error: KunneIkkeOppdatereRevurderingsperiode,
        expectedStatusCode: HttpStatusCode,
        expectedJsonResponse: String
    ) {
        val revurderingServiceMock = mock<RevurderingService> {
            on { oppdaterRevurderingsperiode(any(), any(), any()) } doReturn error.left()
        }

        withTestApplication({
            testSusebakover(services = testServices.copy(revurdering = revurderingServiceMock))
        }) {
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/$revurderingId/oppdaterPeriode",
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
