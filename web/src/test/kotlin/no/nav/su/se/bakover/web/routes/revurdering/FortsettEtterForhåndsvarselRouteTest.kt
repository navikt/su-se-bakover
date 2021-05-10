package no.nav.su.se.bakover.web.routes.revurdering

import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.BeslutningEtterForhåndsvarsling
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.søknadsbehandling.TestBeregning
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.LocalDate
import java.util.UUID

internal class FortsettEtterForhåndsvarselRouteTest {

    private val revurderingId = UUID.randomUUID()

    @Test
    fun `uautoriserte kan ikke sende revurdering til attestering`() {
        withTestApplication(
            {
                testSusebakover()
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "${RevurderingRoutesTestData.requestPath}/$revurderingId/fortsettEtterForhåndsvarsel",
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
    fun `fortsetter med andre opplysninger`() {
        val simulertRevurdering = SimulertRevurdering.Innvilget(
            id = UUID.randomUUID(),
            periode = RevurderingRoutesTestData.periode,
            opprettet = Tidspunkt.now(),
            tilRevurdering = RevurderingRoutesTestData.vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = ""),
            beregning = TestBeregning,
            simulering = Simulering(
                gjelderId = Fnr(fnr = "12345678901"),
                gjelderNavn = "navn",
                datoBeregnet = LocalDate.now(),
                nettoBeløp = 0,
                periodeList = listOf(),
            ),
            oppgaveId = OppgaveId("OppgaveId"),
            fritekstTilBrev = "Friteksten",
            revurderingsårsak = Revurderingsårsak(
                Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
            ),
            forhåndsvarsel = Forhåndsvarsel.SkalForhåndsvarsles.Besluttet(
                journalpostId = JournalpostId("lol"),
                brevbestillingId = null,
                valg = BeslutningEtterForhåndsvarsling.FortsettMedAndreOpplysninger,
                begrunnelse = "begrunnelse"

            ),
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
            informasjonSomRevurderes = emptyMap(),
        )

        val revurderingServiceMock = mock<RevurderingService> {
            on { fortsettEtterForhåndsvarsling(any()) } doReturn simulertRevurdering.right()
        }

        withTestApplication(
            {
                testSusebakover(services = RevurderingRoutesTestData.testServices.copy(revurdering = revurderingServiceMock))
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "${RevurderingRoutesTestData.requestPath}/${simulertRevurdering.id}/fortsettEtterForhåndsvarsel",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    //language=json
                    """
                        {
                          "fritekstTilBrev": "Friteksten",
                          "valg": "FORTSETT_MED_ANDRE_OPPLYSNINGER",
                          "begrunnelse": "begrunnelse"
                        }
                    """.trimIndent(),
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.OK
                val actualResponse = objectMapper.readValue<SimulertRevurderingJson>(response.content!!)
                actualResponse.id shouldBe simulertRevurdering.id.toString()
                actualResponse.status shouldBe RevurderingsStatus.SIMULERT_INNVILGET
                actualResponse.fritekstTilBrev shouldBe "Friteksten"
                actualResponse.forhåndsvarsel shouldBe ForhåndsvarselJson.SkalVarslesBesluttet(
                    begrunnelse = "begrunnelse",
                    beslutningEtterForhåndsvarsling = BeslutningEtterForhåndsvarsling.FortsettMedAndreOpplysninger,
                )
            }
        }
    }

    @Test
    fun `fortsetter med samme opplysninger`() {
        val simulertRevurdering = SimulertRevurdering.Innvilget(
            id = UUID.randomUUID(),
            periode = RevurderingRoutesTestData.periode,
            opprettet = Tidspunkt.now(),
            tilRevurdering = RevurderingRoutesTestData.vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = ""),
            beregning = TestBeregning,
            simulering = Simulering(
                gjelderId = Fnr(fnr = "12345678901"),
                gjelderNavn = "navn",
                datoBeregnet = LocalDate.now(),
                nettoBeløp = 0,
                periodeList = listOf(),
            ),
            oppgaveId = OppgaveId("OppgaveId"),
            fritekstTilBrev = "Friteksten",
            revurderingsårsak = Revurderingsårsak(
                Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
            ),
            forhåndsvarsel = Forhåndsvarsel.SkalForhåndsvarsles.Besluttet(
                journalpostId = JournalpostId("lol"),
                brevbestillingId = null,
                valg = BeslutningEtterForhåndsvarsling.FortsettSammeOpplysninger,
                begrunnelse = "begrunnelse"
            ),
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
            informasjonSomRevurderes = emptyMap(),
        )

        val revurderingServiceMock = mock<RevurderingService> {
            on { fortsettEtterForhåndsvarsling(any()) } doReturn simulertRevurdering.right()
        }

        withTestApplication(
            {
                testSusebakover(services = RevurderingRoutesTestData.testServices.copy(revurdering = revurderingServiceMock))
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "${RevurderingRoutesTestData.requestPath}/${simulertRevurdering.id}/fortsettEtterForhåndsvarsel",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    //language=json
                    """
                        {
                          "fritekstTilBrev": "Friteksten",
                          "valg": "FORTSETT_MED_SAMME_OPPLYSNINGER",
                          "begrunnelse": "begrunnelse"
                        }
                    """.trimIndent(),
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.OK
                val actualResponse = objectMapper.readValue<SimulertRevurderingJson>(response.content!!)
                actualResponse.id shouldBe simulertRevurdering.id.toString()
                actualResponse.status shouldBe RevurderingsStatus.SIMULERT_INNVILGET
                actualResponse.fritekstTilBrev shouldBe "Friteksten"
                actualResponse.forhåndsvarsel shouldBe ForhåndsvarselJson.SkalVarslesBesluttet(
                    begrunnelse = "begrunnelse",
                    beslutningEtterForhåndsvarsling = BeslutningEtterForhåndsvarsling.FortsettSammeOpplysninger,
                )
            }
        }
    }

    @Test
    fun `avslutter uten endring`() {
        val simulertRevurdering = SimulertRevurdering.Innvilget(
            id = UUID.randomUUID(),
            periode = RevurderingRoutesTestData.periode,
            opprettet = Tidspunkt.now(),
            tilRevurdering = RevurderingRoutesTestData.vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = ""),
            beregning = TestBeregning,
            simulering = Simulering(
                gjelderId = Fnr(fnr = "12345678901"),
                gjelderNavn = "navn",
                datoBeregnet = LocalDate.now(),
                nettoBeløp = 0,
                periodeList = listOf(),
            ),
            oppgaveId = OppgaveId("OppgaveId"),
            fritekstTilBrev = "Friteksten",
            revurderingsårsak = Revurderingsårsak(
                Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
                Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
            ),
            forhåndsvarsel = Forhåndsvarsel.SkalForhåndsvarsles.Besluttet(
                journalpostId = JournalpostId("lol"),
                brevbestillingId = null,
                valg = BeslutningEtterForhåndsvarsling.AvsluttUtenEndringer,
                begrunnelse = "begrunnelse"
            ),
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
            informasjonSomRevurderes = emptyMap(),
        )

        val revurderingServiceMock = mock<RevurderingService> {
            on { fortsettEtterForhåndsvarsling(any()) } doReturn simulertRevurdering.right()
        }

        withTestApplication(
            {
                testSusebakover(services = RevurderingRoutesTestData.testServices.copy(revurdering = revurderingServiceMock))
            },
        ) {
            defaultRequest(
                HttpMethod.Post,
                "${RevurderingRoutesTestData.requestPath}/${simulertRevurdering.id}/fortsettEtterForhåndsvarsel",
                listOf(Brukerrolle.Saksbehandler),
            ) {
                setBody(
                    //language=json
                    """
                        {
                          "fritekstTilBrev": "Friteksten",
                          "valg": "AVSLUTT_UTEN_ENDRINGER",
                          "begrunnelse": "begrunnelse"
                        }
                    """.trimIndent(),
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.OK
                val actualResponse = objectMapper.readValue<SimulertRevurderingJson>(response.content!!)
                actualResponse.id shouldBe simulertRevurdering.id.toString()
                actualResponse.status shouldBe RevurderingsStatus.SIMULERT_INNVILGET
                actualResponse.fritekstTilBrev shouldBe "Friteksten"
                actualResponse.forhåndsvarsel shouldBe ForhåndsvarselJson.SkalVarslesBesluttet(
                    begrunnelse = "begrunnelse",
                    beslutningEtterForhåndsvarsling = BeslutningEtterForhåndsvarsling.AvsluttUtenEndringer,
                )
            }
        }
    }
}
