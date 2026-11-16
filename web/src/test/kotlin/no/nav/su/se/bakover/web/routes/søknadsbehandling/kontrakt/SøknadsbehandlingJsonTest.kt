package no.nav.su.se.bakover.web.routes.søknadsbehandling.kontrakt

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.tid.april
import no.nav.su.se.bakover.common.domain.tid.fixedClock
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.formuegrenserFactoryTestPåDato
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.nySøknadsbehandlingUføre
import no.nav.su.se.bakover.web.Frontendkontrakt
import no.nav.su.se.bakover.web.routes.grunnlag.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SøknadsbehandlingJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.toJson
import no.nav.su.se.bakover.web.routes.søknadsbehandling.toJson
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

@Frontendkontrakt
internal class SøknadsbehandlingJsonTest {

    @Test
    fun `should serialize to json string`() {
        val søknadsbehandling = iverksattSøknadsbehandlingUføre(
            clock = TikkendeKlokke(1.april(2021).fixedClock()),
            stønadsperiode = Stønadsperiode.create(januar(2021)),
        ).second as IverksattSøknadsbehandling.Innvilget

        //language=JSON
        val expected = """
            {
              "id": "${søknadsbehandling.id}",
              "søknad": {
                "innsendtAv": "${søknadsbehandling.søknad.innsendtAv}",
                "id": "${søknadsbehandling.søknad.id}",
                "sakId": "${søknadsbehandling.sakId}",
                "søknadInnhold": {
                  "type": "uføre",
                  "uførevedtak": {
                    "harUførevedtak": true
                  },
                  "flyktningsstatus": {
                    "registrertFlyktning": false
                  },
                  "personopplysninger": {
                    "fnr": "${søknadsbehandling.fnr}"
                  },
                  "boforhold": {
                    "borOgOppholderSegINorge": true,
                    "delerBoligMedVoksne": true,
                    "delerBoligMed": "EKTEMAKE_SAMBOER",
                    "ektefellePartnerSamboer": {
                      "erUførFlyktning": false,
                      "fnr": "${søknadsbehandling.søknad.søknadInnhold.boforhold.ektefellePartnerSamboer!!.fnr}"
                    },
                    "innlagtPåInstitusjon": {
                      "datoForInnleggelse": "2020-01-01",
                      "datoForUtskrivelse": "2020-01-31",
                      "fortsattInnlagt": false
                    },
                    "borPåAdresse": null,
                    "ingenAdresseGrunn": "HAR_IKKE_FAST_BOSTED"
                  },
                  "utenlandsopphold": {
                    "registrertePerioder": [
                      {
                        "utreisedato": "2020-01-01",
                        "innreisedato": "2020-01-31"
                      },
                      {
                        "utreisedato": "2020-02-01",
                        "innreisedato": "2020-02-05"
                      }
                    ],
                    "planlagtePerioder": [
                      {
                        "utreisedato": "2020-07-01",
                        "innreisedato": "2020-07-31"
                      }
                    ]
                  },
                  "oppholdstillatelse": {
                    "erNorskStatsborger": false,
                    "harOppholdstillatelse": true,
                    "typeOppholdstillatelse": "midlertidig",
                    "statsborgerskapAndreLand": false,
                    "statsborgerskapAndreLandFritekst": null
                  },
                  "inntektOgPensjon": {
                    "forventetInntekt": 2500,
                    "andreYtelserINav": "sosialstønad",
                    "andreYtelserINavBeløp": 33,
                    "søktAndreYtelserIkkeBehandletBegrunnelse": "uføre",
                    "trygdeytelserIUtlandet": [
                      {
                        "beløp": 200,
                        "type": "trygd",
                        "valuta": "En valuta"
                      },
                      {
                        "beløp": 500,
                        "type": "Annen trygd",
                        "valuta": "En annen valuta"
                      }
                    ],
                    "pensjon": [
                      {
                        "ordning": "KLP",
                        "beløp": 2000.0
                      },
                      {
                        "ordning": "SPK",
                        "beløp": 5000.0
                      }
                    ]
                  },
                  "formue": {
                    "eierBolig": true,
                    "borIBolig": false,
                    "verdiPåBolig": 600000,
                    "boligBrukesTil": "Mine barn bor der",
                    "depositumsBeløp": 1000.0,
                    "verdiPåEiendom": 3,
                    "eiendomBrukesTil": "",
                    "kjøretøy": [
                      {
                        "verdiPåKjøretøy": 25000,
                        "kjøretøyDeEier": "bil"
                      }
                    ],
                    "innskuddsBeløp": 25000,
                    "verdipapirBeløp": 25000,
                    "skylderNoenMegPengerBeløp": 25000,
                    "kontanterBeløp": 25000
                  },
                  "forNav": {
                    "type": "DigitalSøknad",
                    "harFullmektigEllerVerge": "verge"
                  },
                  "ektefelle": {
                    "formue": {
                      "eierBolig": true,
                      "borIBolig": false,
                      "verdiPåBolig": 0,
                      "boligBrukesTil": "",
                      "depositumsBeløp": 0,
                      "verdiPåEiendom": 0,
                      "eiendomBrukesTil": "",
                      "kjøretøy": [],
                      "innskuddsBeløp": 0,
                      "verdipapirBeløp": 0,
                      "skylderNoenMegPengerBeløp": 0,
                      "kontanterBeløp": 0
                    },
                    "inntektOgPensjon": {
                      "forventetInntekt": null,
                      "andreYtelserINav": null,
                      "andreYtelserINavBeløp": null,
                      "søktAndreYtelserIkkeBehandletBegrunnelse": null,
                      "trygdeytelserIUtlandet": null,
                      "pensjon": null
                    }
                  }
                },
                "opprettet": "${søknadsbehandling.søknad.opprettet}",
                "lukket": null
              },
              "beregning": null,
              "status": "IVERKSATT_INNVILGET",
              "simulering": null,
              "opprettet": "${søknadsbehandling.opprettet}",
              "attesteringer": [
                {
                  "attestant": "attestant",
                  "underkjennelse": null,
                  "opprettet": "${søknadsbehandling.attesteringer.hentSisteAttestering().opprettet}"
                }
              ],
              "saksbehandler": "saksbehandler",
              "sakId": "${søknadsbehandling.sakId}",
              "stønadsperiode": {
                "periode": {
                  "fraOgMed": "2021-01-01",
                  "tilOgMed": "2021-01-31"
                }
              },
              "grunnlagsdataOgVilkårsvurderinger": {
                "uføre": null,
                "lovligOpphold": {
                  "vurderinger": [
                    {
                      "periode": {
                        "fraOgMed": "2021-01-01",
                        "tilOgMed": "2021-01-31"
                      },
                      "resultat": "VilkårOppfylt"
                    }
                  ],
                  "resultat": "VilkårOppfylt"
                },
                "fradrag": [],
                "bosituasjon": [
                  {
                    "type": "ENSLIG",
                    "fnr": null,
                    "delerBolig": false,
                    "ektemakeEllerSamboerUførFlyktning": null,
                    "sats": "HØY",
                    "periode": {
                      "fraOgMed": "2021-01-01",
                      "tilOgMed": "2021-01-31"
                    }
                  }
                ],
                "formue": null,
                "utenlandsopphold": null,
                "opplysningsplikt": {
                  "resultat": "TilstrekkeligDokumentasjon",
                  "vurderinger": [
                    {
                      "periode": {
                        "fraOgMed": "2021-01-01",
                        "tilOgMed": "2021-01-31"
                      },
                      "beskrivelse": "TilstrekkeligDokumentasjon"
                    }
                  ]
                },
                "pensjon": null,
                "familiegjenforening": null,
                "flyktning": {
                  "vurderinger": [
                    {
                      "resultat": "VilkårOppfylt",
                      "periode": {
                        "fraOgMed": "2021-01-01",
                        "tilOgMed": "2021-01-31"
                      }
                    }
                  ],
                  "resultat": "VilkårOppfylt"
                },
                "personligOppmøte": {
                  "vurderinger": [
                    {
                      "resultat": "VilkårOppfylt",
                      "vurdering": "IkkeMøttMenMidlertidigUnntakFraOppmøteplikt",
                      "periode": {
                        "fraOgMed": "2021-01-01",
                        "tilOgMed": "2021-01-31"
                      }
                    }
                  ],
                  "resultat": "VilkårOppfylt"
                },
                  "fastOpphold": {
                  "vurderinger": [
                    {
                      "periode": {
                        "fraOgMed": "2021-01-01",
                        "tilOgMed": "2021-01-31"
                      },
                      "resultat": "VilkårOppfylt"
                    }
                  ],
                  "resultat": "VilkårOppfylt"
                },
                 "institusjonsopphold":{
                    "vurderingsperioder": [
                      {
                        "periode": {
                          "fraOgMed": "2021-01-01",
                          "tilOgMed": "2021-01-31"
                        },
                        "vurdering": "VilkårOppfylt"
                      }
                   ],
                   "resultat": "VilkårOppfylt"
                 }
              },
              "erLukket": false,
              "sakstype": "uføre",
              "aldersvurdering": {
                "harSaksbehandlerAvgjort": false,
                "maskinellVurderingsresultat": "RETT_PÅ_UFØRE"
              },
            "omgjøringsgrunn": null,
            "omgjøringsårsak": null,
            "brevvalg": {
                    "begrunnelse": "begrunnelse",
                    "valg": "SEND",
                    "bestemtAv": "srvsupstonad"
                    },
              "eksterneGrunnlag": {
                  "skatt":{
                     "søkers":{
                        "fnr": "${søknadsbehandling.fnr}",
                        "hentetTidspunkt":"2021-01-01T01:02:03.456789Z",
                        "årsgrunnlag":[
                           {
                              "type": "Grunnlag",
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
                                 "verdsettingsrabattSomGirGjeldsreduksjon":[
                                    {
                                       "navn":"fradragForFagforeningskontingent",
                                       "beløp":"4000",
                                       "spesifisering":[]
                                    }
                                 ],
                                 "oppjusteringAvEierinntekter":[
                                    {
                                       "navn":"fradragForFagforeningskontingent",
                                       "beløp":"4000",
                                       "spesifisering":[]
                                    }
                                 ],
                                 "annet":[
                                    {
                                       "navn":"fradragForFagforeningskontingent",
                                       "beløp":"4000",
                                       "spesifisering":[]
                                    }
                                 ]
                              },
                              "stadie":"OPPGJØR",
                              "inntektsår":2021
                           }
                        ],
                        "saksbehandler":"saksbehandler",
                        "årSpurtFor":{
                           "fra":2021,
                           "til":2021
                        }
                     },
                     "eps": null
                  }
               }
            }
        """.trimIndent()
        val json = søknadsbehandling.toJson(formuegrenserFactoryTestPåDato())
        val grunnlagOgVilkår = json.grunnlagsdataOgVilkårsvurderinger
        val vilkårsvurderinger = søknadsbehandling.vilkårsvurderinger

        // Innholdet i disse feltene dekkes av egne kontraktstester. Her sjekkes bare at riktig objekt er koblet inn,
        // og feltene settes til null før resten sammenlignes.
        json.beregning shouldBe søknadsbehandling.beregning.toJson()
        json.simulering shouldBe søknadsbehandling.simulering.toJson()
        grunnlagOgVilkår.uføre.shouldNotBeNull() shouldBe vilkårsvurderinger.uføreVilkår().getOrFail().toJson()
        grunnlagOgVilkår.formue shouldBe vilkårsvurderinger.formueVilkår().toJson(formuegrenserFactoryTestPåDato())
        grunnlagOgVilkår.utenlandsopphold.shouldNotBeNull() shouldBe vilkårsvurderinger.utenlandsoppholdVilkår().toJson()

        val utenDekkedeFelt = json.copy(
            beregning = null,
            simulering = null,
            grunnlagsdataOgVilkårsvurderinger = grunnlagOgVilkår.copy(
                uføre = null,
                formue = null,
                utenlandsopphold = null,
            ),
        )
        JSONAssert.assertEquals(expected, serialize(utenDekkedeFelt), true)
        deserialize<SøknadsbehandlingJson>(expected) shouldBe utenDekkedeFelt
    }

