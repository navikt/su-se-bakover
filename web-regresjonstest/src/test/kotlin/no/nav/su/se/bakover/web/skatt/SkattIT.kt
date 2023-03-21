package no.nav.su.se.bakover.web.skatt

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.jsonAssertEquals
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.søknad.ny.NySøknadJson
import no.nav.su.se.bakover.web.søknad.ny.nyDigitalSøknad
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.ny.nySøknadsbehandling
import no.nav.su.se.bakover.web.søknadsbehandling.virkningstidspunkt.leggTilVirkningstidspunkt
import org.junit.jupiter.api.Test

class SkattIT {

    @Test
    fun `kan hente skattegrunnlag for en søknadsbehandling`() {
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
            val actual = hentSkattegrunnlagForÅr(behandlingId = behandlingId, client = client)
            assertSkattegrunnlag(
                expectedFnr = fnr,
                actual = actual,
            )
        }
    }
}

fun assertSkattegrunnlag(
    expectedFnr: String,
    actual: String,
) {
    jsonAssertEquals(
        //language=json
        """{
           "fnr": "$expectedFnr",
           "hentetTidspunkt":"2021-01-01T01:02:03.456789Z",
           "årsgrunnlag":[
              {
                 "inntektsår":"2020",
                 "skatteoppgjørsdato":null,
                 "stadie": "fastsatt",
                 "grunnlag":{
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
                 }
              }
           ]
        }
        """.trimMargin(),
        actual,
    )
}
