package no.nav.su.se.bakover.web.søknadsbehandling.skatt

import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.test.fnrUnder67
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.jsonAssertEquals
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.søknad.ny.NySøknadJson
import no.nav.su.se.bakover.web.søknad.ny.nyDigitalSøknad
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.bosituasjon.leggTilBosituasjon
import no.nav.su.se.bakover.web.søknadsbehandling.ny.nySøknadsbehandling
import no.nav.su.se.bakover.web.søknadsbehandling.virkningstidspunkt.leggTilVirkningstidspunkt
import org.json.JSONObject
import org.junit.jupiter.api.Test

class SkattIT {

    @Test
    fun `hent skattegrunnlag for bruker og eps, deretter fjern eps`() {
        SharedRegressionTestData.withTestApplicationAndEmbeddedDb {
            val fnr = Fnr.generer().toString()
            val søknadResponseJson = nyDigitalSøknad(fnr = fnr, client = client)
            val sakId = NySøknadJson.Response.hentSakId(søknadResponseJson)
            val søknadId = NySøknadJson.Response.hentSøknadId(søknadResponseJson)
            val nySøknadsbehandlingResponseJson = nySøknadsbehandling(
                sakId = sakId,
                søknadId = søknadId,
                client = client,
            )
            val behandlingId = BehandlingJson.hentBehandlingId(nySøknadsbehandlingResponseJson)
            leggTilVirkningstidspunkt(
                sakId = sakId,
                behandlingId = behandlingId,
                client = client,
            )
            leggTilBosituasjon(
                sakId = sakId,
                behandlingId = behandlingId,
                client = client,
                body = {
                    //language=json
                    """
                  {
                      "bosituasjoner": [
                          {
                            "periode": {
                              "fraOgMed": "2021-01-01",
                              "tilOgMed": "2021-12-31"
                            },
                            "epsFnr": "$fnrUnder67",
                            "delerBolig": null,
                            "erEPSUførFlyktning": true,
                            "begrunnelse": "Lagt til automatisk av Bosituasjon.kt#leggTilBosituasjon"
                          }
                      ]
                  }
                """
                },
            )
            hentSkattegrunnlagForÅr(behandlingId = behandlingId, sakId = sakId, client = client).also {
                jsonAssertEquals(
                    //language=json
                    """{
                      "skatt": {
                        "søkers": ${generateExpectedSkattegrunnlag(fnr)},
                        "eps": ${generateExpectedSkattegrunnlag(fnrUnder67.toString())}
                      }
                    }
                    """.trimIndent(),
                    JSONObject(BehandlingJson.hentEksterneGrunnlag(it)).toString(),
                    "skatt.søkers.id",
                    "skatt.eps.id",
                )
            }

            leggTilBosituasjon(sakId = sakId, behandlingId = behandlingId, client = client)

            hentSkattegrunnlagForÅr(behandlingId = behandlingId, sakId = sakId, client = client).also {
                jsonAssertEquals(
                    //language=json
                    """{
                      "skatt": {
                       "søkers": ${generateExpectedSkattegrunnlag(fnr)},
                        "eps": null
                      }
                    }
                    """.trimIndent(),
                    JSONObject(BehandlingJson.hentEksterneGrunnlag(it)).toString(),
                    "skatt.søkers.id",
                )
            }
        }
    }
}

private fun generateExpectedSkattegrunnlag(expectedFnr: String): String {
    //language=json
    return """{
          "fnr": "$expectedFnr",
          "hentetTidspunkt":"2021-01-01T01:02:03.456789Z",
          "årsgrunnlag":[
             {
                "grunnlag":{
                   "oppgjørsdato":null,
                   "formue":[
                      {
                         "navn":"bruttoformue",
                         "beløp":"1238",
                         "spesifisering":[]
                      },
                      {
                         "navn":"formuesverdiForKjoeretoey",
                         "beløp":"20000",
                         "spesifisering":[
                            {
                               "beløp":"15000",
                               "registreringsnummer":"AB12345",
                               "fabrikatnavn":"Troll",
                               "årForFørstegangsregistrering":"1957",
                               "formuesverdi":"15000",
                               "antattVerdiSomNytt":null,
                               "antattMarkedsverdi":null
                            },
                            {
                               "beløp":"5000",
                               "registreringsnummer":"BC67890",
                               "fabrikatnavn":"Think",
                               "årForFørstegangsregistrering":"2003",
                               "formuesverdi":"5000",
                               "antattVerdiSomNytt":null,
                               "antattMarkedsverdi":null
                            }
                         ]
                      }
                   ],
                   "inntekt":[
                      {
                         "navn":"alminneligInntektFoerSaerfradrag",
                         "beløp":"1000",
                         "spesifisering":[]
                      }
                   ],
                   "inntektsfradrag":[
                      {
                         "navn":"fradragForFagforeningskontingent",
                         "beløp":"4000",
                         "spesifisering":[]
                      }
                   ],
                   "formuesfradrag":[
                      {
                         "navn":"samletAnnenGjeld",
                         "beløp":"6000",
                         "spesifisering":[]
                      }
                   ],
                   "verdsettingsrabattSomGirGjeldsreduksjon":[],
                   "oppjusteringAvEierinntekter":[],
                   "annet":[]
                },
                "stadie":"OPPGJØR",
                "inntektsår":2020
             }
          ],
          "saksbehandler":"Z990Lokal",
          "årSpurtFor":{"fra":2020, "til":2020}
       }
    """.trimIndent()
}
