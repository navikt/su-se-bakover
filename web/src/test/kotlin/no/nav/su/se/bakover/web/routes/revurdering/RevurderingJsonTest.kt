package no.nav.su.se.bakover.web.routes.revurdering

import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.web.routes.behandling.BehandlingTestUtils
import no.nav.su.se.bakover.web.routes.behandling.TestBeregning
import no.nav.su.se.bakover.web.routes.behandling.beregning.toJson
import no.nav.su.se.bakover.web.routes.behandling.toJson
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class RevurderingJsonTest {
    @Test
    fun `should serialize and deserialize OpprettetRevurdering`() {
        val id = UUID.randomUUID()
        val opprettet = Tidspunkt.now()
        val behandling = BehandlingTestUtils.nyBehandling()

        val revurdering = OpprettetRevurdering(
            id = id,
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            opprettet = opprettet,
            tilRevurdering = behandling,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter")
        )

        val revurderingJson = """
            {
            "id": "$id",
            "opprettet": "$opprettet",
            "tilRevurdering": ${serialize(behandling.toJson())},
            "status": "${RevurderingsStatus.OPPRETTET}"
            }
        """.trimIndent()

        JSONAssert.assertEquals(revurderingJson, serialize(revurdering.toJson()), true)
        deserialize<OpprettetRevurderingJson>(revurderingJson) shouldBe revurdering.toJson()
    }

    @Test
    fun `should serialize and deserialize SimulertRevurdering`() {
        val id = UUID.randomUUID()
        val opprettet = Tidspunkt.now()
        val behandling = BehandlingTestUtils.nyBehandling()
        val beregning = TestBeregning

        val revurdering = SimulertRevurdering(
            id = id,
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            opprettet = opprettet,
            tilRevurdering = behandling,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            beregning = beregning,
            simulering = mock()
        )

        val revurderingJson = """
            {
            "id": "$id",
            "opprettet": "$opprettet",
            "tilRevurdering": ${serialize(behandling.toJson())},
            "beregninger":
              {
                "beregning": ${serialize(behandling.beregning()!!.toJson())},
                "revurdert": ${serialize(beregning.toJson())}
              },
            "status": "${RevurderingsStatus.SIMULERT}",
            "saksbehandler": "Petter"
            }
        """.trimIndent()

        JSONAssert.assertEquals(revurderingJson, serialize(revurdering.toJson()), true)
        deserialize<SimulertRevurderingJson>(revurderingJson) shouldBe revurdering.toJson()
    }

    @Test
    fun `should serialize and deserialize RevurderingTilAttestering`() {
        val id = UUID.randomUUID()
        val opprettet = Tidspunkt.now()
        val behandling = BehandlingTestUtils.nyBehandling()
        val beregning = TestBeregning

        val revurdering = RevurderingTilAttestering(
            id = id,
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            opprettet = opprettet,
            tilRevurdering = behandling,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            beregning = beregning,
            simulering = mock(),
            oppgaveId = OppgaveId("OppgaveId")
        )

        val revurderingJson = """
            {
            "id": "$id",
            "opprettet": "$opprettet",
            "tilRevurdering": ${serialize(behandling.toJson())},
            "beregninger":
              {
                "beregning": ${serialize(behandling.beregning()!!.toJson())},
                "revurdert": ${serialize(beregning.toJson())}
              },
            "status": "${RevurderingsStatus.TIL_ATTESTERING}",
            "saksbehandler": "Petter"
            }
        """.trimIndent()

        JSONAssert.assertEquals(revurderingJson, serialize(revurdering.toJson()), true)
        deserialize<TilAttesteringJson>(revurderingJson) shouldBe revurdering.toJson()
    }
}
