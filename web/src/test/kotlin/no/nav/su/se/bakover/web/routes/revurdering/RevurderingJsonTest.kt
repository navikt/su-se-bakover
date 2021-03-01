package no.nav.su.se.bakover.web.routes.revurdering

import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegEtterUtbetaling
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.routes.behandling.TestBeregning
import no.nav.su.se.bakover.web.routes.behandling.beregning.toJson
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class RevurderingJsonTest {

    private val vedtak = Vedtak.InnvilgetStønad.fromSøknadsbehandling(
        Søknadsbehandling.Iverksatt.Innvilget(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            sakId = UUID.randomUUID(),
            saksnummer = Saksnummer(1569),
            søknad = Søknad.Journalført.MedOppgave(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                sakId = UUID.randomUUID(),
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
            eksterneIverksettingsteg = EksterneIverksettingsstegEtterUtbetaling.VenterPåKvittering
        )
    )

    @Test
    fun `should serialize and deserialize OpprettetRevurdering`() {
        val id = UUID.randomUUID()
        val opprettet = Tidspunkt.now()

        val revurdering = OpprettetRevurdering(
            id = id,
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            opprettet = opprettet,
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter")
        )

        val revurderingJson = """
            {
            "id": "$id",
            "opprettet": "$opprettet",
            "tilRevurdering": ${serialize(vedtak.toJson())},
            "status": "${RevurderingsStatus.OPPRETTET}",
            "periode": {
                "fraOgMed": "2020-01-01",
                "tilOgMed": "2020-12-31"
            },
            "saksbehandler": "Petter"
            }
        """.trimIndent()

        JSONAssert.assertEquals(revurderingJson, serialize(revurdering.toJson()), true)
        deserialize<OpprettetRevurderingJson>(revurderingJson) shouldBe revurdering.toJson()
    }

    @Test
    fun `should serialize and deserialize BeregnetInnvilget`() {
        val id = UUID.randomUUID()
        val opprettet = Tidspunkt.now()
        val beregning = TestBeregning

        val revurdering = BeregnetRevurdering.Innvilget(
            id = id,
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            opprettet = opprettet,
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            beregning = beregning,
        )

        val revurderingJson = """
            {
            "id": "$id",
            "opprettet": "$opprettet",
            "tilRevurdering": ${serialize(vedtak.toJson())},
            "beregninger":
              {
                "beregning": ${serialize(vedtak.beregning.toJson())},
                "revurdert": ${serialize(beregning.toJson())}
              },
            "status": "${RevurderingsStatus.BEREGNET_INNVILGET}",
            "saksbehandler": "Petter",
            "periode": {
                "fraOgMed": "2020-01-01",
                "tilOgMed": "2020-12-31"
            }
            }
        """.trimIndent()

        JSONAssert.assertEquals(revurderingJson, serialize(revurdering.toJson()), true)
        deserialize<BeregnetRevurderingJson.Innvilget>(revurderingJson) shouldBe revurdering.toJson()
    }

    @Test
    fun `should serialize and deserialize BeregnetAvslag`() {
        val id = UUID.randomUUID()
        val opprettet = Tidspunkt.now()
        val beregning = TestBeregning

        val revurdering = BeregnetRevurdering.Avslag(
            id = id,
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            opprettet = opprettet,
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            beregning = beregning,
        )

        val revurderingJson = """
            {
            "id": "$id",
            "opprettet": "$opprettet",
            "tilRevurdering": ${serialize(vedtak.toJson())},
            "beregninger":
              {
                "beregning": ${serialize(vedtak.beregning.toJson())},
                "revurdert": ${serialize(beregning.toJson())}
              },
            "status": "${RevurderingsStatus.BEREGNET_AVSLAG}",
            "saksbehandler": "Petter",
            "periode": {
                "fraOgMed": "2020-01-01",
                "tilOgMed": "2020-12-31"
            }
            }
        """.trimIndent()

        JSONAssert.assertEquals(revurderingJson, serialize(revurdering.toJson()), true)
        deserialize<BeregnetRevurderingJson.Avslag>(revurderingJson) shouldBe revurdering.toJson()
    }

    @Test
    fun `should serialize and deserialize SimulertRevurdering`() {
        val id = UUID.randomUUID()
        val opprettet = Tidspunkt.now()
        val beregning = TestBeregning

        val revurdering = SimulertRevurdering(
            id = id,
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            opprettet = opprettet,
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            beregning = beregning,
            simulering = mock()
        )

        val revurderingJson = """
            {
            "id": "$id",
            "opprettet": "$opprettet",
            "tilRevurdering": ${serialize(vedtak.toJson())},
            "beregninger":
              {
                "beregning": ${serialize(vedtak.beregning.toJson())},
                "revurdert": ${serialize(beregning.toJson())}
              },
            "status": "${RevurderingsStatus.SIMULERT}",
            "saksbehandler": "Petter",
            "periode": {
                "fraOgMed": "2020-01-01",
                "tilOgMed": "2020-12-31"
            }
            }
        """.trimIndent()

        JSONAssert.assertEquals(revurderingJson, serialize(revurdering.toJson()), true)
        deserialize<SimulertRevurderingJson>(revurderingJson) shouldBe revurdering.toJson()
    }

    @Test
    fun `should serialize and deserialize RevurderingTilAttestering`() {
        val id = UUID.randomUUID()
        val opprettet = Tidspunkt.now()
        val beregning = TestBeregning

        val revurdering = RevurderingTilAttestering(
            id = id,
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            opprettet = opprettet,
            tilRevurdering = vedtak,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            beregning = beregning,
            simulering = mock(),
            oppgaveId = OppgaveId("OppgaveId")
        )

        val revurderingJson = """
            {
            "id": "$id",
            "opprettet": "$opprettet",
            "tilRevurdering": ${serialize(vedtak.toJson())},
            "beregninger":
              {
                "beregning": ${serialize(vedtak.beregning.toJson())},
                "revurdert": ${serialize(beregning.toJson())}
              },
            "status": "${RevurderingsStatus.TIL_ATTESTERING}",
            "saksbehandler": "Petter",
            "periode": {
                "fraOgMed": "2020-01-01",
                "tilOgMed": "2020-12-31"
            }
            }
        """.trimIndent()

        JSONAssert.assertEquals(revurderingJson, serialize(revurdering.toJson()), true)
        deserialize<TilAttesteringJson>(revurderingJson) shouldBe revurdering.toJson()
    }
}
