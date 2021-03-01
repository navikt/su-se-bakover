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
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeOppretteRevurdering
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.innvilgetSøknadsbehandling
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.periode
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.requestPath
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.testServices
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class OpprettRevurderingRouteKtTest {
    private val validBody = """{ "fraOgMed": "${periode.getFraOgMed()}"}"""

    @Test
    fun `uautoriserte kan ikke opprette revurdering`() {
        withTestApplication({
            testSusebakover()
        }) {
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/opprett",
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
    fun `kan opprette revurdering`() {
        val opprettetRevurdering = OpprettetRevurdering(
            id = UUID.randomUUID(),
            periode = periode,
            opprettet = Tidspunkt.now(),
            tilRevurdering = innvilgetSøknadsbehandling,
            saksbehandler = NavIdentBruker.Saksbehandler("")

        )
        val revurderingServiceMock = mock<RevurderingService> {
            on { opprettRevurdering(any(), any(), any()) } doReturn opprettetRevurdering.right()
        }

        withTestApplication({
            testSusebakover(services = testServices.copy(revurdering = revurderingServiceMock))
        }) {
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/opprett",
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
    fun `fant ikke sak`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeOppretteRevurdering.FantIkkeSak,
            expectedStatusCode = HttpStatusCode.NotFound,
            expectedJsonResponse = """
                {
                    "message":"Fant ikke sak",
                    "code":"fant_ikke_sak"
                }
            """.trimIndent()
        )
    }

    @Test
    fun `fant ingenting som kan revurderes`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeOppretteRevurdering.FantIngentingSomKanRevurderes,
            expectedStatusCode = HttpStatusCode.NotFound,
            expectedJsonResponse = """
                {
                    "message":"Ingen behandlinger som kan revurderes for angitt periode",
                    "code":"ingenting_å_revurdere_i_perioden"
                }
            """.trimIndent()
        )
    }

    @Test
    fun `fant ikke aktør id`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeOppretteRevurdering.FantIkkeAktørId,
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
            error = KunneIkkeOppretteRevurdering.KunneIkkeOppretteOppgave,
            expectedStatusCode = HttpStatusCode.InternalServerError,
            expectedJsonResponse = """
                {
                    "message":"Kunne ikke opprette oppgave",
                    "code":"kunne_ikke_opprette_oppgave"
                }
            """.trimIndent()
        )
    }

    @Test
    fun `kunne ikke revurdere inneværende måned eller tidligere`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeOppretteRevurdering.KanIkkeRevurdereInneværendeMånedEllerTidligere,
            expectedStatusCode = HttpStatusCode.BadRequest,
            expectedJsonResponse = """
                {
                    "message":"Revurdering kan kun gjøres fra og med neste kalendermåned",
                    "code":"tidligest_neste_måned"
                }
            """.trimIndent()

        )
    }

    @Test
    fun `kan ikke revurdere perioder med flere aktive stønadsperioder`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeOppretteRevurdering.KanIkkeRevurderePerioderMedFlereAktiveStønadsperioder,
            expectedStatusCode = HttpStatusCode.BadRequest,
            expectedJsonResponse = """
                {
                    "message":"Revurderingsperioden kan ikke overlappe flere aktive stønadsperioder",
                    "code":"flere_aktive_stønadsperioder"
                }
            """.trimIndent()

        )
    }

    @Test
    fun `kan ikke revurdere en periode med eksisterende revurdering`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeOppretteRevurdering.KanIkkeRevurdereEnPeriodeMedEksisterendeRevurdering,
            expectedStatusCode = HttpStatusCode.BadRequest,
            expectedJsonResponse = """
                {
                    "message":"Kan ikke revurdere en behandling som allerede har en eksisterende revurdering",
                    "code":"finnes_en_eksisterende_revurdering"
                }
            """.trimIndent()

        )
    }

    @Test
    fun `ugyldig fraOgMed dato`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeOppretteRevurdering.UgyldigPeriode(
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

    private fun shouldMapErrorCorrectly(
        error: KunneIkkeOppretteRevurdering,
        expectedStatusCode: HttpStatusCode,
        expectedJsonResponse: String
    ) {
        val revurderingServiceMock = mock<RevurderingService> {
            on { opprettRevurdering(any(), any(), any()) } doReturn error.left()
        }

        withTestApplication({
            testSusebakover(services = testServices.copy(revurdering = revurderingServiceMock))
        }) {
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/opprett",
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
