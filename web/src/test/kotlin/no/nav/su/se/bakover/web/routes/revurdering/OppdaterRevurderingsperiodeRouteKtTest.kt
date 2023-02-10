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
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.oppdater.KunneIkkeOppdatereRevurdering
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.test.opprettetRevurdering
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

internal class OppdaterRevurderingsperiodeRouteKtTest {
    val periode = år(2021)
    private val validBody = """
        {
         "fraOgMed": "${periode.fraOgMed}",
         "tilOgMed": "${periode.tilOgMed}",
         "årsak":"INFORMASJON_FRA_KONTROLLSAMTALE",
         "begrunnelse":"begrunnelse",
         "informasjonSomRevurderes": ["Uførhet"]
        }
    """.trimMargin()

    private val revurderingId = UUID.randomUUID()

    @Test
    fun `uautoriserte kan ikke oppdatere revurderingsperioden`() {
        testApplication {
            application {
                testSusebakover()
            }
            defaultRequest(
                HttpMethod.Put,
                "/saker/$sakId/revurderinger/$revurderingId",
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
    fun `kan oppdatere revurderingsperioden`() {
        val (_, opprettetRevurdering) = opprettetRevurdering(
            revurderingsperiode = mai(2021)..desember(2021),
        )
        val revurderingServiceMock = mock<RevurderingService> {
            on { oppdaterRevurdering(any()) } doReturn opprettetRevurdering.right()
        }

        testApplication {
            application {
                testSusebakover(services = TestServicesBuilder.services(revurdering = revurderingServiceMock))
            }
            defaultRequest(
                HttpMethod.Put,
                "/saker/$sakId/revurderinger/$revurderingId",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    """
                    {
                        "fraOgMed": "${periode.fraOgMed}",
                        "tilOgMed": "${periode.tilOgMed}",
                        "årsak":"DØDSFALL",
                        "begrunnelse":"begrunnelse",
                        "informasjonSomRevurderes": ["Inntekt"]
                    }

                    """.trimMargin(),
                )
            }.apply {
                status shouldBe HttpStatusCode.OK
                val actualResponse = objectMapper.readValue<OpprettetRevurderingJson>(bodyAsText())
                actualResponse.id shouldBe opprettetRevurdering.id.toString()
                actualResponse.status shouldBe RevurderingsStatus.OPPRETTET
            }
        }
    }

    @Test
    fun `ugyldig tilstand`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeOppdatereRevurdering.UgyldigTilstand(
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
    fun `må velge minst en ting å revurdere`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeOppdatereRevurdering.MåVelgeInformasjonSomSkalRevurderes,
            expectedStatusCode = HttpStatusCode.BadRequest,
            expectedJsonResponse = """
                {
                    "message":"Må velge minst en ting som skal revurderes",
                    "code":"må_velge_informasjon_som_revurderes"
                }
            """.trimIndent(),
        )
    }

    private fun shouldMapErrorCorrectly(
        error: KunneIkkeOppdatereRevurdering,
        expectedStatusCode: HttpStatusCode,
        expectedJsonResponse: String,
    ) {
        val revurderingServiceMock = mock<RevurderingService> {
            on { oppdaterRevurdering(any()) } doReturn error.left()
        }

        testApplication {
            application {
                testSusebakover(services = TestServicesBuilder.services(revurdering = revurderingServiceMock))
            }
            defaultRequest(
                HttpMethod.Put,
                "/saker/$sakId/revurderinger/$revurderingId",
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
