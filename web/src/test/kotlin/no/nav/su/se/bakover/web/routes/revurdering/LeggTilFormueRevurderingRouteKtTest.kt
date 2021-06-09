package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilFormuegrunnlag
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.opprettetRevurdering
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class LeggTilFormueRevurderingRouteKtTest {
    private val revurderingId = UUID.randomUUID().toString()

    //language=JSON
    private val validBody = """
        {
          "søkersFormue" : {
            "verdiIkkePrimærbolig": 0,
            "verdiEiendommer": 0,
            "verdiKjøretøy": 0,
            "innskudd": 0,
            "verdipapir": 0,
            "pengerSkyldt": 0,
            "kontanter": 0,
            "depositumskonto": 0
          }
        }
    """.trimIndent()

    @Test
    fun `ikke tillatte roller`() {
        Brukerrolle.values().filterNot { it == Brukerrolle.Saksbehandler }.forEach { rolle ->
            withTestApplication(
                {
                    testSusebakover()
                },
            ) {
                defaultRequest(
                    HttpMethod.Post,
                    "${RevurderingRoutesTestData.requestPath}/$revurderingId/formuegrunnlag",
                    listOf(rolle),
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
                        true,
                    )
                }
            }
        }
    }

    @Test
    fun `fant ikke revurdering`() {
        val revurderingServiceMock = mock<RevurderingService> {
            on { leggTilFormuegrunnlag(any()) } doReturn KunneIkkeLeggeTilFormuegrunnlag.FantIkkeRevurdering.left()
        }

        withTestApplication(
            {
                testSusebakover(services = RevurderingRoutesTestData.testServices.copy(revurdering = revurderingServiceMock))
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "${RevurderingRoutesTestData.requestPath}/$revurderingId/formuegrunnlag",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(validBody)
            }.apply {
                response.status() shouldBe HttpStatusCode.NotFound
                JSONAssert.assertEquals(
                    """
                            {
                                "message":"Fant ikke revurdering",
                                "code":"fant_ikke_revurdering"
                            }
                    """.trimIndent(),
                    response.content,
                    true,
                )
            }
        }
    }

    @Test
    fun `happy case`() {
        val revurderingServiceMock = mock<RevurderingService> {
            on { leggTilFormuegrunnlag(any()) } doReturn opprettetRevurdering.right()
        }

        withTestApplication(
            {
                testSusebakover(services = RevurderingRoutesTestData.testServices.copy(revurdering = revurderingServiceMock))
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "${RevurderingRoutesTestData.requestPath}/$revurderingId/formuegrunnlag",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(validBody)
            }.apply {
                response.status() shouldBe HttpStatusCode.OK
                response.headers.values("Content-Type") shouldBe listOf("application/json; charset=UTF-8")
                response.headers.values("X-Correlation-ID") shouldBe listOf("her skulle vi sikkert hatt en korrelasjonsid")
                response.content shouldContain opprettetRevurdering.id.toString()
            }
        }
    }
}
