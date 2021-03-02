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
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegEtterUtbetaling
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak
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
    companion object {

        internal val sakId = UUID.randomUUID()
        internal val requestPath = "$sakPath/$sakId/revurderinger"
        internal val testServices = TestServicesBuilder.services()
        internal val periode = Periode.create(fraOgMed = 1.januar(2020), tilOgMed = 31.desember(2020))

        internal val vedtak = Vedtak.InnvilgetStønad.fromSøknadsbehandling(
            Søknadsbehandling.Iverksatt.Innvilget(
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

                        delerBolig = true,
                        ektemakeEllerSamboerUførFlyktning = true,
                        begrunnelse = null
                    ),
                    ektefelle = Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle
                ),
                fnr = FnrGenerator.random(),
                beregning = TestBeregning,
                simulering = mock(),
                saksbehandler = NavIdentBruker.Saksbehandler("saks"),
                attestering = Attestering.Iverksatt(NavIdentBruker.Attestant("attestant")),
                utbetalingId = UUID30.randomUUID(),
                eksterneIverksettingsteg = EksterneIverksettingsstegEtterUtbetaling.VenterPåKvittering
            )
        )
    }

    @Test
    fun `kan opprette beregning og simulering for revurdering`() {
        val månedsberegninger = listOf<Månedsberegning>(
            mock {
                on { getSumYtelse() } doReturn 1
                on { getPeriode() } doReturn TestBeregning.getPeriode()
                on { getSats() } doReturn TestBeregning.getSats()
            }
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
            on { getPeriode() } doReturn TestBeregning.getPeriode()
        }

        val beregnetRevurdering = OpprettetRevurdering(
            id = UUID.randomUUID(),
            periode = TestBeregning.getPeriode(),
            opprettet = Tidspunkt.now(),
            tilRevurdering = vedtak.copy(beregning = beregning),
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "")
        ).beregn(
            listOf(
                FradragFactory.ny(
                    type = Fradragstype.BidragEtterEkteskapsloven,
                    månedsbeløp = 12.0,
                    periode = TestBeregning.getMånedsberegninger()[0].getPeriode(),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                )
            )
        ).orNull()!!

        val simulertRevurdering = when (beregnetRevurdering) {
            is BeregnetRevurdering.Innvilget -> {
                beregnetRevurdering.toSimulert(
                    Simulering(
                        gjelderId = vedtak.behandling.fnr,
                        gjelderNavn = "Test",
                        datoBeregnet = LocalDate.now(),
                        nettoBeløp = 0,
                        periodeList = listOf()
                    )
                )
            }
            is BeregnetRevurdering.Avslag -> throw RuntimeException("Revurderingen må ha en endring på minst 10 prosent")
        }

        val revurderingServiceMock = mock<RevurderingService> {
            on { beregnOgSimuler(any(), any(), any()) } doReturn simulertRevurdering.right()
        }

        val periode = Periode.create(fraOgMed = 1.januar(2020), tilOgMed = 31.desember(2020))

        withTestApplication({
            testSusebakover(services = testServices.copy(revurdering = revurderingServiceMock))
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
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = ""),
            beregning = TestBeregning,
            simulering = Simulering(
                gjelderId = vedtak.behandling.fnr,
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
            testSusebakover(services = testServices.copy(revurdering = revurderingServiceMock))
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
