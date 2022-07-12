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
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeBehovForTilbakekrevingUnderBehandling
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.service.revurdering.KunneIkkeSendeRevurderingTilAttestering
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.vilkårsvurderingRevurderingIkkeVurdert
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.periode
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.requestPath
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.testServices
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.vedtak
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.TestBeregning
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class SendRevurderingTilAttesteringRouteKtTest {

    private val revurderingId = UUID.randomUUID()

    @Test
    fun `uautoriserte kan ikke sende revurdering til attestering`() {
        testApplication {
            application {
                testSusebakover()
            }
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/$revurderingId/tilAttestering",
                listOf(Brukerrolle.Veileder),
            ).apply {
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
    fun innvilget() {
        val revurderingTilAttestering = RevurderingTilAttestering.Innvilget(
            id = UUID.randomUUID(),
            periode = periode,
            opprettet = fixedTidspunkt,
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandler"),
            beregning = TestBeregning,
            simulering = Simulering(
                gjelderId = vedtak.behandling.fnr,
                gjelderNavn = "Test",
                datoBeregnet = fixedLocalDate,
                nettoBeløp = 0,
                periodeList = listOf(),
            ),
            oppgaveId = OppgaveId("OppgaveId"),
            fritekstTilBrev = "",
            revurderingsårsak = Revurderingsårsak(
                Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
            ),
            forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
            tilbakekrevingsbehandling = IkkeBehovForTilbakekrevingUnderBehandling,
            sakinfo = vedtak.sakinfo(),
        )

        val revurderingServiceMock = mock<RevurderingService> {
            on { sendTilAttestering(any()) } doReturn revurderingTilAttestering.right()
        }

        testApplication {
            application {
                testSusebakover(services = testServices.copy(revurdering = revurderingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/${revurderingTilAttestering.id}/tilAttestering",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{ "fritekstTilBrev": "Friteksten" }""")
            }.apply {
                status shouldBe HttpStatusCode.OK
                val actualResponse = objectMapper.readValue<TilAttesteringJson>(bodyAsText())
                actualResponse.id shouldBe revurderingTilAttestering.id.toString()
                actualResponse.status shouldBe RevurderingsStatus.TIL_ATTESTERING_INNVILGET
            }
        }
    }

    @Test
    fun `ingen endring uten brev`() {
        val revurderingTilAttestering = RevurderingTilAttestering.IngenEndring(
            id = UUID.randomUUID(),
            periode = periode,
            opprettet = fixedTidspunkt,
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandler"),
            beregning = TestBeregning,
            oppgaveId = OppgaveId("OppgaveId"),
            fritekstTilBrev = "",
            revurderingsårsak = Revurderingsårsak(
                Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
            ),
            skalFøreTilUtsendingAvVedtaksbrev = false,
            forhåndsvarsel = null,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
            sakinfo = vedtak.sakinfo(),
        )

        val revurderingServiceMock = mock<RevurderingService> {
            on { sendTilAttestering(any()) } doReturn revurderingTilAttestering.right()
        }

        testApplication {
            application {
                testSusebakover(services = testServices.copy(revurdering = revurderingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/${revurderingTilAttestering.id}/tilAttestering",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{ "fritekstTilBrev": "", "skalFøreTilBrevutsending": "false" }""")
            }.apply {
                status shouldBe HttpStatusCode.OK
                val actualResponse = objectMapper.readValue<TilAttesteringJson>(bodyAsText())
                actualResponse.id shouldBe revurderingTilAttestering.id.toString()
                actualResponse.status shouldBe RevurderingsStatus.TIL_ATTESTERING_INGEN_ENDRING
                actualResponse.fritekstTilBrev shouldBe ""
                actualResponse.skalFøreTilBrevutsending shouldBe false
            }
        }
    }

    @Test
    fun `ingen endring med brev`() {
        val revurderingTilAttestering = RevurderingTilAttestering.IngenEndring(
            id = UUID.randomUUID(),
            periode = periode,
            opprettet = fixedTidspunkt,
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandler"),
            beregning = TestBeregning,
            oppgaveId = OppgaveId("OppgaveId"),
            fritekstTilBrev = "Friteksten",
            revurderingsårsak = Revurderingsårsak(
                Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
            ),
            skalFøreTilUtsendingAvVedtaksbrev = true,
            forhåndsvarsel = null,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
            sakinfo = vedtak.sakinfo(),
        )

        val revurderingServiceMock = mock<RevurderingService> {
            on { sendTilAttestering(any()) } doReturn revurderingTilAttestering.right()
        }

        testApplication {
            application {
                testSusebakover(services = testServices.copy(revurdering = revurderingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/${revurderingTilAttestering.id}/tilAttestering",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{ "fritekstTilBrev": "Friteksten", "skalFøreTilBrevutsending": "true" }""")
            }.apply {
                status shouldBe HttpStatusCode.OK
                val actualResponse = objectMapper.readValue<TilAttesteringJson>(bodyAsText())
                actualResponse.id shouldBe revurderingTilAttestering.id.toString()
                actualResponse.status shouldBe RevurderingsStatus.TIL_ATTESTERING_INGEN_ENDRING
                actualResponse.fritekstTilBrev shouldBe "Friteksten"
                actualResponse.skalFøreTilBrevutsending shouldBe true
            }
        }
    }

    @Test
    fun `fant ikke revurdering`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeSendeRevurderingTilAttestering.FantIkkeRevurdering,
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
            error = KunneIkkeSendeRevurderingTilAttestering.UgyldigTilstand(
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
    fun `fant ikke aktør id`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeSendeRevurderingTilAttestering.FantIkkeAktørId,
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
            error = KunneIkkeSendeRevurderingTilAttestering.KunneIkkeOppretteOppgave,
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
    fun `feilutbetaling støttes ikke`() {
        shouldMapErrorCorrectly(
            error = KunneIkkeSendeRevurderingTilAttestering.FeilutbetalingStøttesIkke,
            expectedStatusCode = HttpStatusCode.InternalServerError,
            expectedJsonResponse = """
                {
                    "message":"Feilutbetalinger støttes ikke",
                    "code":"feilutbetalinger_støttes_ikke"
                }
            """.trimIndent(),
        )
    }

    private fun shouldMapErrorCorrectly(
        error: KunneIkkeSendeRevurderingTilAttestering,
        expectedStatusCode: HttpStatusCode,
        expectedJsonResponse: String,
    ) {
        val revurderingServiceMock = mock<RevurderingService> {
            on { sendTilAttestering(any()) } doReturn error.left()
        }

        testApplication {
            application {
                testSusebakover(services = testServices.copy(revurdering = revurderingServiceMock))
            }
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/$revurderingId/tilAttestering",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody("""{ "fritekstTilBrev": "Dette er friteksten" }""")
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
