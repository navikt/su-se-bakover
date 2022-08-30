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
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.service.revurdering.KunneIkkeOppretteRevurdering
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.sakinfo
import no.nav.su.se.bakover.test.vilkårsvurderingRevurderingIkkeVurdert
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

internal class OpprettRevurderingRouteKtTest {
    //language=JSON
    private val validBody = """
        {
         "fraOgMed": "${periode.fraOgMed}",
         "årsak": "ANDRE_KILDER",
         "begrunnelse": "begrunnelse",
         "informasjonSomRevurderes" : ["Uførhet"]
        }
    """.trimIndent()

    @Test
    fun `uautoriserte kan ikke opprette revurdering`() {
        testApplication {
            application {
                testSusebakover()
            }
            defaultRequest(
                HttpMethod.Post,
                requestPath,
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
    fun `kan opprette revurdering`() {
        val opprettetRevurdering = OpprettetRevurdering(
            id = UUID.randomUUID(),
            periode = periode,
            opprettet = fixedTidspunkt,
            tilRevurdering = vedtak.id,
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandler"),
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = Revurderingsårsak(
                Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
            ),
            forhåndsvarsel = null,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = AvkortingVedRevurdering.Uhåndtert.IngenUtestående,
            sakinfo = sakinfo,
        )
        val revurderingServiceMock = mock<RevurderingService> {
            on { opprettRevurdering(any()) } doReturn opprettetRevurdering.right()
        }

        testApplication {
            application {
                testSusebakover(services = testServices.copy(revurdering = revurderingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                requestPath,
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    """
                    {
                        "fraOgMed": "${periode.fraOgMed}",
                        "årsak":"DØDSFALL",
                        "begrunnelse":"begrunnelse",
                        "informasjonSomRevurderes": ["Uførhet"]
                    }
                    """.trimMargin(),
                )
            }.apply {
                status shouldBe HttpStatusCode.Created
                val actualResponse = objectMapper.readValue<OpprettetRevurderingJson>(bodyAsText())
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
            """.trimIndent(),
        )
    }

    @Test
    fun `fant ingenting som kan revurderes`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeOppretteRevurdering.FeilVedOpprettelseAvRevurdering(Sak.KunneIkkeOppretteRevurdering.FantIngenVedtakSomKanRevurderes),
            expectedStatusCode = HttpStatusCode.NotFound,
            expectedJsonResponse = """
                {
                    "message":"Fant ingen vedtak som kan revurderes for angitt periode",
                    "code":"ingenting_å_revurdere_i_perioden"
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `fant ikke aktør id`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeOppretteRevurdering.FeilVedOpprettelseAvRevurdering(Sak.KunneIkkeOppretteRevurdering.FantIkkeAktørId),
            expectedStatusCode = HttpStatusCode.NotFound,
            expectedJsonResponse = """
                {
                    "message":"Fant ikke aktør id",
                    "code":"fant_ikke_aktør_id"
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `kunne ikke opprette oppgave`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeOppretteRevurdering.FeilVedOpprettelseAvRevurdering(Sak.KunneIkkeOppretteRevurdering.KunneIkkeOppretteOppgave),
            expectedStatusCode = HttpStatusCode.InternalServerError,
            expectedJsonResponse = """
                {
                    "message":"Kunne ikke opprette oppgave",
                    "code":"kunne_ikke_opprette_oppgave"
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `må velge minst en ting å revurdere`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeOppretteRevurdering.MåVelgeInformasjonSomSkalRevurderes,
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
        error: KunneIkkeOppretteRevurdering,
        expectedStatusCode: HttpStatusCode,
        expectedJsonResponse: String,
    ) {
        val revurderingServiceMock = mock<RevurderingService> {
            on { opprettRevurdering(any()) } doReturn error.left()
        }

        testApplication {
            application {
                testSusebakover(services = testServices.copy(revurdering = revurderingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                requestPath,
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
