package no.nav.su.se.bakover.web.routes.søknadsbehandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.routes.grunnlag.BosituasjonJsonTest.Companion.expectedBosituasjonJson
import no.nav.su.se.bakover.web.routes.grunnlag.UføreVilkårJsonTest.Companion.expectedVurderingUføreJson
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
                },
                "bosituasjon": {
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
                    "fødselsdato": "1975-08-17",
                    "adressebeskyttelse": null,
                    "skjermet": null,
                    "alder": 46
                },
                "utledetSats": "HØY"
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
          "hendelser": [],
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
                      "gyldigFra": "2021-05-01",
                      "beløp": 53200
                  },
                  {
                      "gyldigFra": "2020-05-01",
                      "beløp": 50676
                  }
                ],
                "vilkår": "Formue",
                "vurderinger": []
              },
              "oppholdIUtlandet": null
          },
          "fritekstTilBrev": "",
          "erLukket": false
        }
            """.trimIndent()
    }

    @Test
    fun `should serialize to json string`() {
        JSONAssert.assertEquals(behandlingJsonString, serialize(søknadsbehandling.toJson()), true)
    }

    @Test
    fun `should deserialize json string`() {
        deserialize<BehandlingJson>(behandlingJsonString) shouldBe søknadsbehandling.toJson()
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
            vilkårsvurderinger = Vilkårsvurderinger.Søknadsbehandling.IkkeVurdert,
            attesteringer = Attesteringshistorikk.empty(),
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
          "attesteringer": [],
          "saksbehandler": null,
          "sakId": "$sakId",
          "hendelser": [],
          "stønadsperiode": null,
          "grunnlagsdataOgVilkårsvurderinger": {
            "uføre": null,
            "fradrag": [],
            "bosituasjon": [],
            "formue": {
                "resultat": "MåInnhenteMerInformasjon",
                "formuegrenser": [
                  {
                      "gyldigFra": "2021-05-01",
                      "beløp": 53200
                  },
                  {
                      "gyldigFra": "2020-05-01",
                      "beløp": 50676
                  }
                ],
                "vilkår": "Formue",
                "vurderinger": []
            },
            "oppholdIUtlandet": null
          },
          "fritekstTilBrev": "",
          "erLukket": false
        }
        """

        val serialize = serialize(behandlingWithNulls.toJson())
        JSONAssert.assertEquals(expectedNullsJson, serialize, true)
        deserialize<BehandlingJson>(expectedNullsJson) shouldBe behandlingWithNulls.toJson()
    }
}
