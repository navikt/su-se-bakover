package no.nav.su.se.bakover.web.routes.revurdering

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
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeBehovForTilbakekrevingUnderBehandling
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.service.revurdering.Forhåndsvarselhandling
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.vilkårsvurderingRevurderingIkkeVurdert
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.revurdering.forhåndsvarsel.BeslutningEtterForhåndsvarsling
import no.nav.su.se.bakover.web.routes.revurdering.forhåndsvarsel.ForhåndsvarselJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.TestBeregning
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class ForhåndsvarslingRouteTest {
    private val revurderingId = UUID.randomUUID()

    @Nested
    inner class `lagre forhåndsvarselvalg` {
        @Test
        fun `uautoriserte kan ikke forhåndsvarsle eller sende til attestering`() {
            testApplication {
                application {
                    testSusebakover()
                }
                defaultRequest(
                    HttpMethod.Post,
                    "${RevurderingRoutesTestData.requestPath}/$revurderingId/forhandsvarsel",
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
        fun `lagrer valget`() {
            val simulertRevurdering = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak()
                .second.copy(
                    forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
                    fritekstTilBrev = ""
                )

            val revurderingServiceMock = mock<RevurderingService> {
                on {
                    lagreOgSendForhåndsvarsel(
                        eq(simulertRevurdering.id),
                        any(),
                        eq(Forhåndsvarselhandling.INGEN_FORHÅNDSVARSEL),
                        eq(""),
                    )
                } doReturn simulertRevurdering.right()
            }

            testApplication {
                application {
                    testSusebakover(
                        services = RevurderingRoutesTestData.testServices.copy(revurdering = revurderingServiceMock),
                    )
                }
                defaultRequest(
                    HttpMethod.Post,
                    "${RevurderingRoutesTestData.requestPath}/${simulertRevurdering.id}/forhandsvarsel",
                    listOf(Brukerrolle.Saksbehandler),
                ) {
                    setBody(
                        //language=json
                        """
                        {
                          "forhåndsvarselhandling": "INGEN_FORHÅNDSVARSEL",
                          "fritekst": ""
                        }
                        """.trimIndent(),
                    )
                }.apply {
                    status shouldBe HttpStatusCode.OK
                    val actualResponse = objectMapper.readValue<SimulertRevurderingJson>(bodyAsText())
                    actualResponse.id shouldBe simulertRevurdering.id.toString()
                    actualResponse.status shouldBe RevurderingsStatus.SIMULERT_INNVILGET
                    actualResponse.fritekstTilBrev shouldBe ""
                    actualResponse.forhåndsvarsel shouldBe ForhåndsvarselJson.IngenForhåndsvarsel
                }
            }
        }
    }

    @Nested
    inner class `fortsett etter forhåndsvarsel` {
        @Test
        fun `uautoriserte kan ikke sende revurdering til attestering`() {
            testApplication {
                application {
                    testSusebakover()
                }
                defaultRequest(
                    HttpMethod.Post,
                    "${RevurderingRoutesTestData.requestPath}/$revurderingId/fortsettEtterForhåndsvarsel",
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
        fun `fortsetter med andre opplysninger`() {
            val simulertRevurdering = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
                forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.EndreGrunnlaget("begrunnelse"),
            ).second

            val revurderingServiceMock = mock<RevurderingService> {
                on { fortsettEtterForhåndsvarsling(any()) } doReturn simulertRevurdering.right()
            }

            testApplication {
                application {
                    testSusebakover(services = RevurderingRoutesTestData.testServices.copy(revurdering = revurderingServiceMock))
                }
                defaultRequest(
                    HttpMethod.Post,
                    "${RevurderingRoutesTestData.requestPath}/${simulertRevurdering.id}/fortsettEtterForhåndsvarsel",
                    listOf(Brukerrolle.Saksbehandler),
                ) {
                    // fritekstTilBrev skal bli ignorert i dette tilfellet
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
                    status shouldBe HttpStatusCode.OK
                    val actualResponse = objectMapper.readValue<SimulertRevurderingJson>(bodyAsText())
                    actualResponse.id shouldBe simulertRevurdering.id.toString()
                    actualResponse.status shouldBe RevurderingsStatus.SIMULERT_INNVILGET
                    actualResponse.fritekstTilBrev shouldBe ""
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
                forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag("begrunnelse"),
                grunnlagsdata = Grunnlagsdata.IkkeVurdert,
                vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
                informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
                attesteringer = Attesteringshistorikk.empty(),
                avkorting = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
                tilbakekrevingsbehandling = IkkeBehovForTilbakekrevingUnderBehandling,
            )

            val revurderingServiceMock = mock<RevurderingService> {
                on { fortsettEtterForhåndsvarsling(any()) } doReturn simulertRevurdering.right()
            }

            testApplication {
                application {
                    testSusebakover(services = RevurderingRoutesTestData.testServices.copy(revurdering = revurderingServiceMock))
                }
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
                    status shouldBe HttpStatusCode.OK
                    val actualResponse = objectMapper.readValue<SimulertRevurderingJson>(bodyAsText())
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
                forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.Avsluttet("begrunnelse"),
                grunnlagsdata = Grunnlagsdata.IkkeVurdert,
                vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
                informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
                attesteringer = Attesteringshistorikk.empty(),
                avkorting = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
                tilbakekrevingsbehandling = IkkeBehovForTilbakekrevingUnderBehandling,
            )

            val revurderingServiceMock = mock<RevurderingService> {
                on { fortsettEtterForhåndsvarsling(any()) } doReturn simulertRevurdering.right()
            }

            testApplication {
                application {
                    testSusebakover(services = RevurderingRoutesTestData.testServices.copy(revurdering = revurderingServiceMock))
                }
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
                    status shouldBe HttpStatusCode.OK
                    val actualResponse = objectMapper.readValue<SimulertRevurderingJson>(bodyAsText())
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
}
