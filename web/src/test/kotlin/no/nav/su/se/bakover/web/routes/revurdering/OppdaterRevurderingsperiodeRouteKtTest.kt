package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.service.revurdering.KunneIkkeOppdatereRevurdering
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.periode
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.requestPath
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.testServices
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.vedtak
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class OppdaterRevurderingsperiodeRouteKtTest {
    private val validBody = """
        {
         "fraOgMed": "${periode.fraOgMed}",
         "årsak":"INFORMASJON_FRA_KONTROLLSAMTALE",
         "begrunnelse":"begrunnelse",
         "informasjonSomRevurderes": ["Uførhet"]
        }
    """.trimMargin()

    private val revurderingId = UUID.randomUUID()

    @Test
    fun `uautoriserte kan ikke oppdatere revurderingsperioden`() {
        withTestApplication(
            {
                testSusebakover()
            },
        ) {
            defaultRequest(
                HttpMethod.Put,
                "$requestPath/$revurderingId",
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
    fun `kan oppdatere revurderingsperioden`() {
        val opprettetRevurdering = OpprettetRevurdering(
            id = UUID.randomUUID(),
            periode = periode,
            opprettet = fixedTidspunkt,
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandler"),
            oppgaveId = OppgaveId("oppgaveId"),
            fritekstTilBrev = "",
            revurderingsårsak = Revurderingsårsak(
                Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
            ),
            forhåndsvarsel = null,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = AvkortingVedRevurdering.Uhåndtert.IngenUtestående
        )
        val revurderingServiceMock = mock<RevurderingService> {
            on { oppdaterRevurdering(any()) } doReturn opprettetRevurdering.right()
        }

        withTestApplication(
            {
                testSusebakover(services = testServices.copy(revurdering = revurderingServiceMock))
            },
        ) {
            defaultRequest(
                HttpMethod.Put,
                "$requestPath/$revurderingId",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    """
                    {
                        "fraOgMed": "${periode.fraOgMed}",
                        "årsak":"DØDSFALL",
                        "begrunnelse":"begrunnelse",
                        "informasjonSomRevurderes": ["Inntekt"]
                    }

                    """.trimMargin(),
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.OK
                val actualResponse = objectMapper.readValue<OpprettetRevurderingJson>(response.content!!)
                actualResponse.id shouldBe opprettetRevurdering.id.toString()
                actualResponse.status shouldBe RevurderingsStatus.OPPRETTET
            }
        }
    }

    @Test
    fun `ugyldig fraOgMed dato`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeOppdatereRevurdering.UgyldigPeriode(
                Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden,
            ),
            expectedStatusCode = HttpStatusCode.BadRequest,
            expectedJsonResponse = """
                {
                    "message":"FraOgMedDatoMåVæreFørsteDagIMåneden",
                    "code":"ugyldig_periode"
                }
            """.trimIndent(),

        )
    }

    @Test
    fun `fant ikke revurdering`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeOppdatereRevurdering.FantIkkeRevurdering,
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

    @Test
    fun `feilmelding hvis det eksisterer flere bosituasjoner`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeOppdatereRevurdering.BosituasjonMedFlerePerioderMåRevurderes,
            expectedStatusCode = HttpStatusCode.BadRequest,
            expectedJsonResponse = """
                {
                    "message":"Bosituasjon og inntekt må revurderes siden det finnes flere bosituasjonsperioder",
                    "code":"bosituasjon_med_flere_perioder_må_revurderes"
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

        withTestApplication(
            {
                testSusebakover(services = testServices.copy(revurdering = revurderingServiceMock))
            },
        ) {
            defaultRequest(
                HttpMethod.Put,
                "$requestPath/$revurderingId",
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
