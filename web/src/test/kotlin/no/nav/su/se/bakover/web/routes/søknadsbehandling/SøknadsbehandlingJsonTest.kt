package no.nav.su.se.bakover.web.routes.søknadsbehandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.vilkårsvurderingSøknadsbehandlingIkkeVurdert
import no.nav.su.se.bakover.web.routes.grunnlag.BosituasjonJsonTest.Companion.expectedBosituasjonJson
import no.nav.su.se.bakover.web.routes.grunnlag.UføreVilkårJsonTest.Companion.expectedVurderingUføreJson
import no.nav.su.se.bakover.web.routes.grunnlag.UtenlandsoppholdVilkårJsonTest.Companion.expectedUtenlandsoppholdVurdert
import no.nav.su.se.bakover.web.routes.søknad.UføresøknadJsonTest.Companion.søknadJsonString
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingTestUtils.behandlingId
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingTestUtils.innvilgetSøknadsbehandling
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingTestUtils.journalførtSøknadMedOppgave
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingTestUtils.oppgaveId
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingTestUtils.sakId
import no.nav.su.se.bakover.web.routes.søknadsbehandling.BehandlingTestUtils.saksnummer
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.BeregningJsonTest.Companion.expectedBeregningJson
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.format.DateTimeFormatter

internal class SøknadsbehandlingJsonTest {

    companion object {

        private val søknadsbehandling = innvilgetSøknadsbehandling()

        //language=JSON
        internal val behandlingJsonString =
            """
        {
          "id": "$behandlingId",
          "opprettet": "${DateTimeFormatter.ISO_INSTANT.format(søknadsbehandling.opprettet)}",
          "behandlingsinformasjon": {
                "flyktning": {
                    "status" : "VilkårOppfylt"
                },
                "lovligOpphold": {
                    "status" : "VilkårOppfylt"
                },
                "fastOppholdINorge": {
                    "status": "VilkårOppfylt"
                },
                "institusjonsopphold": {
                    "status": "VilkårOppfylt"
                },
                "personligOppmøte": {
                    "status": "MøttPersonlig"
                }
          },
          "søknad": $søknadJsonString,
          "beregning": $expectedBeregningJson,
          "status": "IVERKSATT_INNVILGET",
          "simulering": {
            "totalBruttoYtelse": 0,
            "perioder": []
          },
          "attesteringer" : [{ "attestant" : "kjella", "underkjennelse":  null, "opprettet": "${Tidspunkt.EPOCH}"}],
          "saksbehandler" : "pro-saksbehandler",
          "sakId": "$sakId",
          "stønadsperiode": {
            "periode": {
              "fraOgMed": "2021-01-01",
              "tilOgMed": "2021-12-31"
            }
          },
          "grunnlagsdataOgVilkårsvurderinger": {
            "uføre": $expectedVurderingUføreJson,
            "fradrag": [],
            "bosituasjon": $expectedBosituasjonJson,
            "formue": {
                "resultat": null,
                "formuegrenser": [
                  {
                      "gyldigFra": "2020-05-01",
                      "beløp": 50676
                  }
                ],
                "vurderinger": []
              },
              "utenlandsopphold": $expectedUtenlandsoppholdVurdert,
              "opplysningsplikt": {
                 "vurderinger": [
                  {
                    "periode": {
                      "fraOgMed": "2021-01-01",
                      "tilOgMed": "2021-12-31"
                    },
                    "beskrivelse": "TilstrekkeligDokumentasjon"
                  }
                 ]     
              },
              "pensjon": null,
               "familiegjenforening": null
          },
          "fritekstTilBrev": "",
          "erLukket": false,
          "simuleringForAvkortingsvarsel": null,
          "sakstype": "UFØRE"
        }
            """.trimIndent()
    }

    @Test
    fun `should serialize to json string`() {
        JSONAssert.assertEquals(
            behandlingJsonString,
            serialize(søknadsbehandling.toJson(satsFactoryTestPåDato())), true,
        )
    }

    @Test
    fun `should deserialize json string`() {
        deserialize<BehandlingJson>(behandlingJsonString) shouldBe søknadsbehandling.toJson(satsFactoryTestPåDato())
    }

    @Test
    fun nullables() {
        val behandlingWithNulls = Søknadsbehandling.Vilkårsvurdert.Uavklart(
            id = behandlingId,
            opprettet = Tidspunkt.EPOCH,
            sakId = sakId,
            saksnummer = saksnummer,
            søknad = journalførtSøknadMedOppgave,
            oppgaveId = oppgaveId,
            behandlingsinformasjon = Behandlingsinformasjon(),
            fnr = Fnr.generer(),
            fritekstTilBrev = "",
            stønadsperiode = null,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingIkkeVurdert(),
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående.kanIkke(),
            sakstype = Sakstype.UFØRE,
        )
        val opprettetTidspunkt = DateTimeFormatter.ISO_INSTANT.format(behandlingWithNulls.opprettet)

        //language=JSON
        val expectedNullsJson =
            """
        {
          "id": "$behandlingId",
          "behandlingsinformasjon": {
            "flyktning": null,
            "lovligOpphold": null,
            "fastOppholdINorge": null,
            "institusjonsopphold": null,
            "personligOppmøte": null
          },
          "søknad": $søknadJsonString,
          "beregning": null,
          "status": "OPPRETTET",
          "simulering": null,
          "opprettet": "$opprettetTidspunkt",
          "attesteringer": [],
          "saksbehandler": null,
          "sakId": "$sakId",
          "stønadsperiode": null,
          "grunnlagsdataOgVilkårsvurderinger": {
            "uføre": null,
            "fradrag": [],
            "bosituasjon": [],
            "formue": {
                "resultat": null,
                "formuegrenser": [
                  {
                      "gyldigFra": "2020-05-01",
                      "beløp": 50676
                  }
                ],
                "vurderinger": []
            },
            "utenlandsopphold": null,
            "opplysningsplikt":null,
            "pensjon": null,
            "familiegjenforening": null
          },
          "fritekstTilBrev": "",
          "erLukket": false,
          "simuleringForAvkortingsvarsel": null,
          "sakstype": "UFØRE"
        }
            """

        val serialize = serialize(behandlingWithNulls.toJson(satsFactoryTestPåDato()))
        JSONAssert.assertEquals(expectedNullsJson, serialize, true)
        deserialize<BehandlingJson>(expectedNullsJson) shouldBe behandlingWithNulls.toJson(satsFactoryTestPåDato())
    }
}