    @Test
    fun nullables() {
        val søknadsbehandling = nySøknadsbehandlingUføre().second as VilkårsvurdertSøknadsbehandling.Uavklart

        //language=JSON
        val expected = """
            {
              "id": "${søknadsbehandling.id}",
              "søknad": {
                "innsendtAv": "${søknadsbehandling.søknad.innsendtAv}",
                "id": "${søknadsbehandling.søknad.id}",
                "sakId": "${søknadsbehandling.sakId}",
                "søknadInnhold": {
                  "type": "uføre",
                  "uførevedtak": {
                    "harUførevedtak": true
                  },
                  "flyktningsstatus": {
                    "registrertFlyktning": false
                  },
                  "personopplysninger": {
                    "fnr": "${søknadsbehandling.fnr}"
                  },
                  "boforhold": {
                    "borOgOppholderSegINorge": true,
                    "delerBoligMedVoksne": true,
                    "delerBoligMed": "EKTEMAKE_SAMBOER",
                    "ektefellePartnerSamboer": {
                      "erUførFlyktning": false,
                      "fnr": "${søknadsbehandling.søknad.søknadInnhold.boforhold.ektefellePartnerSamboer!!.fnr}"
                    },
                    "innlagtPåInstitusjon": {
                      "datoForInnleggelse": "2020-01-01",
                      "datoForUtskrivelse": "2020-01-31",
                      "fortsattInnlagt": false
                    },
                    "borPåAdresse": null,
                    "ingenAdresseGrunn": "HAR_IKKE_FAST_BOSTED"
                  },
                  "utenlandsopphold": {
                    "registrertePerioder": [
                      {
                        "utreisedato": "2020-01-01",
                        "innreisedato": "2020-01-31"
                      },
                      {
                        "utreisedato": "2020-02-01",
                        "innreisedato": "2020-02-05"
                      }
                    ],
                    "planlagtePerioder": [
                      {
                        "utreisedato": "2020-07-01",
                        "innreisedato": "2020-07-31"
                      }
                    ]
                  },
                  "oppholdstillatelse": {
                    "erNorskStatsborger": false,
                    "harOppholdstillatelse": true,
                    "typeOppholdstillatelse": "midlertidig",
                    "statsborgerskapAndreLand": false,
                    "statsborgerskapAndreLandFritekst": null
                  },
                  "inntektOgPensjon": {
                    "forventetInntekt": 2500,
                    "andreYtelserINav": "sosialstønad",
                    "andreYtelserINavBeløp": 33,
                    "søktAndreYtelserIkkeBehandletBegrunnelse": "uføre",
                    "trygdeytelserIUtlandet": [
                      {
                        "beløp": 200,
                        "type": "trygd",
                        "valuta": "En valuta"
                      },
                      {
                        "beløp": 500,
                        "type": "Annen trygd",
                        "valuta": "En annen valuta"
                      }
                    ],
                    "pensjon": [
                      {
                        "ordning": "KLP",
                        "beløp": 2000.0
                      },
                      {
                        "ordning": "SPK",
                        "beløp": 5000.0
                      }
                    ]
                  },
                  "formue": {
                    "eierBolig": true,
                    "borIBolig": false,
                    "verdiPåBolig": 600000,
                    "boligBrukesTil": "Mine barn bor der",
                    "depositumsBeløp": 1000.0,
                    "verdiPåEiendom": 3,
                    "eiendomBrukesTil": "",
                    "kjøretøy": [
                      {
                        "verdiPåKjøretøy": 25000,
                        "kjøretøyDeEier": "bil"
                      }
                    ],
                    "innskuddsBeløp": 25000,
                    "verdipapirBeløp": 25000,
                    "skylderNoenMegPengerBeløp": 25000,
                    "kontanterBeløp": 25000
                  },
                  "forNav": {
                    "type": "DigitalSøknad",
                    "harFullmektigEllerVerge": "verge"
                  },
                  "ektefelle": {
                    "formue": {
                      "eierBolig": true,
                      "borIBolig": false,
                      "verdiPåBolig": 0,
                      "boligBrukesTil": "",
                      "depositumsBeløp": 0,
                      "verdiPåEiendom": 0,
                      "eiendomBrukesTil": "",
                      "kjøretøy": [],
                      "innskuddsBeløp": 0,
                      "verdipapirBeløp": 0,
                      "skylderNoenMegPengerBeløp": 0,
                      "kontanterBeløp": 0
                    },
                    "inntektOgPensjon": {
                      "forventetInntekt": null,
                      "andreYtelserINav": null,
                      "andreYtelserINavBeløp": null,
                      "søktAndreYtelserIkkeBehandletBegrunnelse": null,
                      "trygdeytelserIUtlandet": null,
                      "pensjon": null
                    }
                  }
                },
                "opprettet": "${søknadsbehandling.søknad.opprettet}",
                "lukket": null
              },
              "beregning": null,
              "status": "OPPRETTET",
              "simulering": null,
              "opprettet": "${søknadsbehandling.opprettet}",
              "attesteringer": [],
              "saksbehandler": "saksbehandler",
              "sakId": "${søknadsbehandling.sakId}",
              "stønadsperiode": {
                "periode": {
                  "fraOgMed": "2021-01-01",
                  "tilOgMed": "2021-12-31"
                }
              },
              "grunnlagsdataOgVilkårsvurderinger": {
                "uføre": null,
                "lovligOpphold": null,
                "fradrag": [],
                "bosituasjon": [],
                "formue": {
                  "vurderinger": [],
                  "resultat": null,
                  "formuegrenser": [
                    {
                      "gyldigFra": "2020-05-01",
                      "beløp": 50676
                    }
                  ]
                },
                "utenlandsopphold": null,
                "opplysningsplikt": {
                  "resultat": "TilstrekkeligDokumentasjon",
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
                "familiegjenforening": null,
                "flyktning": null,
                "fastOpphold": null,
                "personligOppmøte": null,
                "institusjonsopphold": null
              },
              "erLukket": false,
              "sakstype": "uføre",
              "aldersvurdering": {
                "harSaksbehandlerAvgjort": false,
                "maskinellVurderingsresultat": "RETT_PÅ_UFØRE"
              },
              "eksterneGrunnlag": {
                  "skatt": null
                },
            "omgjøringsgrunn": null,
            "omgjøringsårsak": null,
            "brevvalg": {
                    "begrunnelse": null,
                    "valg": "IKKE_VALGT",
                    "bestemtAv": ""
                    }
            }
        """.trimIndent()

        JSONAssert.assertEquals(expected, serialize(søknadsbehandling.toJson(formuegrenserFactoryTestPåDato())), true)
        deserialize<SøknadsbehandlingJson>(expected) shouldBe søknadsbehandling.toJson(formuegrenserFactoryTestPåDato())
    }
}
