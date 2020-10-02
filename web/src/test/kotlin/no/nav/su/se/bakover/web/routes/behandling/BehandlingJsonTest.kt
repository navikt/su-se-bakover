package no.nav.su.se.bakover.web.routes.behandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.web.routes.behandling.BeregningJsonTest.Companion.beregning
import no.nav.su.se.bakover.web.routes.behandling.BeregningJsonTest.Companion.expectedBeregningJson
import no.nav.su.se.bakover.web.routes.søknad.SøknadJsonTest.Companion.søknad
import no.nav.su.se.bakover.web.routes.søknad.SøknadJsonTest.Companion.søknadJsonString
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.format.DateTimeFormatter
import java.util.UUID

internal class BehandlingJsonTest {

    companion object {
        private val behandlingId = UUID.randomUUID()
        private val vv1id = UUID.randomUUID()
        private val vv2id = UUID.randomUUID()
        private val sakId = UUID.randomUUID()

        internal val behandling = Behandling(
            id = behandlingId,
            behandlingsinformasjon = Behandlingsinformasjon(
                uførhet = Behandlingsinformasjon.Uførhet(
                    status = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
                    uføregrad = 20,
                    forventetInntekt = 10
                ),
                flyktning = Behandlingsinformasjon.Flyktning(
                    status = Behandlingsinformasjon.Flyktning.Status.VilkårOppfylt,
                    begrunnelse = null
                ),
                lovligOpphold = Behandlingsinformasjon.LovligOpphold(
                    status = Behandlingsinformasjon.LovligOpphold.Status.VilkårOppfylt,
                    begrunnelse = null
                ),
                fastOppholdINorge = Behandlingsinformasjon.FastOppholdINorge(
                    status = Behandlingsinformasjon.FastOppholdINorge.Status.VilkårOppfylt,
                    begrunnelse = null
                ),
                oppholdIUtlandet = Behandlingsinformasjon.OppholdIUtlandet(
                    status = Behandlingsinformasjon.OppholdIUtlandet.Status.SkalHoldeSegINorge,
                    begrunnelse = null
                ),
                formue = Behandlingsinformasjon.Formue(
                    status = Behandlingsinformasjon.Formue.Status.VilkårOppfylt,
                    verdiIkkePrimærbolig = 0,
                    verdiKjøretøy = 0,
                    innskudd = 0,
                    verdipapir = 0,
                    pengerSkyldt = 0,
                    kontanter = 0,
                    depositumskonto = 0,
                    begrunnelse = null
                ),
                personligOppmøte = Behandlingsinformasjon.PersonligOppmøte(
                    status = Behandlingsinformasjon.PersonligOppmøte.Status.MøttPersonlig,
                    begrunnelse = null
                ),
                bosituasjon = Behandlingsinformasjon.Bosituasjon(
                    delerBolig = false,
                    delerBoligMed = null,
                    ektemakeEllerSamboerUnder67År = false,
                    ektemakeEllerSamboerUførFlyktning = false,
                    begrunnelse = null
                )
            ),
            søknad = søknad,
            beregning = beregning,
            attestant = Attestant("kjella"),
            saksbehandler = Saksbehandler("pro-saksbehandler"),
            sakId = sakId
        )

        //language=JSON
        internal val behandlingJsonString =
            """
        {
          "id": "$behandlingId",
          "opprettet": "${DateTimeFormatter.ISO_INSTANT.format(behandling.opprettet)}",
          "behandlingsinformasjon": {
                "uførhet": {
                    "status": "VilkårOppfylt",
                    "uføregrad": 20,
                    "forventetInntekt": 10
                },
                "flyktning": {
                    "status" : "VilkårOppfylt",
                    "begrunnelse" : null
                },
                "lovligOpphold": {
                    "status" : "VilkårOppfylt",
                    "begrunnelse" : null
                },
                "fastOppholdINorge": {
                    "status": "VilkårOppfylt",
                    "begrunnelse": null
                },
                "oppholdIUtlandet": {
                    "status": "SkalHoldeSegINorge",
                    "begrunnelse": null
                },
                "formue": {
                    "status": "VilkårOppfylt",
                    "verdiIkkePrimærbolig": 0,
                    "verdiKjøretøy": 0,
                    "innskudd": 0,
                    "verdipapir": 0,
                    "pengerSkyldt": 0,
                    "kontanter": 0,
                    "depositumskonto": 0,
                    "begrunnelse": null
                },
                "personligOppmøte": {
                    "status": "MøttPersonlig",
                    "begrunnelse": null
                },
                "bosituasjon": {
                    "delerBolig": false,
                    "delerBoligMed": null,
                    "ektemakeEllerSamboerUnder67År": false,
                    "ektemakeEllerSamboerUførFlyktning": false,
                    "begrunnelse": null
                },
                "utledetSats": "HØY"
          },
          "søknad": $søknadJsonString,
          "beregning": $expectedBeregningJson,
          "status": "OPPRETTET",
          "utbetaling": null,
          "attestant" : "kjella",
          "saksbehandler" : "pro-saksbehandler",
          "sakId": "$sakId",
          "hendelser": null
        }
            """.trimIndent()
    }

    @Test
    fun `should serialize to json string`() {
        JSONAssert.assertEquals(behandlingJsonString, serialize(behandling.toJson()), true)
    }

    @Test
    fun `should deserialize json string`() {
        deserialize<BehandlingJson>(behandlingJsonString) shouldBe behandling.toJson()
    }

    @Test
    fun nullables() {
        val behandlingWithNulls = Behandling(
            id = behandlingId,
            behandlingsinformasjon = Behandlingsinformasjon(),
            søknad = søknad,
            sakId = sakId
        )
        val opprettetTidspunkt = DateTimeFormatter.ISO_INSTANT.format(behandlingWithNulls.opprettet)

        //language=JSON
        val expectedNullsJson =
            """
        {
          "id": "$behandlingId",
          "behandlingsinformasjon": {
            "uførhet": null,
            "flyktning": null,
            "lovligOpphold": null,
            "fastOppholdINorge": null,
            "oppholdIUtlandet": null,
            "formue": null,
            "personligOppmøte": null,
            "bosituasjon": null,
            "utledetSats": null
          },
          "søknad": $søknadJsonString,
          "beregning": null,
          "status": "OPPRETTET",
          "utbetaling": null,
          "opprettet": "$opprettetTidspunkt",
          "attestant": null,
          "saksbehandler": null,
          "sakId": "$sakId",
          "hendelser": null
        }
        """

        JSONAssert.assertEquals(expectedNullsJson, serialize(behandlingWithNulls.toJson()), true)
        deserialize<BehandlingJson>(expectedNullsJson) shouldBe behandlingWithNulls.toJson()
    }
}
