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
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingOgFeilmeldingerResponse
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.domain.revurdering.vilkår.formue.KunneIkkeLeggeTilFormuegrunnlag
import no.nav.su.se.bakover.domain.vilkår.formue.LeggTilFormuevilkårRequest
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class LeggTilFormueRevurderingRouteKtTest {
    private val revurderingId = UUID.randomUUID().toString()

    //language=JSON
    private val validBody = """
        [{
           "periode":{"fraOgMed":"2021-05-01","tilOgMed":"2021-12-31"},
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
        }]
    """.trimIndent()

    @Test
    fun `ikke tillatte roller`() {
        Brukerrolle.entries.filterNot { it == Brukerrolle.Saksbehandler }.forEach { rolle ->
            testApplication {
                application {
                    testSusebakoverWithMockedDb()
                }

                defaultRequest(
                    HttpMethod.Post,
                    "/saker/$sakId/revurderinger/$revurderingId/formuegrunnlag",
                    listOf(rolle),
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
    }

    @Test
    fun `fant ikke revurdering`() {
        assertErrorMapsToJson(
            error = KunneIkkeLeggeTilFormuegrunnlag.FantIkkeRevurdering,
            expectStatusCode = HttpStatusCode.NotFound,
            expectErrorJson = """
                {
                    "message":"Fant ikke revurdering",
                    "code":"fant_ikke_revurdering"
                }
            """.trimIndent(),

        )
    }

    @Test
    fun `ugyldig tilstand`() {
        assertErrorMapsToJson(
            error = KunneIkkeLeggeTilFormuegrunnlag.UgyldigTilstand(
                OpprettetRevurdering::class,
                OpprettetRevurdering::class,
            ),
            expectStatusCode = HttpStatusCode.BadRequest,
            expectErrorJson = """
                {
                    "message":"Kan ikke gå fra tilstanden OpprettetRevurdering til tilstanden OpprettetRevurdering",
                    "code":"ugyldig_tilstand"
                }
            """.trimIndent(),

        )
    }

    @Test
    fun `ikke lov med overlappende perioder`() {
        assertErrorMapsToJson(
            error = KunneIkkeLeggeTilFormuegrunnlag.KunneIkkeMappeTilDomenet(
                LeggTilFormuevilkårRequest.KunneIkkeMappeTilDomenet.IkkeLovMedOverlappendePerioder,
            ),
            expectStatusCode = HttpStatusCode.BadRequest,
            expectErrorJson = """
                {
                    "message":"Vurderingperioder kan ikke overlappe",
                    "code":"overlappende_vurderingsperioder"
                }
            """.trimIndent(),

        )
    }

    @Test
    fun `formueperioden er utenfor behandlingsperioden`() {
        assertErrorMapsToJson(
            error = KunneIkkeLeggeTilFormuegrunnlag.KunneIkkeMappeTilDomenet(LeggTilFormuevilkårRequest.KunneIkkeMappeTilDomenet.FormuePeriodeErUtenforBehandlingsperioden),
            expectStatusCode = HttpStatusCode.BadRequest,
            expectErrorJson = """
                {
                    "message":"Ikke lov med formueperiode utenfor behandlingsperioden",
                    "code":"ikke_lov_med_formueperiode_utenfor_behandlingsperioden"
                }
            """.trimIndent(),

        )
    }

    private fun assertErrorMapsToJson(
        error: KunneIkkeLeggeTilFormuegrunnlag,
        expectStatusCode: HttpStatusCode,
        expectErrorJson: String,
    ) {
        val revurderingServiceMock = mock<RevurderingService> {
            on { leggTilFormuegrunnlag(any()) } doReturn error.left()
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services(revurdering = revurderingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "/saker/$sakId/revurderinger/$revurderingId/formuegrunnlag",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(validBody)
            }.apply {
                status shouldBe expectStatusCode
                JSONAssert.assertEquals(
                    expectErrorJson,
                    bodyAsText(),
                    true,
                )
            }
        }
    }

    @Test
    fun `happy case`() {
        val opprettetRevurdering = opprettetRevurdering().second
        val revurderingServiceMock = mock<RevurderingService> {
            on { leggTilFormuegrunnlag(any()) } doReturn RevurderingOgFeilmeldingerResponse(
                opprettetRevurdering,
                emptyList(),
            ).right()
        }

        testApplication {
            application {
                testSusebakoverWithMockedDb(services = TestServicesBuilder.services(revurdering = revurderingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "/saker/$sakId/revurderinger/$revurderingId/formuegrunnlag",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(validBody)
            }.apply {
                status shouldBe HttpStatusCode.OK
                this.headers["Content-Type"] shouldBe "application/json"
                this.headers["X-Correlation-ID"] shouldBe "her skulle vi sikkert hatt en korrelasjonsid"
                bodyAsText() shouldContain opprettetRevurdering.id.toString()
            }
        }
    }
}
