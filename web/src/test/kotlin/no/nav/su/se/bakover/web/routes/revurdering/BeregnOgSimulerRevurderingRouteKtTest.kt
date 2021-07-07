package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.left
import arrow.core.nonEmptyListOf
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
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.service.revurdering.BeregnOgSimulerResponse
import no.nav.su.se.bakover.service.revurdering.KunneIkkeBeregneOgSimulereRevurdering
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.web.argThat
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.fixedTidspunkt
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.formueVilkår
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.periode
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.requestPath
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.testServices
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.vedtak
import no.nav.su.se.bakover.web.routes.søknadsbehandling.TestBeregning
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.LocalDate
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
        withTestApplication(
            {
                testSusebakover()
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/$revurderingId/beregnOgSimuler",
                listOf(Brukerrolle.Veileder),
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

    @Test
    fun `kan opprette beregning og simulering for revurdering`() {
        val månedsberegninger = listOf<Månedsberegning>(
            mock {
                on { getSumYtelse() } doReturn 1
                on { periode } doReturn TestBeregning.periode
                on { getSats() } doReturn TestBeregning.getSats()
            },
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
            on { periode } doReturn TestBeregning.periode
        }

        val uføregrunnlag = Grunnlag.Uføregrunnlag(
            periode = TestBeregning.periode,
            uføregrad = Uføregrad.parse(20),
            forventetInntekt = 12000,
            opprettet = fixedTidspunkt,
        )
        val beregnetRevurdering = OpprettetRevurdering(
            id = UUID.randomUUID(),
            periode = TestBeregning.periode,
            opprettet = Tidspunkt.now(),
            tilRevurdering = vedtak.copy(beregning = beregning),
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = ""),
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = Revurderingsårsak(
                Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
            ),
            forhåndsvarsel = null,
            grunnlagsdata = Grunnlagsdata(
                bosituasjon = listOf(
                    Grunnlag.Bosituasjon.Fullstendig.Enslig(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(),
                        periode = TestBeregning.periode,
                        begrunnelse = null,
                    ),
                ),
            ),
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Uførhet.Vurdert.create(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Uføre.create(
                            resultat = Resultat.Innvilget,
                            grunnlag = uføregrunnlag,
                            periode = TestBeregning.periode,
                            begrunnelse = null,
                            opprettet = fixedTidspunkt
                        ),
                    ),
                ),
                formue = formueVilkår(periode),
            ),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
        ).beregn(eksisterendeUtbetalinger = emptyList()).orNull()!!

        val simulertRevurdering = when (beregnetRevurdering) {
            is BeregnetRevurdering.Innvilget -> {
                beregnetRevurdering.toSimulert(
                    Simulering(
                        gjelderId = vedtak.behandling.fnr,
                        gjelderNavn = "Test",
                        datoBeregnet = LocalDate.now(),
                        nettoBeløp = 0,
                        periodeList = listOf(),
                    ),
                )
            }
            is BeregnetRevurdering.IngenEndring -> throw RuntimeException("Revurderingen må ha en endring på minst 10 prosent")
            is BeregnetRevurdering.Opphørt -> throw RuntimeException("Beregningen har 0 kroners utbetalinger")
        }

        val revurderingServiceMock = mock<RevurderingService> {
            on { beregnOgSimuler(any(), any()) } doReturn BeregnOgSimulerResponse(simulertRevurdering).right()
        }

        withTestApplication(
            {
                testSusebakover(services = testServices.copy(revurdering = revurderingServiceMock))
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/${simulertRevurdering.id}/beregnOgSimuler",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(validBody)
            }.apply {
                response.status() shouldBe HttpStatusCode.Created
                val actualResponse = objectMapper.readValue<Map<String, Any>>(response.content!!)
                val revurdering = objectMapper.readValue<SimulertRevurderingJson>(objectMapper.writeValueAsString(actualResponse["revurdering"]))
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
            error = KunneIkkeBeregneOgSimulereRevurdering.SimuleringFeilet,
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

        withTestApplication(
            {
                testSusebakover(services = testServices.copy(revurdering = revurderingServiceMock))
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/$revurderingId/beregnOgSimuler",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(validBody)
            }.apply {
                response.status() shouldBe expectedStatusCode
                JSONAssert.assertEquals(
                    expectedJsonResponse,
                    response.content,
                    true,
                )
            }
        }
    }
}
