package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.domain.bruker.Brukerrolle
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilFormuegrunnlag
import no.nav.su.se.bakover.service.revurdering.RevurderingOgFeilmeldingerResponse
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.opprettetRevurdering
import no.nav.su.se.bakover.web.testSusebakover
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
            error = KunneIkkeLeggeTilFormuegrunnlag.IkkeLovMedOverlappendePerioder,
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
    fun `eps formueperioder er utenfor bosituasjonsperioden`() {
        assertErrorMapsToJson(
            error = KunneIkkeLeggeTilFormuegrunnlag.EpsFormueperiodeErUtenforBosituasjonPeriode,
            expectStatusCode = HttpStatusCode.BadRequest,
            expectErrorJson = """
                {
                    "message":"Ikke lov med formueperiode utenfor bosituasjonperioder",
                    "code":"ikke_lov_med_formueperiode_utenfor_bosituasjonperiode"
                }
            """.trimIndent(),

        )
    }

    @Test
    fun `formueperioden er utenfor behandlingsperioden`() {
        assertErrorMapsToJson(
            error = KunneIkkeLeggeTilFormuegrunnlag.FormuePeriodeErUtenforBehandlingsperioden,
            expectStatusCode = HttpStatusCode.BadRequest,
            expectErrorJson = """
                {
                    "message":"Ikke lov med formueperiode utenfor behandlingsperioden",
                    "code":"ikke_lov_med_formueperiode_utenfor_behandlingsperioden"
                }
            """.trimIndent(),

        )
    }

    @Test
    fun `må ha EPS hvis man har lagt inn eps formue`() {
        assertErrorMapsToJson(
            error = KunneIkkeLeggeTilFormuegrunnlag.MåHaEpsHvisManHarSattEpsFormue,
            expectStatusCode = HttpStatusCode.BadRequest,
            expectErrorJson = """
                {
                    "message":"Ikke lov med formue for eps hvis man ikke har eps",
                    "code":"ikke_lov_med_formue_for_eps_hvis_man_ikke_har_eps"
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
                response.status() shouldBe expectStatusCode
                JSONAssert.assertEquals(
                    expectErrorJson,
                    response.content,
                    true,
                )
            }
        }
    }

    @Test
    fun `happy case`() {
        val revurderingServiceMock = mock<RevurderingService> {
            on { leggTilFormuegrunnlag(any()) } doReturn RevurderingOgFeilmeldingerResponse(
                opprettetRevurdering,
                emptyList(),
            ).right()
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
