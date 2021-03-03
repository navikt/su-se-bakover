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
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.web.routes.behandling.TestBeregning
import no.nav.su.se.bakover.web.routes.behandling.beregning.toJson
import no.nav.su.se.bakover.web.routes.behandling.toJson
import no.nav.su.se.bakover.web.routes.revurdering.RevurderingRoutesTestData.innvilgetSøknadsbehandling
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class RevurderingJsonTest {

    @Test
    fun `should serialize and deserialize OpprettetRevurdering`() {
        val id = UUID.randomUUID()
        val opprettet = Tidspunkt.now()

        val revurdering = OpprettetRevurdering(
            id = id,
            periode = Periode.create(1.januar(2020), 31.desember(2020)),
            opprettet = opprettet,
            tilRevurdering = innvilgetSøknadsbehandling,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            oppgaveId = OppgaveId("oppgaveid"),
        )

        val revurderingJson = """
            {
            "id": "$id",
            "opprettet": "$opprettet",
            "tilRevurdering": ${serialize(innvilgetSøknadsbehandling.toJson())},
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
            tilRevurdering = innvilgetSøknadsbehandling,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            beregning = beregning,
            oppgaveId = OppgaveId("oppgaveid")
        )

        val revurderingJson = """
            {
            "id": "$id",
            "opprettet": "$opprettet",
            "tilRevurdering": ${serialize(innvilgetSøknadsbehandling.toJson())},
            "beregninger":
              {
                "beregning": ${serialize(innvilgetSøknadsbehandling.beregning.toJson())},
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
            tilRevurdering = innvilgetSøknadsbehandling,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            beregning = beregning,
            oppgaveId = OppgaveId("oppgaveid")
        )

        val revurderingJson = """
            {
            "id": "$id",
            "opprettet": "$opprettet",
            "tilRevurdering": ${serialize(innvilgetSøknadsbehandling.toJson())},
            "beregninger":
              {
                "beregning": ${serialize(innvilgetSøknadsbehandling.beregning.toJson())},
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
            tilRevurdering = innvilgetSøknadsbehandling,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            beregning = beregning,
            simulering = mock(),
            oppgaveId = OppgaveId("oppgaveid")
        )

        val revurderingJson = """
            {
            "id": "$id",
            "opprettet": "$opprettet",
            "tilRevurdering": ${serialize(innvilgetSøknadsbehandling.toJson())},
            "beregninger":
              {
                "beregning": ${serialize(innvilgetSøknadsbehandling.beregning.toJson())},
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
            tilRevurdering = innvilgetSøknadsbehandling,
            saksbehandler = NavIdentBruker.Saksbehandler("Petter"),
            beregning = beregning,
            simulering = mock(),
            oppgaveId = OppgaveId("OppgaveId")
        )

        val revurderingJson = """
            {
            "id": "$id",
            "opprettet": "$opprettet",
            "tilRevurdering": ${serialize(innvilgetSøknadsbehandling.toJson())},
            "beregninger":
              {
                "beregning": ${serialize(innvilgetSøknadsbehandling.beregning.toJson())},
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
