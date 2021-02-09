package no.nav.su.se.bakover.web.routes.revurdering

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
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.argThat
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.routes.behandling.TestBeregning
import no.nav.su.se.bakover.web.routes.sak.sakPath
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class RevurderingRoutesKtTest {
    private val sakId = UUID.randomUUID()
    private val requestPath = "$sakPath/$sakId/revurderinger"
    private val services = TestServicesBuilder.services()
    private val periode = Periode.create(fraOgMed = 1.januar(2020), tilOgMed = 31.desember(2020))

    private val behandling = Søknadsbehandling.Iverksatt.Innvilget(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(),
        sakId = sakId,
        saksnummer = Saksnummer(1569),
        søknad = Søknad.Journalført.MedOppgave(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            sakId = sakId,
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            journalpostId = JournalpostId(value = ""),
            oppgaveId = OppgaveId(value = "")

        ),
        oppgaveId = OppgaveId(value = ""),
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().copy(
            bosituasjon = Behandlingsinformasjon.Bosituasjon(
                epsAlder = 55,
                delerBolig = true,
                ektemakeEllerSamboerUførFlyktning = true,
                begrunnelse = null
            )
        ),
        fnr = FnrGenerator.random(),
        beregning = TestBeregning,
        simulering = mock(),
        saksbehandler = NavIdentBruker.Saksbehandler("saks"),
        attestering = Attestering.Iverksatt(NavIdentBruker.Attestant("attestant")),
        utbetalingId = UUID30.randomUUID(),
        eksterneIverksettingsteg = Søknadsbehandling.Iverksatt.Innvilget.EksterneIverksettingsteg.VenterPåKvittering
    )

    @Test
    fun `uautoriserte kan ej opprette revurdering `() {
        withTestApplication({
            testSusebakover()
        }) {
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/opprett",
                listOf(Brukerrolle.Veileder)
            ) {
                setBody("""{ "fraOgMed": "${periode.getFraOgMed()}", "tilOgMed": "${periode.getTilOgMed()}"}""")
            }.apply {
                response.status() shouldBe HttpStatusCode.Forbidden
            }
        }
    }

    @Test
    fun `kan opprette revurdering`() {
        val opprettetRevurdering = OpprettetRevurdering(
            id = UUID.randomUUID(),
            periode = periode,
            opprettet = Tidspunkt.now(),
            tilRevurdering = behandling,
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "")

        )
        val revurderingServiceMock = mock<RevurderingService> {
            on { opprettRevurdering(any(), any(), any()) } doReturn opprettetRevurdering.right()
        }

        withTestApplication({
            testSusebakover(services = services.copy(revurdering = revurderingServiceMock))
        }) {
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/opprett",
                listOf(Brukerrolle.Saksbehandler)
            ) {
                setBody("""{ "fraOgMed": "${periode.getFraOgMed()}", "tilOgMed": "${periode.getTilOgMed()}"}""")
            }.apply {
                response.status() shouldBe HttpStatusCode.Created
                response.content
                val actualResponse = objectMapper.readValue<OpprettetRevurderingJson>(response.content!!)
                actualResponse.id shouldBe opprettetRevurdering.id.toString()
                actualResponse.status shouldBe RevurderingsStatus.OPPRETTET
            }
        }
    }

    @Test
    fun `kan opprette beregning og simulering for revurdering`() {
        val simulertRevurdering = OpprettetRevurdering(
            id = UUID.randomUUID(),
            periode = periode,
            opprettet = Tidspunkt.now(),
            tilRevurdering = behandling,
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "")
        ).beregn(emptyList())
            .toSimulert(
                Simulering(
                    gjelderId = behandling.fnr,
                    gjelderNavn = "Test",
                    datoBeregnet = LocalDate.now(),
                    nettoBeløp = 0,
                    periodeList = listOf()
                )
            )

        val revurderingServiceMock = mock<RevurderingService> {
            on { beregnOgSimuler(any(), any(), any()) } doReturn simulertRevurdering.right()
        }

        val periode = Periode.create(fraOgMed = 1.januar(2020), tilOgMed = 31.desember(2020))

        withTestApplication({
            testSusebakover(services = services.copy(revurdering = revurderingServiceMock))
        }) {
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/${simulertRevurdering.id}/beregnOgSimuler",
                listOf(Brukerrolle.Saksbehandler)
            ) {
                setBody(
                    """{
                    "periode": { "fraOgMed": "${periode.getFraOgMed()}", "tilOgMed": "${periode.getTilOgMed()}"},
                    "fradrag": []
                    } 
                    """.trimIndent()
                )
            }.apply {
                response.status() shouldBe HttpStatusCode.Created
                val actualResponse = objectMapper.readValue<SimulertRevurderingJson>(response.content!!)
                verify(revurderingServiceMock).beregnOgSimuler(
                    argThat { it shouldBe simulertRevurdering.id },
                    argThat { it shouldBe NavIdentBruker.Saksbehandler("Z990Lokal") },
                    argThat { it shouldBe emptyList() },
                )
                verifyNoMoreInteractions(revurderingServiceMock)
                actualResponse.id shouldBe simulertRevurdering.id.toString()
                actualResponse.status shouldBe RevurderingsStatus.SIMULERT
            }
        }
    }

    @Test
    fun `send til attestering`() {
        val revurderingTilAttestering = RevurderingTilAttestering(
            id = UUID.randomUUID(),
            periode = periode,
            opprettet = Tidspunkt.now(),
            tilRevurdering = behandling,
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = ""),
            beregning = TestBeregning,
            simulering = Simulering(
                gjelderId = behandling.fnr,
                gjelderNavn = "Test",
                datoBeregnet = LocalDate.now(),
                nettoBeløp = 0,
                periodeList = listOf()
            ),
            oppgaveId = OppgaveId("OppgaveId")
        )

        val revurderingServiceMock = mock<RevurderingService> {
            on { sendTilAttestering(any(), any()) } doReturn revurderingTilAttestering.right()
        }

        withTestApplication({
            testSusebakover(services = services.copy(revurdering = revurderingServiceMock))
        }) {
            defaultRequest(
                HttpMethod.Post,
                "$requestPath/${revurderingTilAttestering.id}/tilAttestering",
                listOf(Brukerrolle.Saksbehandler)
            ).apply {
                response.status() shouldBe HttpStatusCode.OK
                val actualResponse = objectMapper.readValue<TilAttesteringJson>(response.content!!)
                actualResponse.id shouldBe revurderingTilAttestering.id.toString()
                actualResponse.status shouldBe RevurderingsStatus.TIL_ATTESTERING
            }
        }
    }
}
