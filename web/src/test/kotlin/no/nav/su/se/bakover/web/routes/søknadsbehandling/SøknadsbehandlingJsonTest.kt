package no.nav.su.se.bakover.web.routes.søknadsbehandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.satsFactoryTest
import no.nav.su.se.bakover.test.vilkårsvurderingSøknadsbehandlingIkkeVurdert
import no.nav.su.se.bakover.web.routes.grunnlag.BosituasjonJsonTest.Companion.expectedBosituasjonJson
import no.nav.su.se.bakover.web.routes.grunnlag.UføreVilkårJsonTest.Companion.expectedVurderingUføreJson
import no.nav.su.se.bakover.web.routes.grunnlag.UtenlandsoppholdVilkårJsonTest.Companion.expectedUtenlandsoppholdVurdert
import no.nav.su.se.bakover.web.routes.søknad.SøknadJsonTest.Companion.søknadJsonString
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
                "formue": {
                    "status": "VilkårOppfylt",
                    "verdier": {
                        "verdiIkkePrimærbolig": 0,
                        "verdiEiendommer": 0,
                        "verdiKjøretøy": 0,
                        "innskudd": 0,
                        "verdipapir": 0,
                        "pengerSkyldt": 0,
                        "kontanter": 0,
                        "depositumskonto": 0
                    },
                    "epsVerdier": {
                        "verdiIkkePrimærbolig": 0,
                        "verdiEiendommer": 0,
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
            },
            "begrunnelse": "begrunnelsen"
          },
          "grunnlagsdataOgVilkårsvurderinger": {
            "uføre": $expectedVurderingUføreJson,
            "fradrag": [],
            "bosituasjon": $expectedBosituasjonJson,
            "formue": {
                "resultat": "MåInnhenteMerInformasjon",
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
              }
          },
          "fritekstTilBrev": "",
          "erLukket": false,
          "simuleringForAvkortingsvarsel": null
        }
            """.trimIndent()
    }

    @Test
    fun `should serialize to json string`() {
        JSONAssert.assertEquals(
            behandlingJsonString,
            serialize(søknadsbehandling.toJson(satsFactoryTest)), true
        )
    }

    @Test
    fun `should deserialize json string`() {
        deserialize<BehandlingJson>(behandlingJsonString) shouldBe søknadsbehandling.toJson(satsFactoryTest)
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
            "formue": null,
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
                "resultat": "MåInnhenteMerInformasjon",
                "formuegrenser": [
                  {
                      "gyldigFra": "2020-05-01",
                      "beløp": 50676
                  }
                ],
                "vurderinger": []
            },
            "utenlandsopphold": null,
            "opplysningsplikt":null
          },
          "fritekstTilBrev": "",
          "erLukket": false,
          "simuleringForAvkortingsvarsel": null
        }
            """

        val serialize = serialize(behandlingWithNulls.toJson(satsFactoryTest))
        JSONAssert.assertEquals(expectedNullsJson, serialize, true)
        deserialize<BehandlingJson>(expectedNullsJson) shouldBe behandlingWithNulls.toJson(satsFactoryTest)
    }
}
