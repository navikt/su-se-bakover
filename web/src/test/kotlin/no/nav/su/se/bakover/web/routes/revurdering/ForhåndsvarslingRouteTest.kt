package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.søknadsbehandling.TestBeregning
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class ForhåndsvarslingRouteTest {

    private val revurderingId = UUID.randomUUID()

    @Test
    fun `uautoriserte kan ikke forhåndsvarsle eller sende til attestering`() {
        withTestApplication(
            {
                testSusebakover()
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "${RevurderingRoutesTestData.requestPath}/$revurderingId/forhandsvarsleEllerSendTilAttestering",
                listOf(Brukerrolle.Veileder),
            ).apply {
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
    fun `sender forhådsvarsling`() {
        val simulertRevurdering = SimulertRevurdering.Innvilget(
            id = UUID.randomUUID(),
            periode = RevurderingRoutesTestData.periode,
            opprettet = fixedTidspunkt,
            tilRevurdering = RevurderingRoutesTestData.vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandler"),
            beregning = TestBeregning,
            simulering = Simulering(
                gjelderId = Fnr(fnr = "12345678901"),
                gjelderNavn = "navn",
                datoBeregnet = fixedLocalDate,
                nettoBeløp = 0,
                periodeList = listOf(),
            ),
            oppgaveId = OppgaveId("OppgaveId"),
            fritekstTilBrev = "Friteksten",
            revurderingsårsak = Revurderingsårsak(
                Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
            ),
            forhåndsvarsel = Forhåndsvarsel.SkalForhåndsvarsles.Sendt,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            attesteringer = Attesteringshistorikk.empty(),
        )

        val revurderingServiceMock = mock<RevurderingService> {
            on { forhåndsvarsleEllerSendTilAttestering(any(), any(), any(), any()) } doReturn simulertRevurdering.right()
        }

        withTestApplication(
            {
                testSusebakover(services = RevurderingRoutesTestData.testServices.copy(revurdering = revurderingServiceMock))
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "${RevurderingRoutesTestData.requestPath}/${simulertRevurdering.id}/forhandsvarsleEllerSendTilAttestering",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    //language=json
                    """
                        {
                          "revurderingshandling": "FORHÅNDSVARSLE",
                          "fritekst": "Friteksten"
                        }
                    """.trimIndent(),
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.OK
                val actualResponse = objectMapper.readValue<SimulertRevurderingJson>(response.content!!)
                actualResponse.id shouldBe simulertRevurdering.id.toString()
                actualResponse.status shouldBe RevurderingsStatus.SIMULERT_INNVILGET
                actualResponse.fritekstTilBrev shouldBe "Friteksten"
                actualResponse.forhåndsvarsel shouldBe ForhåndsvarselJson.SkalVarslesSendt
            }
        }
    }

    @Test
    fun `sender til attestering`() {
        val simulertRevurdering = SimulertRevurdering.Innvilget(
            id = UUID.randomUUID(),
            periode = RevurderingRoutesTestData.periode,
            opprettet = fixedTidspunkt,
            tilRevurdering = RevurderingRoutesTestData.vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandler"),
            beregning = TestBeregning,
            simulering = Simulering(
                gjelderId = Fnr(fnr = "12345678901"),
                gjelderNavn = "navn",
                datoBeregnet = fixedLocalDate,
                nettoBeløp = 0,
                periodeList = listOf(),
            ),
            oppgaveId = OppgaveId("OppgaveId"),
            fritekstTilBrev = "Friteksten",
            revurderingsårsak = Revurderingsårsak(
                Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
            ),
            forhåndsvarsel = Forhåndsvarsel.IngenForhåndsvarsel,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            attesteringer = Attesteringshistorikk.empty(),
        )

        val revurderingServiceMock = mock<RevurderingService> {
            on { forhåndsvarsleEllerSendTilAttestering(any(), any(), any(), any()) } doReturn simulertRevurdering.right()
        }

        withTestApplication(
            {
                testSusebakover(services = RevurderingRoutesTestData.testServices.copy(revurdering = revurderingServiceMock))
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "${RevurderingRoutesTestData.requestPath}/${simulertRevurdering.id}/forhandsvarsleEllerSendTilAttestering",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    //language=json
                    """
                        {
                          "revurderingshandling": "SEND_TIL_ATTESTERING",
                          "fritekst": "Friteksten"
                        }
                    """.trimIndent(),
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.OK
                val actualResponse = objectMapper.readValue<SimulertRevurderingJson>(response.content!!)
                actualResponse.id shouldBe simulertRevurdering.id.toString()
                actualResponse.status shouldBe RevurderingsStatus.SIMULERT_INNVILGET
                actualResponse.fritekstTilBrev shouldBe "Friteksten"
                actualResponse.forhåndsvarsel shouldBe ForhåndsvarselJson.IngenForhåndsvarsel
            }
        }
    }
}
