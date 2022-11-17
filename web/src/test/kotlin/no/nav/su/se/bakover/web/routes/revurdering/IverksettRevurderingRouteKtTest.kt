package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.shouldBe
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeIverksetteRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingService
import no.nav.su.se.bakover.domain.sak.SimulerUtbetalingFeilet
import no.nav.su.se.bakover.test.iverksattRevurdering
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class IverksettRevurderingRouteKtTest {

    private val revurderingId = UUID.randomUUID()
    private val path = "/saker/$sakId/revurderinger/$revurderingId/iverksett"

    @Test
    fun `uautoriserte kan ikke iverksette revurdering`() {
        testApplication {
            application {
                testSusebakover()
            }
            defaultRequest(
                HttpMethod.Post,
                path,
                listOf(Brukerrolle.Veileder),
            ).apply {
                status shouldBe HttpStatusCode.Forbidden
                JSONAssert.assertEquals(
                    """
                    {
                        "message":"Bruker mangler en av de tillatte rollene: [Attestant]",
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
    fun `iverksett revurdering`() {
        val iverksattRevurdering = iverksattRevurdering().second
        val revurderingServiceMock = mock<RevurderingService> {
            on { iverksett(any(), any()) } doReturn iverksattRevurdering.right()
        }

        testApplication {
            application {
                testSusebakover(services = TestServicesBuilder.services(revurdering = revurderingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                path,
                listOf(Brukerrolle.Attestant),
            ).apply {
                status shouldBe HttpStatusCode.OK
                val actualResponse = objectMapper.readValue<IverksattRevurderingJson>(bodyAsText())
                actualResponse.id shouldBe iverksattRevurdering.id.toString()
                actualResponse.status shouldBe RevurderingsStatus.IVERKSATT_INNVILGET
            }
        }
    }

    @Test
    fun `fant ikke revurdering`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeIverksetteRevurdering.FeilVedIverksettelse(no.nav.su.se.bakover.domain.sak.iverksett.KunneIkkeIverksetteRevurdering.FantIkkeRevurdering),
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
            error = KunneIkkeIverksetteRevurdering.FeilVedIverksettelse(
                no.nav.su.se.bakover.domain.sak.iverksett.KunneIkkeIverksetteRevurdering.UgyldigTilstand(
                    fra = IverksattRevurdering::class,
                    til = OpprettetRevurdering::class,
                ),
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
    fun `attestant og saksbehandler kan ikke være samme person`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeIverksetteRevurdering.FeilVedIverksettelse(no.nav.su.se.bakover.domain.sak.iverksett.KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson),
            expectedStatusCode = HttpStatusCode.Forbidden,
            expectedJsonResponse = """
                {
                    "message":"Attestant og saksbehandler kan ikke være samme person",
                    "code":"attestant_og_saksbehandler_kan_ikke_være_samme_person"
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `kunne ikke kontrollsimulere`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeIverksetteRevurdering.FeilVedIverksettelse(no.nav.su.se.bakover.domain.sak.iverksett.KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale(UtbetalingFeilet.KunneIkkeSimulere(SimulerUtbetalingFeilet.FeilVedSimulering(SimuleringFeilet.TekniskFeil)))),
            expectedStatusCode = HttpStatusCode.InternalServerError,
            expectedJsonResponse = """
                {
                    "message":"Simulering feilet",
                    "code":"simulering_feilet"
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `kunne ikke utbetale`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeIverksetteRevurdering.IverksettelsestransaksjonFeilet(no.nav.su.se.bakover.domain.sak.iverksett.KunneIkkeFerdigstilleIverksettelsestransaksjon.KunneIkkeUtbetale(UtbetalingFeilet.Protokollfeil)),
            expectedStatusCode = HttpStatusCode.InternalServerError,
            expectedJsonResponse = """
                {
                    "message":"Kunne ikke utføre utbetaling",
                    "code":"kunne_ikke_utbetale"
                }
            """.trimIndent(),
        )
    }

    private fun shouldMapErrorCorrectly(
        error: KunneIkkeIverksetteRevurdering,
        expectedStatusCode: HttpStatusCode,
        expectedJsonResponse: String,
    ) {
        val revurderingServiceMock = mock<RevurderingService> {
            on { iverksett(any(), any()) } doReturn error.left()
        }

        testApplication {
            application {
                testSusebakover(services = TestServicesBuilder.services(revurdering = revurderingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                path,
                listOf(Brukerrolle.Attestant),
            ).apply {
                status shouldBe expectedStatusCode
                JSONAssert.assertEquals(expectedJsonResponse, bodyAsText(), true)
            }
        }
    }
}
