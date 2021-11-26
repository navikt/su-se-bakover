package no.nav.su.se.bakover.database.vedtak.snapshot

import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.vedtak.snapshot.VedtakssnapshotJson.Companion.toJson
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.snapshot.Vedtakssnapshot
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.periodeJanuar2021
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagUtenBeregning
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class VedtakssnapshotJsonTest {

    @Test
    fun `kan serialisere avslag`() {
        val avslagUtenBeregning = søknadsbehandlingIverksattAvslagUtenBeregning().second
        val vedtakssnapshotId = "06015ac6-07ef-4017-bd04-1e7b87b160fa"
        val avslag = Vedtakssnapshot.Avslag(
            id = UUID.fromString(vedtakssnapshotId),
            opprettet = fixedTidspunkt,
            søknadsbehandling = avslagUtenBeregning,
            avslagsgrunner = listOf(Avslagsgrunn.PERSONLIG_OPPMØTE),
        )

        //language=JSON
        val expectedJson = """
            {
               "type":"avslag",
               "id":"$vedtakssnapshotId",
               "opprettet":"2021-01-01T01:02:03.456789Z",
               "avslagsgrunner":["PERSONLIG_OPPMØTE"],
               "behandling":{
                  "id":"${avslagUtenBeregning.id}",
                  "opprettet":"2021-01-01T01:02:03.456789Z",
                  "sakId":"${avslagUtenBeregning.sakId}",
                  "saksnummer":${avslagUtenBeregning.saksnummer},
                  "fnr":"${avslagUtenBeregning.fnr}",
                  "status":"IVERKSATT_AVSLAG",
                  "saksbehandler":"saksbehandler",
                  "attestering":{
                     "type": "Iverksatt",
                     "attestant": "attestant",
                     "opprettet": "2021-01-01T01:02:04.456789Z"
                  },
                  "oppgaveId":"oppgaveIdSøknad",
                  "beregning": null,
                  "behandlingsinformasjon":{
                     "uførhet":{
                        "status":"VilkårIkkeOppfylt",
                        "uføregrad":20,
                        "forventetInntekt":10,
                        "begrunnelse":null
                     },
                     "flyktning":{
                        "status":"VilkårIkkeOppfylt",
                        "begrunnelse":null
                     },
                     "lovligOpphold":{
                        "status":"VilkårIkkeOppfylt",
                        "begrunnelse":null
                     },
                     "fastOppholdINorge":{
                        "status":"VilkårIkkeOppfylt",
                        "begrunnelse":null
                     },
                     "institusjonsopphold":{
                        "status":"VilkårIkkeOppfylt",
                        "begrunnelse":null
                     },
                     "formue":{
                        "status":"VilkårIkkeOppfylt",
                        "verdier":{
                           "verdiIkkePrimærbolig":90000000,
                           "verdiEiendommer":0,
                           "verdiKjøretøy":0,
                           "innskudd":0,
                           "verdipapir":0,
                           "pengerSkyldt":0,
                           "kontanter":0,
                           "depositumskonto":0
                        },
                        "epsVerdier": null,
                        "begrunnelse":null
                     },
                     "personligOppmøte":{
                        "status":"IkkeMøttPersonlig",
                        "begrunnelse":null
                     }
                  },
                  "behandlingsresultat": {
                      "sats": "HØY",
                      "satsgrunn":"ENSLIG"
                  },
                  "søknad":{
                     "id":"${avslagUtenBeregning.søknad.id}",
                     "opprettet":"2021-01-01T01:02:03.456789Z",
                     "sakId":"${avslagUtenBeregning.sakId}",
                     "søknadInnhold":{
                        "uførevedtak":{
                           "harUførevedtak":true
                        },
                        "personopplysninger":{
                           "fnr":"${avslagUtenBeregning.fnr}"
                        },
                        "flyktningsstatus":{
                           "registrertFlyktning":false
                        },
                        "boforhold":{
                           "borOgOppholderSegINorge":true,
                           "delerBolig":true,
                           "delerBoligMed":"EKTEMAKE_SAMBOER",
                           "ektefellePartnerSamboer":{
                              "erUførFlyktning":false,
                              "fnr":"01017001337"
                           },
                           "innlagtPåInstitusjon":{
                              "datoForInnleggelse":"2020-01-01",
                              "datoForUtskrivelse":"2020-01-31",
                              "fortsattInnlagt":false
                           },
                           "oppgittAdresse":{
                              "type":"IngenAdresse",
                              "grunn":"HAR_IKKE_FAST_BOSTED"
                           }
                        },
                        "utenlandsopphold":{
                           "registrertePerioder":[
                              {
                                 "utreisedato":"2020-01-01",
                                 "innreisedato":"2020-01-31"
                              },
                              {
                                 "utreisedato":"2020-02-01",
                                 "innreisedato":"2020-02-05"
                              }
                           ],
                           "planlagtePerioder":[
                              {
                                 "utreisedato":"2020-07-01",
                                 "innreisedato":"2020-07-31"
                              }
                           ]
                        },
                        "oppholdstillatelse":{
                           "erNorskStatsborger":false,
                           "harOppholdstillatelse":true,
                           "oppholdstillatelseType":"MIDLERTIDIG",
                           "statsborgerskapAndreLand":false,
                           "statsborgerskapAndreLandFritekst":null
                        },
                        "inntektOgPensjon":{
                           "forventetInntekt":2500,
                           "andreYtelserINav":"sosialstønad",
                           "andreYtelserINavBeløp":33,
                           "søktAndreYtelserIkkeBehandletBegrunnelse":"uføre",
                           "sosialstønadBeløp":7000.0,
                           "trygdeytelseIUtlandet":[
                              {
                                 "beløp":200,
                                 "type":"trygd",
                                 "valuta":"En valuta"
                              },
                              {
                                 "beløp":500,
                                 "type":"Annen trygd",
                                 "valuta":"En annen valuta"
                              }
                           ],
                           "pensjon":[
                              {
                                 "ordning":"KLP",
                                 "beløp":2000.0
                              },
                              {
                                 "ordning":"SPK",
                                 "beløp":5000.0
                              }
                           ]
                        },
                        "formue":{
                           "eierBolig":true,
                           "borIBolig":false,
                           "verdiPåBolig":600000,
                           "boligBrukesTil":"Mine barn bor der",
                           "depositumsBeløp":1000.0,
                           "kontonummer":"12345678912",
                           "verdiPåEiendom":3,
                           "eiendomBrukesTil":"",
                           "kjøretøy":[
                              {
                                 "verdiPåKjøretøy":25000,
                                 "kjøretøyDeEier":"bil"
                              }
                           ],
                           "innskuddsBeløp":25000,
                           "verdipapirBeløp":25000,
                           "skylderNoenMegPengerBeløp":25000,
                           "kontanterBeløp":25000
                        },
                        "forNav":{
                           "type":"DigitalSøknad",
                           "harFullmektigEllerVerge":"VERGE"
                        },
                        "ektefelle":{
                           "formue":{
                              "eierBolig":true,
                              "borIBolig":false,
                              "verdiPåBolig":0,
                              "boligBrukesTil":"",
                              "depositumsBeløp":0,
                              "kontonummer":"11111111111",
                              "verdiPåEiendom":0,
                              "eiendomBrukesTil":"",
                              "kjøretøy":[

                              ],
                              "innskuddsBeløp":0,
                              "verdipapirBeløp":0,
                              "skylderNoenMegPengerBeløp":0,
                              "kontanterBeløp":0
                           },
                           "inntektOgPensjon":{
                              "forventetInntekt":null,
                              "andreYtelserINav":null,
                              "andreYtelserINavBeløp":null,
                              "søktAndreYtelserIkkeBehandletBegrunnelse":null,
                              "sosialstønadBeløp":null,
                              "trygdeytelseIUtlandet":null,
                              "pensjon":null
                           }
                        }
                     },
                     "journalpostId":"journalpostIdSøknad",
                     "oppgaveId":"oppgaveIdSøknad"
                  },
                  "simulering": null
               }
            }
        """.trimIndent()

        val actualJson = objectMapper.writeValueAsString(avslag.toJson())
        JSONAssert.assertEquals(expectedJson, actualJson, true)
    }

    @Test
    fun `kan serialisere innvilgelse`() {
        val vedtakssnapshotId = "06015ac6-07ef-4017-bd04-1e7b87b160fa"
        val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(periodeJanuar2021)
        )
        val behandling = sak.søknadsbehandlinger.first() as Søknadsbehandling.Iverksatt.Innvilget
        val utbetaling = sak.utbetalinger.first() as Utbetaling.OversendtUtbetaling.MedKvittering
        val innvilgelse = Vedtakssnapshot.Innvilgelse(
            id = UUID.fromString(vedtakssnapshotId),
            opprettet = fixedTidspunkt,
            søknadsbehandling = behandling,
            utbetaling = utbetaling,
        )
        //language=JSON
        val expectedJson = """
            {
               "type":"innvilgelse",
               "id":"$vedtakssnapshotId",
               "opprettet":"2021-01-01T01:02:03.456789Z",
               "behandling":{
                  "id":"${behandling.id}",
                  "opprettet":"2021-01-01T01:02:03.456789Z",
                  "sakId":"${sak.id}",
                  "saksnummer":${sak.saksnummer},
                  "fnr":"${sak.fnr}",
                  "status":"IVERKSATT_INNVILGET",
                  "saksbehandler":"saksbehandler",
                  "attestering":{
                     "type": "Iverksatt",
                     "attestant": "attestant",
                     "opprettet": "2021-01-01T01:02:03.456789Z"
                  },
                  "oppgaveId":"oppgaveIdSøknad",
                  "beregning":{
                    "id":"${behandling.beregning.getId()}",
                    "opprettet":"2021-01-01T01:02:03.456789Z",
                    "sats":"HØY",
                     "månedsberegninger":[
                        {
                          "sumYtelse":20946,
                          "sumFradrag":0.0,
                          "benyttetGrunnbeløp":101351,
                          "sats":"HØY",
                          "satsbeløp":20945.873333333333,
                          "fradrag":[
                            {
                              "fradragstype":"ForventetInntekt",
                              "månedsbeløp":0.0,
                              "utenlandskInntekt":null,
                              "periode":{
                                "fraOgMed":"2021-01-01",
                                "tilOgMed":"2021-01-31"
                              },
                              "tilhører":"BRUKER"
                            }
                          ],
                          "periode":{
                            "fraOgMed":"2021-01-01",
                            "tilOgMed":"2021-01-31"
                          },
                          "fribeløpForEps":0.0,
                          "merknader":[
                            
                          ]
                        }
                      ],
                    "fradrag":[
                      {
                        "fradragstype":"ForventetInntekt",
                        "månedsbeløp":0.0,
                        "utenlandskInntekt":null,
                        "periode":{
                            "fraOgMed":"2021-01-01",
                            "tilOgMed":"2021-01-31"
                        },
                        "tilhører":"BRUKER"
                      }
                    ],
                    "sumYtelse":20946,
                    "sumFradrag":0.0,
                    "periode":{
                        "fraOgMed":"2021-01-01",
                        "tilOgMed":"2021-01-31"
                    },
                    "fradragStrategyName":"Enslig",
                    "begrunnelse": null
                },
                  "behandlingsinformasjon":{
                     "uførhet":{
                        "status":"VilkårOppfylt",
                        "uføregrad":20,
                        "forventetInntekt":10,
                        "begrunnelse":null
                     },
                     "flyktning":{
                        "status":"VilkårOppfylt",
                        "begrunnelse":null
                     },
                     "lovligOpphold":{
                        "status":"VilkårOppfylt",
                        "begrunnelse":null
                     },
                     "fastOppholdINorge":{
                        "status":"VilkårOppfylt",
                        "begrunnelse":null
                     },
                     "institusjonsopphold":{
                        "status":"VilkårOppfylt",
                        "begrunnelse":null
                     },
                     "formue":{
                        "status":"VilkårOppfylt",
                        "verdier":{
                           "verdiIkkePrimærbolig":0,
                           "verdiEiendommer":0,
                           "verdiKjøretøy":0,
                           "innskudd":0,
                           "verdipapir":0,
                           "pengerSkyldt":0,
                           "kontanter":0,
                           "depositumskonto":0
                        },
                        "epsVerdier":null,
                        "begrunnelse":null
                     },
                     "personligOppmøte":{
                        "status":"MøttPersonlig",
                        "begrunnelse":null
                     }
                  },
                  "behandlingsresultat": {
                    "sats": "HØY",
                    "satsgrunn":"ENSLIG"
                  },
                  "søknad":{
                     "id":"${behandling.søknad.id}",
                     "opprettet":"2021-01-01T01:02:03.456789Z",
                     "sakId":"${sak.id}",
                     "søknadInnhold":{
                        "uførevedtak":{
                           "harUførevedtak":true
                        },
                        "personopplysninger":{
                           "fnr":"${sak.fnr}"
                        },
                        "flyktningsstatus":{
                           "registrertFlyktning":false
                        },
                        "boforhold":{
                           "borOgOppholderSegINorge":true,
                           "delerBolig":true,
                           "delerBoligMed":"EKTEMAKE_SAMBOER",
                           "ektefellePartnerSamboer":{
                              "erUførFlyktning":false,
                              "fnr":"01017001337"
                           },
                           "innlagtPåInstitusjon":{
                              "datoForInnleggelse":"2020-01-01",
                              "datoForUtskrivelse":"2020-01-31",
                              "fortsattInnlagt":false
                           },
                           "oppgittAdresse":{
                              "type":"IngenAdresse",
                              "grunn":"HAR_IKKE_FAST_BOSTED"
                           }
                        },
                        "utenlandsopphold":{
                           "registrertePerioder":[
                              {
                                 "utreisedato":"2020-01-01",
                                 "innreisedato":"2020-01-31"
                              },
                              {
                                 "utreisedato":"2020-02-01",
                                 "innreisedato":"2020-02-05"
                              }
                           ],
                           "planlagtePerioder":[
                              {
                                 "utreisedato":"2020-07-01",
                                 "innreisedato":"2020-07-31"
                              }
                           ]
                        },
                        "oppholdstillatelse":{
                           "erNorskStatsborger":false,
                           "harOppholdstillatelse":true,
                           "oppholdstillatelseType":"MIDLERTIDIG",
                           "statsborgerskapAndreLand":false,
                           "statsborgerskapAndreLandFritekst":null
                        },
                        "inntektOgPensjon":{
                           "forventetInntekt":2500,
                           "andreYtelserINav":"sosialstønad",
                           "andreYtelserINavBeløp":33,
                           "søktAndreYtelserIkkeBehandletBegrunnelse":"uføre",
                           "sosialstønadBeløp":7000.0,
                           "trygdeytelseIUtlandet":[
                              {
                                 "beløp":200,
                                 "type":"trygd",
                                 "valuta":"En valuta"
                              },
                              {
                                 "beløp":500,
                                 "type":"Annen trygd",
                                 "valuta":"En annen valuta"
                              }
                           ],
                           "pensjon":[
                              {
                                 "ordning":"KLP",
                                 "beløp":2000.0
                              },
                              {
                                 "ordning":"SPK",
                                 "beløp":5000.0
                              }
                           ]
                        },
                        "formue":{
                           "eierBolig":true,
                           "borIBolig":false,
                           "verdiPåBolig":600000,
                           "boligBrukesTil":"Mine barn bor der",
                           "depositumsBeløp":1000.0,
                           "kontonummer":"12345678912",
                           "verdiPåEiendom":3,
                           "eiendomBrukesTil":"",
                           "kjøretøy":[
                              {
                                 "verdiPåKjøretøy":25000,
                                 "kjøretøyDeEier":"bil"
                              }
                           ],
                           "innskuddsBeløp":25000,
                           "verdipapirBeløp":25000,
                           "skylderNoenMegPengerBeløp":25000,
                           "kontanterBeløp":25000
                        },
                        "forNav":{
                           "type":"DigitalSøknad",
                           "harFullmektigEllerVerge":"VERGE"
                        },
                        "ektefelle":{
                           "formue":{
                              "eierBolig":true,
                              "borIBolig":false,
                              "verdiPåBolig":0,
                              "boligBrukesTil":"",
                              "depositumsBeløp":0,
                              "kontonummer":"11111111111",
                              "verdiPåEiendom":0,
                              "eiendomBrukesTil":"",
                              "kjøretøy":[

                              ],
                              "innskuddsBeløp":0,
                              "verdipapirBeløp":0,
                              "skylderNoenMegPengerBeløp":0,
                              "kontanterBeløp":0
                           },
                           "inntektOgPensjon":{
                              "forventetInntekt":null,
                              "andreYtelserINav":null,
                              "andreYtelserINavBeløp":null,
                              "søktAndreYtelserIkkeBehandletBegrunnelse":null,
                              "sosialstønadBeløp":null,
                              "trygdeytelseIUtlandet":null,
                              "pensjon":null
                           }
                        }
                     },
                     "journalpostId":"journalpostIdSøknad",
                     "oppgaveId":"oppgaveIdSøknad"
                  },
                   "simulering":{
                      "gjelderId":"${sak.fnr}",
                      "gjelderNavn":"MYGG LUR",
                      "datoBeregnet":"2021-01-01",
                      "nettoBeløp":20946,
                      "periodeList":[
                        {
                          "fraOgMed":"2021-01-01",
                          "tilOgMed":"2021-01-31",
                          "utbetaling":[
                            {
                              "fagSystemId":"12345676",
                              "utbetalesTilId":"${sak.fnr}",
                              "utbetalesTilNavn":"MYGG LUR",
                              "forfall":"2021-01-31",
                              "feilkonto":false,
                              "detaljer":[
                                {
                                  "faktiskFraOgMed":"2021-01-01",
                                  "faktiskTilOgMed":"2021-01-31",
                                  "konto":"4952000",
                                  "belop":20946,
                                  "tilbakeforing":false,
                                  "sats":20946,
                                  "typeSats":"MND",
                                  "antallSats":1,
                                  "uforegrad":0,
                                  "klassekode":"SUUFORE",
                                  "klassekodeBeskrivelse":"Supplerende stønad Uføre",
                                  "klasseType":"YTEL"
                                }
                              ]
                            }
                          ]
                        }
                      ]
                    }
                  },
               "utbetaling":{
                   "id":"${utbetaling.id}",
                   "opprettet":"2021-01-01T01:02:03.456789Z",
                   "sakId":"${sak.id}",
                   "saksnummer":${sak.saksnummer},
                   "fnr":"${sak.fnr}",
                   "utbetalingslinjer":[
                     {
                       "id":"${utbetaling.utbetalingslinjer.first().id}",
                       "opprettet":"2021-01-01T01:02:03.456789Z",
                       "fraOgMed":"2021-01-01",
                       "tilOgMed":"2021-01-31",
                       "forrigeUtbetalingslinjeId":null,
                       "beløp":15000,
                       "uføregrad":{
                         "value":50
                       }
                     }
                   ],
                   "type":"NY",
                   "behandler":"attestant",
                   "avstemmingsnøkkel":{
                     "opprettet":"2021-01-01T01:02:03.456789Z",
                     "nøkkel":"${utbetaling.avstemmingsnøkkel}"
                   },
                   "simulering":{
                     "gjelderId":"${sak.fnr}",
                     "gjelderNavn":"MYGG LUR",
                     "datoBeregnet":"2021-01-01",
                     "nettoBeløp":20946,
                     "periodeList":[
                       {
                         "fraOgMed":"2021-01-01",
                         "tilOgMed":"2021-01-31",
                         "utbetaling":[
                           {
                             "fagSystemId":"12345676",
                             "utbetalesTilId":"${sak.fnr}",
                             "utbetalesTilNavn":"MYGG LUR",
                             "forfall":"2021-01-31",
                             "feilkonto":false,
                             "detaljer":[
                               {
                                 "faktiskFraOgMed":"2021-01-01",
                                 "faktiskTilOgMed":"2021-01-31",
                                 "konto":"4952000",
                                 "belop":20946,
                                 "tilbakeforing":false,
                                 "sats":20946,
                                 "typeSats":"MND",
                                 "antallSats":1,
                                 "uforegrad":0,
                                 "klassekode":"SUUFORE",
                                 "klassekodeBeskrivelse":"Supplerende stønad Uføre",
                                 "klasseType":"YTEL"
                               }
                             ]
                           }
                         ]
                       }
                     ]
                   },
                   "utbetalingsrequest":{
                     "value":"<xml></xml>"
                   },
                   "kvittering":{
                     "utbetalingsstatus":"OK",
                     "originalKvittering":"<xml></xml>",
                     "mottattTidspunkt":"2021-01-01T01:02:03.456789Z"
                   }
                 }
               }
        """.trimIndent()

        val actualJson = objectMapper.writeValueAsString(innvilgelse.toJson())
        JSONAssert.assertEquals(expectedJson, actualJson, true)
    }
}
