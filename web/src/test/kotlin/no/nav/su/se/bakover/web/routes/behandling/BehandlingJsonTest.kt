package no.nav.su.se.bakover.web.routes.behandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.web.FnrGenerator
import no.nav.su.se.bakover.web.routes.behandling.BehandlingTestUtils.behandlingFactory
import no.nav.su.se.bakover.web.routes.behandling.BehandlingTestUtils.behandlingId
import no.nav.su.se.bakover.web.routes.behandling.BehandlingTestUtils.journalførtSøknadMedOppgave
import no.nav.su.se.bakover.web.routes.behandling.BehandlingTestUtils.nyBehandling
import no.nav.su.se.bakover.web.routes.behandling.BehandlingTestUtils.oppgaveId
import no.nav.su.se.bakover.web.routes.behandling.BehandlingTestUtils.sakId
import no.nav.su.se.bakover.web.routes.behandling.BehandlingTestUtils.saksnummer
import no.nav.su.se.bakover.web.routes.behandling.BeregningJsonTest.Companion.expectedBeregningJson
import no.nav.su.se.bakover.web.routes.søknad.SøknadJsonTest.Companion.søknadJsonString
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.format.DateTimeFormatter

internal class BehandlingJsonTest {

    companion object {

        private val behandling = nyBehandling()

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
                    "forventetInntekt": 10,
                    "begrunnelse": null
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
                "institusjonsopphold": {
                    "status": "VilkårOppfylt",
                    "begrunnelse": null
                },
                "oppholdIUtlandet": {
                    "status": "SkalHoldeSegINorge",
                    "begrunnelse": null
                },
                "formue": {
                    "status": "VilkårOppfylt",
                    "borSøkerMedEPS": true,
                    "verdier": {
                        "verdiIkkePrimærbolig": 0,
                        "verdiKjøretøy": 0,
                        "innskudd": 0,
                        "verdipapir": 0,
                        "pengerSkyldt": 0,
                        "kontanter": 0,
                        "depositumskonto": 0
                    },
                    "epsVerdier": {
                        "verdiIkkePrimærbolig": 0,
                        "verdiKjøretøy": 0,
                        "innskudd": 0,
                        "verdipapir": 0,
                        "pengerSkyldt": 0,
                        "kontanter": 0,
                        "depositumskonto": 0
                    },
                    "begrunnelse": null
                },
                "personligOppmøte": {
                    "status": "MøttPersonlig",
                    "begrunnelse": null
                },
                "bosituasjon": {
                    "epsFnr": null,
                    "delerBolig": false,
                    "ektemakeEllerSamboerUførFlyktning": false,
                    "begrunnelse": null
                },
                "ektefelle": {
                    "fnr": "17087524256",
                    "navn": {
                      "fornavn": "fornavn",
                      "mellomnavn": null,
                      "etternavn": "etternavn"
                    },
                    "kjønn": null,
                    "adressebeskyttelse": null,
                    "skjermet": null,
                    "alder": 45
                },
                "utledetSats": "HØY"
          },
          "søknad": $søknadJsonString,
          "beregning": $expectedBeregningJson,
          "status": "OPPRETTET",
          "simulering": null,
          "attestering" : { "attestant" : "kjella", "underkjennelse":  null},
          "saksbehandler" : "pro-saksbehandler",
          "sakId": "$sakId",
          "hendelser": []
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
        val behandlingWithNulls = behandlingFactory.createBehandling(
            id = behandlingId,
            behandlingsinformasjon = Behandlingsinformasjon(),
            søknad = journalførtSøknadMedOppgave,
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = FnrGenerator.random(),
            oppgaveId = oppgaveId
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
            "institusjonsopphold": null,
            "oppholdIUtlandet": null,
            "formue": null,
            "personligOppmøte": null,
            "bosituasjon": null,
            "utledetSats": null,
            "ektefelle": null
          },
          "søknad": $søknadJsonString,
          "beregning": null,
          "status": "OPPRETTET",
          "simulering": null,
          "opprettet": "$opprettetTidspunkt",
          "attestering": null,
          "saksbehandler": null,
          "sakId": "$sakId",
          "hendelser": []
        }
        """

        JSONAssert.assertEquals(expectedNullsJson, serialize(behandlingWithNulls.toJson()), true)
        deserialize<BehandlingJson>(expectedNullsJson) shouldBe behandlingWithNulls.toJson()
    }
}
